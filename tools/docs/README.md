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

The sidebar groups each gameplay overview with its child pages. World generation
lives under `experience/world/`; the shared inventory, changelog, support and
credits remain cross-project references. Keep paired pages consistent in facts,
implementation status and practical instructions; use natural wording in each language.

`src/styles/custom.css` owns the site palette, typography and page styling.
The homepage uses Starlight's existing hero and link-card components. The original
`src/assets/islands.svg` is decorative illustration, not gameplay evidence.
Add real pack screenshots beside the subject they explain when they are available;
establish reuse permission, retain required attribution, and provide alternative
text and captions in both languages. Label illustrations separately from pack screenshots.

`reference/mods.mdx` uses one component to read included pinned artifact metadata;
do not copy the file list into Markdown. The paired changelog is the source for
site release notes and future platform descriptions of changes. There is no
separate editable platform changelog.

The Pages workflow is manual and its publication input defaults to false. Builds
and previews do not publish anything. A future authorized publication requires
GitHub Pages configuration, release/third-party permission acceptance and a
deliberate workflow run with publication enabled. Until then the site carries
`noindex, nofollow`; it is a draft website, not private hosting or a public pack
release. No remote settings are changed by these tools.

Framework references: [Astro collections](https://docs.astro.build/en/guides/content-collections/),
[Starlight locales](https://starlight.astro.build/guides/i18n/),
[GitHub Pages deployment](https://docs.astro.build/en/guides/deploy/github/).
