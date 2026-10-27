"""Expected failures shared by both command-line entry points."""

# Exception types only; this module never executes child processes.
import subprocess  # nosec B404
import zipfile

from aeroscapes_pack.data import PackError

EXPECTED_ERRORS = (PackError, OSError, UnicodeError, zipfile.BadZipFile, subprocess.SubprocessError)


def error_message(error: Exception) -> str:
    if isinstance(error, subprocess.CalledProcessError):
        details = error.stderr or error.stdout or ""
        return f"{error}\n{details[-8000:]}".rstrip()
    return str(error)
