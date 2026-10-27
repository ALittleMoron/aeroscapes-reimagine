"""Decode untrusted structured data before it enters the domain model."""

import json
import tomllib
from pathlib import Path

type Table = dict[str, object]


class PackError(ValueError):
    """An expected input, integrity or installation failure."""


def json_value(content: str | bytes, context: str) -> object:
    """Keep the decoder's dynamic return type at this boundary."""
    try:
        result: object = json.loads(content)
    except (ValueError, UnicodeError) as error:
        raise PackError(f"{context}: invalid JSON: {error}") from error
    return result


def read_json(path: Path) -> object:
    return json_value(path.read_bytes(), str(path))


def toml_table(content: str, context: str) -> Table:
    try:
        result: object = tomllib.loads(content)
    except ValueError as error:
        raise PackError(f"{context}: invalid TOML: {error}") from error
    return table(result, context)


def read_toml(path: Path) -> Table:
    try:
        content = path.read_text(encoding="utf-8")
    except UnicodeError as error:
        raise PackError(f"{path}: invalid UTF-8 TOML: {error}") from error
    return toml_table(content, str(path))


def table(value: object, context: str) -> Table:
    if not isinstance(value, dict):
        raise PackError(f"{context}: expected a table/object")
    result: Table = {}
    for key, item in value.items():
        if not isinstance(key, str):
            raise PackError(f"{context}: object keys must be strings")
        result[key] = item
    return result


def array(value: object, context: str) -> list[object]:
    if not isinstance(value, list):
        raise PackError(f"{context}: expected an array")
    return list(value)


def text(value: object, context: str) -> str:
    if not isinstance(value, str) or not value:
        raise PackError(f"{context}: expected a nonempty string")
    return value


def integer(value: object, context: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise PackError(f"{context}: expected a nonnegative integer")
    return value


def field(record: Table, key: str, context: str) -> str:
    """Read one required, nonempty text field with its location in an error."""
    return text(record.get(key), f"{context}.{key}")


def strings(value: object, context: str) -> tuple[str, ...]:
    return tuple(text(item, context) for item in array(value, context))
