"""Reconcile real main pushes once; never hold a runner waiting for another run."""

import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

from aeroscapes_pack.data import PackError, array, field, integer, json_value, table
from aeroscapes_pack.errors import EXPECTED_ERRORS, error_message
from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.models import Project
from aeroscapes_pack.release_cli import git, publish_checked_run

STATE_VARIABLE = "RELEASE_QUEUE_STATE"


def commit_sha(value: str) -> str:
    if not re.fullmatch(r"[0-9a-f]{40}", value) or value == "0" * 40:
        raise PackError("Expected a nonzero exact Git commit SHA")
    return value


@dataclass(frozen=True, slots=True)
class Push:
    id: int
    attempt: int
    before: str
    head: str
    status: str
    conclusion: str | None

    @classmethod
    def parse(cls, value: object, repository: str) -> Push:
        record = table(value, "CI push run")
        if (
            record.get("event") != "push"
            or record.get("head_branch") != "main"
            or record.get("path")
            not in {
                ".github/workflows/ci.yml",
                ".github/workflows/ci.yml@main",
                ".github/workflows/ci.yml@refs/heads/main",
            }
            or table(record.get("repository"), "run.repository").get("full_name") != repository
            or table(record.get("head_repository"), "run.head_repository").get("full_name")
            != repository
        ):
            raise PackError("Untrusted CI push provenance")
        match = re.fullmatch(
            r"push ([0-9a-f]{40})\.\.([0-9a-f]{40})", field(record, "display_title", "run")
        )
        if match is None or match[2] != record.get("head_sha"):
            raise PackError("Missing or inconsistent CI push receipt in run-name")
        conclusion = record.get("conclusion")
        if conclusion is not None and not isinstance(conclusion, str):
            raise PackError("Invalid CI run conclusion")
        return cls(
            id=integer(record.get("id"), "run.id"),
            attempt=integer(record.get("run_attempt"), "run.run_attempt"),
            before=commit_sha(match[1]),
            head=commit_sha(match[2]),
            status=field(record, "status", "run"),
            conclusion=conclusion,
        )


def pushes(github: GitHub) -> tuple[object, ...]:
    # Do not filter this endpoint: GitHub caps filtered searches at 1,000 results.
    # A push can be dispatched out of order, so do not stop at the checkpoint run.
    result: list[object] = []
    page = 1
    while True:
        record = table(
            github.json("GET", f"/actions/workflows/ci.yml/runs?per_page=100&page={page}"),
            "CI runs",
        )
        values = array(record.get("workflow_runs"), "CI runs")
        result.extend(values)
        if len(values) < 100:
            return tuple(result)
        page += 1


def main_head(github: GitHub) -> str:
    record = table(github.json("GET", "/git/ref/heads/main"), "main ref")
    return commit_sha(field(table(record.get("object"), "main ref object"), "sha", "main"))


def ensure_fast_forward(project: Project, before: str, current: str) -> None:
    commit_sha(before)
    commit_sha(current)
    if before == current:
        return
    try:
        git(project.root, "merge-base", "--is-ancestor", before, current)
    except EXPECTED_ERRORS as error:
        raise PackError("Non-fast-forward main history; inspect the release queue") from error


@dataclass(frozen=True, slots=True)
class Checkpoint:
    sha: str
    pending: Push | None = None


def load_checkpoint(github: GitHub, baseline: str) -> Checkpoint:
    commit_sha(baseline)
    value = github.json("GET", f"/actions/variables/{STATE_VARIABLE}", allow_missing=True)
    if value is None:
        return Checkpoint(sha=baseline)
    state = table(
        json_value(field(table(value, "variable"), "value", "variable"), "queue state"),
        "queue state",
    )
    if state.get("baseline") != baseline:
        raise PackError("RELEASE_BASELINE_SHA changed after queue initialization")
    pending = None
    if state.get("pending") is not None:
        intent = table(state["pending"], "pending publication")
        pending = Push(
            id=integer(intent.get("id"), "intent.id"),
            attempt=integer(intent.get("attempt"), "intent.attempt"),
            before=commit_sha(field(intent, "before", "intent")),
            head=commit_sha(field(intent, "head", "intent")),
            status="completed",
            conclusion="success",
        )
    return Checkpoint(sha=commit_sha(field(state, "sha", "queue state")), pending=pending)


