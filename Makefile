.DEFAULT_GOAL := verify
.PHONY: sync pack-sync verify quality lint format typecheck security deadcode yaml workflows check test build refresh bootstrap companion companion-check source-archive docs-check docs-build docs-preview ci-java docs-sync smoke-ship release-reconcile release-docs release-docs-current docs-format release-modrinth release-curseforge curseforge-check modrinth-profile-check

UV ?= uv
NPM ?= npm
PACK_TOOLS := tools/pack
MOD_PROJECT := mods/aeroscapes-reimagine
UV_RUN := $(UV) run --locked --project $(PACK_TOOLS)
export UV_CACHE_DIR := $(CURDIR)/.cache/uv
export UV_PYTHON_INSTALL_DIR := $(CURDIR)/.cache/uv/python
export UV_PYTHON_BIN_DIR := $(CURDIR)/.cache/uv/bin
export GRADLE_USER_HOME := $(CURDIR)/.cache/gradle
export GOCACHE := $(CURDIR)/.cache/go-build
export GOMODCACHE := $(CURDIR)/.cache/go-mod
export JAVA_HOME

ci-java:
	tools/ci/install-java.sh

docs-sync:
	tools/ci/docs-sync.sh

pack-sync:
	$(UV) sync --locked --project $(PACK_TOOLS)

sync: pack-sync
	cd tools/docs && $(NPM) ci --ignore-scripts

verify: quality check test docs-check modrinth-profile-check

quality: lint typecheck security deadcode yaml workflows companion-check

lint:
	cd $(PACK_TOOLS) && $(UV) run --locked ruff check src tests
	cd $(PACK_TOOLS) && $(UV) run --locked ruff format --check src tests

format:
	cd $(PACK_TOOLS) && $(UV) run --locked ruff format src tests
	cd $(MOD_PROJECT) && ./gradlew --no-daemon spotlessApply
	cd tools/docs && $(NPM) run format

docs-check:
	cd tools/docs && $(NPM) run check

docs-build:
	cd tools/docs && $(NPM) run build

smoke-ship: companion-check
	tools/pack/scripts/ship-smoke.sh "$(JAVA_BINARY)" "$(EULA_FILE)"

docs-preview:
	cd tools/docs && $(NPM) run preview

companion:
	cd $(MOD_PROJECT) && ./gradlew --no-daemon check jar
	cp $(MOD_PROJECT)/build/libs/aeroscapes-reimagine-0.1.0.jar pack/mods/aeroscapes-reimagine-0.1.0.jar
	$(UV_RUN) aeroscapes-pack source-archive

source-archive:
	$(UV_RUN) aeroscapes-pack source-archive

companion-check:
	cd $(MOD_PROJECT) && ./gradlew --no-daemon check jar
	cmp $(MOD_PROJECT)/build/libs/aeroscapes-reimagine-0.1.0.jar pack/mods/aeroscapes-reimagine-0.1.0.jar

typecheck:
	cd $(PACK_TOOLS) && $(UV) run --locked ty check

security:
	cd $(PACK_TOOLS) && $(UV) run --locked bandit -c pyproject.toml -r src

deadcode:
	cd $(PACK_TOOLS) && $(UV) run --locked vulture

yaml:
	$(UV_RUN) yamllint --strict .github/workflows

workflows:
	cd tools/actions && go run -mod=readonly github.com/rhysd/actionlint/cmd/actionlint

check:
	$(UV_RUN) aeroscapes-pack check

test:
	cd $(PACK_TOOLS) && $(UV) run --locked pytest

bootstrap:
	$(UV_RUN) aeroscapes-pack bootstrap

refresh:
	$(UV_RUN) aeroscapes-pack refresh

build:
	$(UV_RUN) aeroscapes-pack build

release-reconcile:
	$(UV_RUN) python -m aeroscapes_pack.release_queue

release-docs:
	$(UV_RUN) python -m aeroscapes_pack.release_docs

release-docs-current:
	$(UV_RUN) python -m aeroscapes_pack.release_docs --check-current

docs-format:
	cd tools/docs && $(NPM) run format

release-modrinth:
	$(UV_RUN) python -m aeroscapes_pack.modrinth_release

modrinth-profile-check:
	$(UV_RUN) python -m aeroscapes_pack.modrinth_profile

curseforge-check:
	$(UV_RUN) python -m aeroscapes_pack.curseforge_pack

release-curseforge:
	$(UV_RUN) python -m aeroscapes_pack.curseforge_release
