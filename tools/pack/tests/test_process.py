"""Exercise shutdown and output limits with real disposable child processes."""

import os
import subprocess
import sys
from pathlib import Path

import pytest

from aeroscapes_pack.data import PackError
from aeroscapes_pack.runtime import java_binary, run_command
from aeroscapes_pack.smoke import CaptureSettings, capture_process


def test_ready_child_receives_save_and_stop(tmp_path: Path) -> None:
    script = """
import sys
print('Done (0.1s)! For help, type "help"', flush=True)
for line in sys.stdin:
    print(line.strip(), flush=True)
    if line.strip() == 'stop':
        break
"""
    result = capture_process([sys.executable, "-c", script], tmp_path, [], CaptureSettings(5))
    assert result.ready
    assert result.exit_code == 0
    assert "save-all flush" in result.output
    assert "stop" in result.output


@pytest.mark.parametrize(
    "output",
    ['Done (0.1s)! For help, type "help"', "Regression finished"],
)
def test_additional_readiness_needs_both_signals(tmp_path: Path, output: str) -> None:
    result = capture_process(
        [sys.executable, "-c", "import sys; print(sys.argv[1], flush=True)", output],
        tmp_path,
        [],
        CaptureSettings(timeout_seconds=5, ready_marker="Regression finished"),
    )
    assert not result.ready
    assert result.exit_code == 0


def test_fixture_runs_before_shutdown_commands(tmp_path: Path) -> None:
    script = """
import select, sys
print('Done (0.1s)! For help, type "help"', flush=True)
if select.select([sys.stdin], [], [], 0.1)[0]:
    print('premature command: ' + sys.stdin.readline(), flush=True)
    raise SystemExit(9)
print('Regression finished', flush=True)
for line in sys.stdin:
    print(line.strip(), flush=True)
    if line.strip() == 'stop':
        break
"""
    result = capture_process(
        [sys.executable, "-c", script],
        tmp_path,
        ["say checked"],
        CaptureSettings(timeout_seconds=5, ready_marker="Regression finished"),
    )
    assert result.ready
    assert result.exit_code == 0
    assert "premature" not in result.output
    assert result.output.index("Regression finished") < result.output.index("say checked")
    assert "save-all flush\nstop" in result.output


def test_crashed_child_is_reported_without_readiness(tmp_path: Path) -> None:
    result = capture_process(
        [sys.executable, "-c", "raise SystemExit(7)"],
        tmp_path,
        [],
        CaptureSettings(5),
    )
    assert not result.ready
    assert result.exit_code == 7


def test_timeout_stops_owned_child_and_preserves_log(tmp_path: Path) -> None:
    script = "import os,time; print(os.getpid(), flush=True); time.sleep(60)"
    settings = CaptureSettings(timeout_seconds=0.5, stop_grace_seconds=0.05)
    with pytest.raises(PackError, match="exceeded"):
        capture_process([sys.executable, "-c", script], tmp_path, [], settings)
    child_pid = int((tmp_path / "smoke.log").read_text(encoding="utf-8").strip())
    with pytest.raises(ProcessLookupError):
        os.kill(child_pid, 0)


def test_noisy_child_cannot_grow_log_without_bound(tmp_path: Path) -> None:
    script = "import time; print('x' * 10000, flush=True); time.sleep(60)"
    settings = CaptureSettings(timeout_seconds=5, stop_grace_seconds=0.05, max_log_bytes=128)
    with pytest.raises(PackError, match="log exceeds"):
        capture_process([sys.executable, "-c", script], tmp_path, [], settings)
    assert (tmp_path / "smoke.log").stat().st_size <= settings.max_log_bytes + 1


def test_full_command_pipe_cannot_bypass_the_capture_timeout(tmp_path: Path) -> None:
    script = """
import pathlib, time
print('Done (0.1s)! For help, type "help"', flush=True)
time.sleep(1)
pathlib.Path('timeout-missed').write_text('child outlived the capture deadline')
"""
    settings = CaptureSettings(timeout_seconds=0.2, stop_grace_seconds=0.05)
    with pytest.raises(PackError, match="exceeded"):
        capture_process([sys.executable, "-c", script], tmp_path, ["x" * 262144], settings)
    assert 'Done (0.1s)! For help, type "help"' in (tmp_path / "smoke.log").read_text(
        encoding="utf-8"
    )
    assert not (tmp_path / "timeout-missed").exists()


