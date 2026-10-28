"""Publication follows actual main pushes without waiting inside a runner."""

import io
import zipfile
from pathlib import Path

import pytest

from aeroscapes_pack.data import PackError
from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.models import Project


def push(
    before: str, head: str, status: str = "completed", result: str = "success"
) -> dict[str, object]:
    return {
        "id": int(head[0], 16) + 1,
        "event": "push",
        "head_branch": "main",
        "head_sha": head,
        "display_title": f"push {before}..{head}",
        "status": status,
        "conclusion": result,
        "run_attempt": 1,
        "path": ".github/workflows/ci.yml",
        "repository": {"full_name": "owner/repo"},
        "head_repository": {"full_name": "owner/repo"},
    }


def test_push_receipt_rejects_prs_foreign_sources_and_mismatched_sha() -> None:
    from aeroscapes_pack.release_queue import Push

    record = push("a" * 40, "b" * 40)
    assert Push.parse(record, "owner/repo").before == "a" * 40
    assert isinstance(record, dict)
    for field, value in (
        ("event", "pull_request"),
        ("head_repository", {"full_name": "attacker/repo"}),
        ("head_sha", "c" * 40),
        ("path", ".github/workflows/other.yml"),
    ):
        changed = {**record, field: value}
        with pytest.raises(PackError):
            Push.parse(changed, "owner/repo")


@pytest.mark.parametrize("suffix", ["", "@main", "@refs/heads/main"])
def test_push_accepts_api_workflow_path_with_main_ref(suffix: str) -> None:
    from aeroscapes_pack.release_queue import Push

    record = push("a" * 40, "b" * 40)
    record["path"] = ".github/workflows/ci.yml" + suffix
    assert Push.parse(record, "owner/repo").head == "b" * 40


def test_queue_uses_push_edges_even_when_api_order_is_reversed() -> None:
    from aeroscapes_pack.release_queue import Push, next_push

    first = Push.parse(push("a" * 40, "c" * 40), "owner/repo")
    second = Push.parse(push("c" * 40, "d" * 40), "owner/repo")
    assert next_push("a" * 40, (second, first)) == first
    assert next_push("c" * 40, (first, second)) == second
    assert next_push("b" * 40, (first, second)) is None
    fork = Push.parse(push("a" * 40, "e" * 40), "owner/repo")
    with pytest.raises(PackError, match="Multiple"):
        next_push("a" * 40, (first, fork))


