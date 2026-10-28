---
title: Releases and versions
description: Major, minor and patch milestones, release preparation and the connection to Git.
status: available
---

An Aeroscapes Reimagine release records a completed scope of pack changes and the source code used to produce its archive. Its number follows **`major.minor.patch`**, such as `0.1.1`. The three parts describe the scale of the completed work.

## Reading the number

| Part  | What it records                                             | Example |
| ----- | ----------------------------------------------------------- | ------- |
| Major | A substantial stage in the development of the whole pack    | `1.0.0` |
| Minor | A completed content area or development direction           | `0.2.0` |
| Patch | Another set of additions and refinements within a direction | `0.1.1` |

### Major: a substantial completed stage

The major number changes when the pack reaches a new large goal. That transition **does not inherently mean breaking changes** or incompatibility with the previous release. It identifies the project stage that has been completed.

The project's major goals are:

| Milestone | Result                                                                                                                                                                                                                              |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `1.0.0`   | The complete agreed core: required major mods and refinements are implemented and checked together.                                                                                                                                 |
| `2.0.0`   | Deep integration between systems: for example, magical tools help players learn and develop engineering, and activities across different areas support each other. Additional quests or a quest line may explain those connections. |

As the project develops, new large goals will receive subsequent major milestones, such as `3.0.0`. Their scope is defined with the goals themselves.

### Minor: a completed content area

A minor milestone concludes work on a particular direction. `0.1.0` established the initial foundation: the island world, Create and Aeronautics, basic optimization and convenience. `0.2.0` is the chosen atmosphere milestone: an agreed set of visual, animation and sound improvements checked together with the rest of the pack.

Patch releases can gradually build toward that result. One might introduce creature animations and resourcepacks; another might refine lighting. Once the selected scope is ready and accepted by the author, the next minor number records it, with the last part reset to zero. Other areas can also change between these milestones.

A minor release can conclude work already delivered through preceding patches. It does not require adding another mod solely to justify the number.

### Patch: further additions and fixes

Patches can include new mods, resourcepacks, settings, compatibility work and fixes. The last number does not restrict a release to bug fixes.

For example, after `0.1.0`, an initial animation and resourcepack wave could be `0.1.1`, followed by another refinement in `0.1.2`. All further changes accumulate under the selected number until publication. Local rebuilds and preparatory commits do not increment it on their own.

## Release readiness and compatibility

Public releases use ordinary numerical versions without alpha, beta or test suffixes. Each records a completed and accepted scope of work. Releases numbered `0.x.y` also have their own agreed scope while the broader vision of the pack continues to develop.

Saved-world compatibility depends on the actual changes, not on which number increased. Release notes explain the update conditions; practical world-handling instructions are in the [saved-world guidance](../known-issues/#saved-worlds).

## From changes to a release

1. Pack changes are collected into **one final PR or push to `main`**. It may contain several preparatory commits; intermediate commits within one push do not produce separate versions.
2. `pack/pack.toml` defines the version. The English and Russian changelogs each contain one corresponding section, such as `## 0.1.1`, with that release's actual changes.
3. The PR title and the first line of the final commit reaching `main` include the number, for example `feat: [0.1.1] animations and foliage`. A squash or merge commit must preserve the number in its final title too.
4. CI checks the source and export. For a new version, it also validates the number in the integration title and both changelog sections. Only a successful main push supplies the checked archive for publication.
5. Automation binds the `v0.1.1` tag to the final commit's exact SHA and publishes the checked `.mrpack` with a checksum. Change descriptions come from that version's section in the same source snapshot. Preparatory commits may appear as work history, but the release belongs to one checked state of the pack.

Site documentation and infrastructure changes that preserve the exported pack's contents can retain the same pack version. CI establishes this by comparing archive contents with the published version; a filename or changed source path does not replace that comparison.

## A missing version in the title

For a PR, correct its title: editing the title triggers CI again. If the mistake has already reached `main`, the check prevents publication. A normal follow-up commit with the correct number in its title can repair it. Until publication, the target number stays the same, and the next successful push includes the accumulated changes.

Published version contents are immutable. Further changes need the next number; the tag, archive and checksum identify the original release independently of wording in Git history.

[Download and install](../install/) · [Changelog](../changelog/)