def test_commands_larger_than_the_pipe_are_delivered_without_truncation(tmp_path: Path) -> None:
    script = """
import sys
print('Done (0.1s)! For help, type "help"', flush=True)
for line in sys.stdin:
    if line.startswith('x'):
        print('command length=' + str(len(line.strip())), flush=True)
    else:
        print(line.strip(), flush=True)
    if line.strip() == 'stop':
        break
"""
    result = capture_process(
        [sys.executable, "-c", script], tmp_path, ["x" * 262144], CaptureSettings(5)
    )
    assert result.ready
    assert result.exit_code == 0
    assert "command length=262144\nsave-all flush\nstop\n" in result.output


def test_exited_parent_cannot_leave_a_descendant_holding_the_pipe(tmp_path: Path) -> None:
    script = """
import subprocess, sys
child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'])
print('grandchild=' + str(child.pid), flush=True)
print('parent finished', flush=True)
"""
    settings = CaptureSettings(timeout_seconds=0.5, stop_grace_seconds=0.05)
    with pytest.raises(PackError, match="exceeded"):
        capture_process([sys.executable, "-c", script], tmp_path, [], settings)
    assert "parent finished" in (tmp_path / "smoke.log").read_text(encoding="utf-8")
    log = (tmp_path / "smoke.log").read_text(encoding="utf-8")
    child_pid = int(
        next(line.split("=")[1] for line in log.splitlines() if line.startswith("grandchild="))
    )
    try:
        state = (Path("/proc") / str(child_pid) / "stat").read_text(encoding="utf-8").split()[2]
    except FileNotFoundError:
        state = "gone"
    assert state in {"gone", "Z"}  # A zombie has terminated and awaits its new parent's reap.


def test_tool_failure_preserves_bounded_diagnostics() -> None:
    script = "import sys; print('tool failed', file=sys.stderr); raise SystemExit(7)"
    with pytest.raises(subprocess.CalledProcessError) as failure:
        run_command([sys.executable, "-c", script])
    assert failure.value.returncode == 7
    assert failure.value.stderr == "tool failed\n"


@pytest.mark.parametrize("major", [21, 25])
def test_explicit_java_command_is_resolved_on_path(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    major: int,
) -> None:
    java = tmp_path / "java"
    java.write_text(
        f"#!{sys.executable}\nimport sys\n"
        f"print('openjdk version \"{major}.0.1\"', file=sys.stderr)\n",
        encoding="utf-8",
    )
    java.chmod(0o755)
    monkeypatch.setenv("PATH", str(tmp_path))
    assert java_binary("java", major) == str(java)


def test_java_runtime_must_match_the_pack_requirement(tmp_path: Path) -> None:
    java = tmp_path / "java"
    java.write_text(
        f"#!{sys.executable}\nimport sys\nprint('openjdk version \"21.0.1\"', file=sys.stderr)\n",
        encoding="utf-8",
    )
    java.chmod(0o755)
    with pytest.raises(PackError, match="Use Java 25"):
        java_binary(str(java), 25)


def test_tool_timeout_stops_the_child(tmp_path: Path) -> None:
    pid_file = tmp_path / "pid"
    script = """
import os, sys, time, pathlib
pathlib.Path(sys.argv[1]).write_text(str(os.getpid()))
time.sleep(60)
"""
    with pytest.raises(subprocess.TimeoutExpired):
        run_command([sys.executable, "-c", script, str(pid_file)], timeout_seconds=1)
    child_pid = int(pid_file.read_text(encoding="utf-8"))
    with pytest.raises(ProcessLookupError):
        os.kill(child_pid, 0)


def test_unrelated_done_log_is_not_a_ready_message(tmp_path: Path) -> None:
    result = capture_process(
        [sys.executable, "-c", "print('mod: Done (background job)')"],
        tmp_path,
        [],
        CaptureSettings(5),
    )
    assert not result.ready
