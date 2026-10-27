# Modrinth project presentation

`profile.toml` owns the page title, summary, English Markdown, documentation
link and selected icon provenance. It describes current play; release changelogs
remain separate. Runtime/platform tokens come from the pinned pack and toolchain.
These publishing inputs stay outside the exported Minecraft pack.

Use `make modrinth-profile-check` to validate locally, without credentials or
network. To export the prepared payload and text for review:

```sh
uv run --locked --project tools/pack python -m aeroscapes_pack.modrinth_profile --output .runtime/modrinth-profile/rendered
```

The project description is in English and links to documentation available in
English and Russian. Change the source and let the release workflow reconcile
the current profile when the integration is enabled.
Never derive the page from each historical release in the upload loop.

## Images and provenance

`icon.png` is Delapouite's unmodified **Floating platforms** PNG from Game-icons.net,
under CC BY 3.0. See `ARTWORK-NOTICE.txt` for source and exact hash. The rendered
description retains title/author/source/license attribution. It is an existing
stock symbol, not exclusive project artwork. Do not replace it while keeping its
old provenance or mark new AI-assisted artwork human-authored.

`concepts/aeroscapes-mark.svg` is a separate procedural design study, with an
adjacent PNG rendered using ordinary librsvg:

```sh
rsvg-convert --width 512 --height 512 tools/pack/publishing/modrinth/concepts/aeroscapes-mark.svg --output tools/pack/publishing/modrinth/concepts/aeroscapes-mark.png
```

No image-generation model produced that study, but its vector source was written
with AI assistance. Modrinth does not explicitly exempt this workflow from its
AI-image restriction. It is kept for local review and is never selected by the
profile uploader. Do not describe it as platform-approved or human-authored.

The [current Modrinth rules](https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai)
ban AI-created or generatively edited page images, including icons and branding.
Selecting unchanged human artwork is a practical interpretation of that scope,
not a moderation guarantee. The origin is documented rather than hidden.

No fabricated screenshots or image placeholders are published. The owner can
later capture three distinct scenes: island landscape; actual aircraft and dock;
home, garden or kitchen. Keep captions descriptive and preserve the screenshot's
meaning. The current uploader leaves the Modrinth gallery unchanged.

## API and first enablement

The integration is retained and disabled by default. Leave `MODRINTH_ENABLED`
unset or `false` and do not configure its project/token variables to keep it off.
GitHub Releases and Pages are independent of this destination.

Stable Modrinth v2 supports project Markdown/summary/links and raw icon PATCH.
The workflow compares owned fields and original icon bytes, writes only changes,
and verifies readback. It does not rewrite immutable release files or historical
version notes. A partial failure is reconciled on rerun.

Enable `MODRINTH_PROFILE_ENABLED=true` only after reviewing the text/icon and
checking the documentation links are public. The current target Pages address
has not been confirmed reachable; do not advertise a private repository as a
public source or support link. The profile owns only its configured wiki link;
existing source/issues/Discord fields are preserved.

The Modrinth token needs **PROJECT_WRITE** in addition to version-upload access;
the account/team role needs **EDIT_BODY** and **EDIT_DETAILS**. Project/profile
creation, approval and moderation remain outside this synchronization.

Before any Modrinth publishing, the owner must set the appropriate AI/text and
other applicable content disclosures in Modrinth's UI, then set repository variable
`MODRINTH_DISCLOSURES_CONFIRMED=true`. AI-written summary/body/changelog and release
tooling require disclosure; assess the owned integration code and derivative data
honestly as well. Included mods do not automatically inherit the pack's disclosure.
The flag records an owner acknowledgement: stable v2 exposes no disclosure read or
write endpoint, so automation cannot verify the selection. The experimental v3
route explicitly says not to use it in production and is not called.

References: [project PATCH](https://docs.modrinth.com/api/operations/modifyproject/),
[icon PATCH](https://docs.modrinth.com/api/operations/changeprojecticon/),
[content disclosures](https://support.modrinth.com/en/articles/16567675-content-disclosures),
[v3 production warning](https://github.com/modrinth/code/blob/cf0a3ca593506c543ed63c35986e31ade1b19b2e/apps/labrinth/src/api_v3_description.md).