def save_checkpoint(github: GitHub, baseline: str, head: str, pending: Push | None = None) -> None:
    state: dict[str, object] = {"baseline": baseline, "sha": head}
    if pending is not None:
        state["pending"] = {
            "id": pending.id,
            "attempt": pending.attempt,
            "before": pending.before,
            "head": pending.head,
        }
    payload: dict[str, object] = {
        "name": STATE_VARIABLE,
        "value": json.dumps(state, sort_keys=True),
    }
    current = github.json("GET", f"/actions/variables/{STATE_VARIABLE}", allow_missing=True)
    if current is None:
        github.json("POST", "/actions/variables", payload)
    else:
        github.json("PATCH", f"/actions/variables/{STATE_VARIABLE}", payload)
    if load_checkpoint(github, baseline) != Checkpoint(sha=head, pending=pending):
        raise PackError("GitHub did not confirm the release checkpoint; retry reconciliation")


def next_push(checkpoint: str, records: tuple[Push, ...]) -> Push | None:
    candidates = [record for record in records if record.before == checkpoint]
    if len(candidates) > 1:
        raise PackError("Multiple push events follow the checkpoint; inspect main history")
    return candidates[0] if candidates else None


def publish_push(project: Project, github: GitHub, candidate: Push) -> None:
    print(publish_checked_run(project, github, candidate.head, candidate.id, candidate.attempt))


def reconcile(project: Project, github: GitHub, baseline: str, *, publish: bool) -> str:
    state = load_checkpoint(github, baseline)
    checkpoint = state.sha
    head = main_head(github)
    ensure_fast_forward(project, baseline, checkpoint)
    ensure_fast_forward(project, checkpoint, head)
    if state.pending is not None:
        if not publish:
            raise PackError("An unfinished publication requires RELEASE_ENABLED to resume")
        pending = state.pending
        original = Push.parse(
            github.json("GET", f"/actions/runs/{pending.id}/attempts/{pending.attempt}"),
            github.repository,
        )
        if original != pending or pending.before != checkpoint:
            raise PackError("Pending publication does not match its successful CI attempt")
        ensure_fast_forward(project, checkpoint, pending.head)
        ensure_fast_forward(project, pending.head, head)
        publish_push(project, github, pending)
        save_checkpoint(github, baseline, pending.head)
        checkpoint = pending.head
    records: list[Push] = []
    history = set(git(project.root, "rev-list", f"{baseline}..{head}").splitlines())
    for value in pushes(github):
        raw = table(value, "CI run")
        if raw.get("event") != "push" or raw.get("head_branch") != "main":
            continue
        source_head = commit_sha(field(raw, "head_sha", "run"))
        # Old runs predate the receipt format and must not enter the queue.
        if source_head not in history:
            continue
        records.append(Push.parse(raw, github.repository))
    candidates = tuple(records)
    while checkpoint != head:
        candidate = next_push(checkpoint, candidates)
        if candidate is None:
            raise PackError(
                f"Missing push receipt after {checkpoint}; check skipped/deleted CI runs"
            )
        ensure_fast_forward(project, candidate.before, candidate.head)
        ensure_fast_forward(project, candidate.head, head)
        if candidate.status != "completed":
            print(f"CI {candidate.id} is {candidate.status}; next reconciliation will continue")
            return ""
        if candidate.conclusion == "success":
            if publish:
                # Save and read back the successful attempt BEFORE the first release write.
                # A later failed CI rerun must not strand a draft or skip this publication.
                save_checkpoint(github, baseline, checkpoint, pending=candidate)
                publish_push(project, github, candidate)
        else:
            print(
                f"CI {candidate.id} ended with {candidate.conclusion}; this push is not published"
            )
            if candidate.head == head:
                # A rerun can still repair a transient failure of the current push.
                return ""
        save_checkpoint(github, baseline, candidate.head)
        checkpoint = candidate.head
    checked_head = any(
        candidate.head == head
        and candidate.status == "completed"
        and candidate.conclusion == "success"
        for candidate in candidates
    )
    return head if checked_head else ""


def main() -> int:
    try:
        github = GitHub(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_TOKEN", ""))
        docs_commit = reconcile(
            Project.discover(Path.cwd()),
            github,
            os.environ.get("RELEASE_BASELINE_SHA", ""),
            publish=os.environ.get("RELEASE_ENABLED") == "true",
        )
        output = os.environ.get("GITHUB_OUTPUT")
        if output is not None and output != "":
            with Path(output).open("a", encoding="utf-8") as stream:
                stream.write(f"docs_commit={docs_commit}\n")
    except EXPECTED_ERRORS as error:
        print(error_message(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