def test_reconciliation_exits_for_pending_ci_and_resumes_after_checkpoint_failure(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from aeroscapes_pack import release_queue

    github = GitHub("owner/repo", "token")
    records = [push("a" * 40, "b" * 40, "queued")]
    checkpoint = [release_queue.Checkpoint(sha="a" * 40)]
    publications: list[str] = []
    saved: list[str] = []
    monkeypatch.setattr(release_queue, "pushes", lambda _github: tuple(records))
    monkeypatch.setattr(release_queue, "load_checkpoint", lambda *_args: checkpoint[0])
    monkeypatch.setattr(release_queue, "ensure_fast_forward", lambda *_args: None)
    monkeypatch.setattr(release_queue, "git", lambda *_args: "b" * 40 + "\n" + "c" * 40)
    monkeypatch.setattr(release_queue, "main_head", lambda _github: "c" * 40)

    def publish(_project: Project, _github: GitHub, candidate: release_queue.Push) -> None:
        publications.append(candidate.head)

    def save(
        _github: GitHub,
        _baseline: str,
        head: str,
        pending: release_queue.Push | None = None,
    ) -> None:
        checkpoint[0] = release_queue.Checkpoint(sha=head, pending=pending)
        if pending is not None:
            return
        saved.append(head)
        if len(saved) == 1:
            checkpoint[0] = release_queue.Checkpoint(
                sha="a" * 40,
                pending=release_queue.Push.parse(push("a" * 40, "b" * 40), "owner/repo"),
            )
            raise PackError("checkpoint response lost")

    monkeypatch.setattr(release_queue, "publish_push", publish)
    monkeypatch.setattr(github, "json", lambda *_args, **_kwargs: push("a" * 40, "b" * 40))
    monkeypatch.setattr(release_queue, "save_checkpoint", save)
    project = Project(tmp_path)
    assert release_queue.reconcile(project, github, "a" * 40, publish=True) == ""
    assert not publications
    records[:] = [
        push("b" * 40, "c" * 40),
        push("a" * 40, "b" * 40),
    ]
    with pytest.raises(PackError, match="response lost"):
        release_queue.reconcile(project, github, "a" * 40, publish=True)
    assert checkpoint[0].sha == "a" * 40
    assert publications == ["b" * 40]
    # The last CI rerun can fail after a draft was created by the original attempt.
    # Recovery must use the saved successful attempt before processing the successor.
    records[1] = {**push("a" * 40, "b" * 40, result="cancelled"), "run_attempt": 2}
    assert release_queue.reconcile(project, github, "a" * 40, publish=True) == "c" * 40
    assert publications == ["b" * 40, "b" * 40, "c" * 40]


def test_failed_checks_are_consumed_without_publishing_and_gap_is_visible(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from aeroscapes_pack import release_queue

    github = GitHub("owner/repo", "token")
    monkeypatch.setattr(
        release_queue, "load_checkpoint", lambda *_args: release_queue.Checkpoint(sha="a" * 40)
    )
    monkeypatch.setattr(release_queue, "ensure_fast_forward", lambda *_args: None)
    monkeypatch.setattr(release_queue, "git", lambda *_args: "b" * 40 + "\n" + "c" * 40)
    monkeypatch.setattr(release_queue, "main_head", lambda _github: "c" * 40)
    records = (
        push("a" * 40, "b" * 40, result="failure"),
        push("b" * 40, "c" * 40, result="failure"),
    )
    monkeypatch.setattr(release_queue, "pushes", lambda _github: records)
    saved: list[str] = []
    monkeypatch.setattr(
        release_queue, "save_checkpoint", lambda _github, _base, head: saved.append(head)
    )
    assert release_queue.reconcile(Project(tmp_path), github, "a" * 40, publish=True) == ""
    assert saved == ["b" * 40]
    records = (push("b" * 40, "c" * 40),)
    with pytest.raises(PackError, match="Missing push"):
        release_queue.reconcile(Project(tmp_path), github, "a" * 40, publish=True)


def test_checkpoint_confirms_successful_attempt_before_publication(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from aeroscapes_pack.release_queue import Checkpoint, Push, load_checkpoint, save_checkpoint

    github = GitHub("owner/repo", "token")
    variable: dict[str, object] = {}

    def request(
        method: str, _path: str, data: dict[str, object] | None = None, **_kwargs: object
    ) -> object:
        if method == "GET":
            return variable or None
        assert data is not None
        variable.update(data)
        return None  # GitHub variable writes have an empty success response.

    monkeypatch.setattr(github, "json", request)
    candidate = Push.parse(push("a" * 40, "b" * 40), "owner/repo")
    save_checkpoint(github, "a" * 40, "a" * 40, pending=candidate)
    assert load_checkpoint(github, "a" * 40) == Checkpoint(sha="a" * 40, pending=candidate)
    save_checkpoint(github, "a" * 40, "b" * 40)
    assert load_checkpoint(github, "a" * 40) == Checkpoint(sha="b" * 40)
    with pytest.raises(PackError, match="changed"):
        load_checkpoint(github, "c" * 40)


def test_failed_title_check_can_be_corrected_by_next_push_without_rewriting_history(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from aeroscapes_pack import release_queue

    github = GitHub("owner/repo", "test-token")
    state = [release_queue.Checkpoint(sha="a" * 40)]
    monkeypatch.setattr(release_queue, "load_checkpoint", lambda *_args: state[0])
    monkeypatch.setattr(release_queue, "ensure_fast_forward", lambda *_args: None)
    monkeypatch.setattr(release_queue, "main_head", lambda _github: "c" * 40)
    monkeypatch.setattr(release_queue, "git", lambda *_args: "b" * 40 + "\n" + "c" * 40)
    monkeypatch.setattr(
        release_queue,
        "pushes",
        lambda _github: (
            push("a" * 40, "b" * 40, result="failure"),
            push("b" * 40, "c" * 40),
        ),
    )

    def save(
        _github: GitHub,
        _baseline: str,
        head: str,
        pending: release_queue.Push | None = None,
    ) -> None:
        state[0] = release_queue.Checkpoint(sha=head, pending=pending)

    publications: list[str] = []

    def publish(_project: Project, _github: GitHub, candidate: release_queue.Push) -> None:
        assert state[0].pending == candidate
        publications.append(candidate.head)

    monkeypatch.setattr(release_queue, "save_checkpoint", save)
    monkeypatch.setattr(release_queue, "publish_push", publish)
    assert release_queue.reconcile(Project(tmp_path), github, "a" * 40, publish=True) == "c" * 40
    assert publications == ["c" * 40]
    assert state[0] == release_queue.Checkpoint(sha="c" * 40)


@pytest.mark.parametrize("defect", ["none", "expired", "checksum", "extra", "missing"])
def test_ci_artifact_is_bound_to_exact_attempt_and_checked_bytes(
    monkeypatch: pytest.MonkeyPatch,
    defect: str,
) -> None:
    from aeroscapes_pack.github_release import checksum
    from aeroscapes_pack.release_cli import checked_artifact

    github = GitHub("owner/repo", "token")
    name = "pack.mrpack"
    content = b"verified pack"
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr(name, content)
        archive.writestr(
            name + ".sha256", b"wrong" if defect == "checksum" else checksum(name, content)
        )
        if defect == "extra":
            archive.writestr("../../outside", b"untrusted")
    records: list[object] = [{"name": "release-42-1", "id": 1, "expired": False}]
    if defect != "missing":
        records.append({"name": "release-42-2", "id": 2, "expired": defect == "expired"})
    monkeypatch.setattr(github, "json", lambda *_args: {"artifacts": records})

    def download(_method: str, path: str) -> bytes:
        assert path == "/actions/artifacts/2/zip"
        return buffer.getvalue()

    monkeypatch.setattr(github, "request", download)
    if defect == "none":
        assert checked_artifact(github, 42, 2, name) == content
    else:
        with pytest.raises(PackError):
            checked_artifact(github, 42, 2, name)
