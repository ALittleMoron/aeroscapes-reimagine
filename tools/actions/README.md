# Workflow validation

`make workflows` runs the pinned actionlint tool from this Go module. `go.mod`
and `go.sum` lock the tool and its dependencies; the command uses read-only
module resolution. It validates GitHub Actions expressions and reusable-workflow
contracts in addition to the YAML formatting check. `make verify` and CI run it.

Use Go 1.27.3. Build/module caches stay under the repository's ignored `.cache/`.
This project contains tooling dependencies only; it is not part of the pack.
