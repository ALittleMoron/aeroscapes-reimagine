# Documentation site

Public Markdown/MDX lives in `docs/en/` and `docs/ru/`. This project builds both
languages with Astro and Starlight. The content collection contains the public
player documentation.

Use Node 26.8.2 and its npm 11.19.1, pinned in `.node-version` and `package.json`.
Select that runtime in your environment before using the root Makefile; installing
or updating system-wide tools is not part of the build.

From the repository root:

```sh
make sync
make docs-check
make docs-preview
```

`sync` installs the locked Python and npm environments. `docs-check` checks
Prettier formatting, Astro/TypeScript diagnostics, validation behavior and the
static build, including local routes and anchors. `make verify` includes it.
`make docs-build` rebuilds `tools/docs/dist/`; preview serves that output locally.
Use `make format` for the project's formatters, or `npm run format` here for the
site alone. Dependency versions and integrity hashes are locked in `package-lock.json`.

Each public page has a counterpart at the same relative path in the other
language. Required `status` is `available`, `partial` or `planned`, matching the
pair; partial/planned pages show a notice. Structural checks cannot establish
meaning: review both versions together. Use relative trailing-slash site routes
for internal links. The default project prefix is `/aeroscapes-reimagine/`.
`DOCS_SITE` and `DOCS_BASE` set the deployment origin and prefix.

Every gameplay direction is a collapsible sidebar group with an Overview entry,
including directions that currently have only that page. World generation
lives under `experience/world/`; the Releases group contains the release guide,
changelog and installation. Compatibility, inventory and credits remain separate
references. Keep paired pages consistent in facts,
implementation status and practical instructions; use natural wording in each language.

`src/styles/custom.css` owns the site palette, typography and page styling.
The selected Night Sky and Gold palette uses slate-blue surfaces and warm gold
accents, with a matching light variant. Both use Starlight's existing theme
selectors, preserving the automatic mode and the reader's manual theme choice.
The homepages use Starlight's existing text hero and link-card components with the
selected Mysterious Islands title cover from `src/components/PackCover.astro`.
The project README uses the same full-size cover.
Its editable source, font provenance and manual rendering commands live in
[`src/assets/branding/README.md`](src/assets/branding/README.md). Prebuilt WebP files
are served directly, so the website build needs no fonts or image-rendering CLI.
The gallery and subject pages share `src/components/Screenshot.astro`;
`src/screenshots.ts` owns their paired alternative text and captions. The component
serves 640/1920-pixel asset URLs with `srcset` and ordinary links to larger images,
without runtime JavaScript or another image transformation. Source hashes, owner
authorization and CLI conversion settings live in
[`src/assets/screenshots/README.md`](src/assets/screenshots/README.md).
Add screenshots beside the subject they explain; establish reuse permission,
retain required attribution, and provide alternative text and captions in both
languages. Label illustrations separately from pack screenshots.

`reference/mods.mdx` uses one component to read included pinned artifact metadata;
do not copy the file list into Markdown. The paired changelog is the source for
site release notes and future platform descriptions of changes. There is no
separate editable platform changelog.

Successful main-push CI can deploy the site through `Reconcile publications`
when repository variable `PAGES_ENABLED=true`. The switch defaults to off;
GitHub Pages must use GitHub Actions as its source. Follow the
[publication setup](../pack/README.md#one-time-setup) before enabling it.
Local builds and previews do not publish anything or change remote settings.

Deployment inserts download links from verified immutable GitHub releases into
the neutral installation instructions in an ephemeral checkout. Numbered
changelog headings and compatibility pages remain unchanged. Main is checked again immediately before
deployment. The site carries `noindex, nofollow`; publishing it does not provide
private hosting.

Framework references: [Astro collections](https://docs.astro.build/en/guides/content-collections/),
[Starlight locales](https://starlight.astro.build/guides/i18n/),
[GitHub Pages deployment](https://docs.astro.build/en/guides/deploy/github/).
