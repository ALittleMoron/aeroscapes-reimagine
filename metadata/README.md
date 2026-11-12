# Third-party provenance and integration

Exact files, hashes, sides and embedded/API license differences live in [`metadata/artifacts.json`](../metadata/artifacts.json). [`worldgen_adoption.json`](../metadata/worldgen_adoption.json) records incorporated Aero data and Java behavior comparisons; [`compat_fixes.json`](../metadata/compat_fixes.json) binds static repairs; [`runtime_data_patches.json`](../metadata/runtime_data_patches.json) owns local JSON recipes; [`native_template_upgrades.json`](../metadata/native_template_upgrades.json) retains historical DFU reference hashes. [Public credits](../docs/en/credits.md) and `pack/licenses/THIRD-PARTY-NOTICES.txt` preserve attribution. The [publication workflow](../tools/pack/README.md#automated-releases) verifies exact artifacts; distribution scope must be reviewed separately for each channel.

## Working principle

Keep original upstream JARs byte-identical, retain notices, and prefer independently authored field-level integration over distributing complete modified upstream resources. Bind each change to exact provider/platform versions, distinguish artifact descriptors from file-specific terms, and preserve the scope of those terms when changing a dependency or extending an integration. Runtime processing does not itself relicense source material. Re-evaluate new scope using the actual files and applicable terms; do not treat the current pack's arrangement as blanket permission for future versions or other projects.

## Mechanical workshop and foundry in 0.5.2

[Vintage Improvements — SSW Edition 0.0.0.8](https://www.curseforge.com/minecraft/mc-mods/vintage-improvenents-ssw-edition/files/8941692)
is the unchanged official CurseForge file. The publisher declares LGPL-2.1;
the descriptor and source declare MIT. The inherited source `LICENSE.txt`
is a Forge/FML notice and does not resolve that difference. The pack preserves
both declarations and satisfies both sets of conditions: its replaceable JAR,
complete corresponding source, original attribution and both license texts
travel together. This does not assert that the publisher corrected its page
or that one declaration overrides the other.

A clean build of source commit
[`1a7e49e`](https://github.com/SSWTLZZ69/Create-Vintage-Improvements/tree/1a7e49e501e15d0ed4620cc501f9b5fa6c0fc703)
reproduced all 221 compiled classes byte for byte with no missing or additional
classes. The distributed source package also includes ten editable generated
JSON resources absent from that Git snapshot. Its provenance and rebuild
instructions accompany `pack/licenses/Vintage-SSW-NOTICE.txt` and
`pack/licenses/vintage-ssw-0.0.0.8-corresponding-source.zip`.

[Create: Metallurgy 1.0.3](https://modrinth.com/mod/create-metallurgy/version/4RhIMmaJ)
is byte-identical to official CurseForge file 8128243. Both platforms and its
descriptor declare MIT. Existing Create, JEI and Jade pins meet the actual
required and installed optional dependency ranges. No platform upgrade is needed.

The required compatibility datapack filters the two removed Vintage recipes,
four multiplying wire recipes and six obsolete-format crushing overrides.
Project recipes preserve common material costs and sulfur/vanadium byproducts.
The seventeen native glass laser recipes receive the port's missing glass input
tags from the installed matching materials, retaining their costs and outputs.
Steel sheets are independently authored material items. CBC spring wire and
Theurgy copper wire retain their separate costs and do not join generic wire
melting tags. Ladles retain native meshes and full-stack fluid storage; the
owned safe-handling adapter only suppresses selected native ignition events.

## Engineering additions in 0.5.1

[RSGauges Unofficial Fix 1.0.0](https://modrinth.com/mod/rsgauges-unofficial-fix/version/1.0.0)
is pinned to the CDN file linked by the author’s Modrinth release page. The
version and project API lookups returned HTTP 404 while the linked JAR was
available. Record the SHA-512 and SHA-1 of those bytes before replacing this pin.
The similarly named CurseForge port has different content and a different author.

[Create: Liquid Fuel 3.0.0](https://modrinth.com/mod/create-liquid-fuel/version/rLn2bDVE)
is present in the client distribution for the integrated server. The Modrinth
project lists ARR and the JAR declares MIT. The unchanged archive supplies lava
as ordinary fuel through a NeoForge fluid data map. Entries for absent mods stay
conditional. The archive adds no items, screens or models.

[Create Big Cannons 5.11.7](https://modrinth.com/mod/create-big-cannons/version/bOiDu0LS)
and [Ritchie’s Projectile Library 2.1.2](https://modrinth.com/mod/rpl/version/hZ6B2Z0x)
match their official CurseForge downloads. CBC’s license separates MIT code and
localization from its CC BY-NC-SA 4.0 handmade artwork. The authored Island Atlas
panels and handheld meshes reference native icon locations and use original
texture and mesh data. The CBC server config enables projectile chunk tickets
and disables smoke chunk tickets.

[Create: Connected 1.3.3](https://modrinth.com/mod/create-connected/version/Xe7EqzfQ)
uses the pack’s existing Create and optional JEI pins. Connected and Liquid Fuel
each have an official CurseForge download whose sole archive-entry difference
from the selected Modrinth file is the manifest. The CurseForge mapping records
the resulting byte-parity limits. The required compatibility datapack gates
unregistered Connected and RSGauges block drops so the game can decode the
available tables. It preserves the original drop rules when their items exist.

## Equipment quality and reforging

[Hero’s Tiered 1.2.0](https://modrinth.com/mod/heros-tiered/version/GY1XViBt) and
[Hero’s Lib 1.1.2](https://modrinth.com/mod/heros-lib/version/QtGWytnQ) run on both
sides with the existing [Cloth Config 15.0.140](https://modrinth.com/mod/cloth-config/version/izKINKFg).
The official CurseForge CDN JARs match the Modrinth pins byte for byte. The
library’s optional client integration requires
[EMI 1.1.24](https://modrinth.com/mod/emi/version/5sIPA1To) when the recipe viewer
is installed. The client rejects the previous 1.1.22 pin during mod loading.
The required equipment datapack filters the provider’s default item attributes and
defines the pack’s rank pools and catalyst. The owned mod redirects the
return from reforging to the anvil block’s menu provider, which exposes the
inputs held by [Easy Anvils 21.1.0](https://modrinth.com/mod/easy-anvils/version/fSQSKhdF).
The provider JARs are distributed through their original channels.

The legendary sword rank has zero initial-roll weight. The reforge selector
adds one to its weight, and the selected chest tables can append an iron sword
with the rank component already set. The owned effect reads the weapon of a
successful charged primary hit and applies Slowness I without refreshing an
existing Slowness effect.

[Yori3o’s Grappling Hooks 5.4.0](https://modrinth.com/mod/yori3os-grappling-hooks/version/lf4DqQBf)
defines native repair materials and already supports Unbreaking and Mending
through the vanilla durability enchantment tag. Explicit mappings let the
reforge menu recognize those materials. Hook rank scales native ascent and
descent using the actual hook stack and leaves the native rope limits in place.

## First spells and casting animation

[Iron’s Spells ’n Spellbooks 3.16.3](https://modrinth.com/mod/irons-spells-n-spellbooks/version/slKLosTb)
uses [Iron’s Lib 2.1.0](https://modrinth.com/mod/irons-lib/version/sQyzhxuH),
[Curios 9.5.1](https://modrinth.com/mod/curios/version/yohfFbgD),
[Player Animator 2.0.4](https://modrinth.com/mod/playeranimator/version/HJZB6bmA)
and the already pinned [GeckoLib 4.9.3](https://modrinth.com/mod/geckolib/version/Grwn5rUB)
on both sides. Modrinth omits the spell mod's required dependency edges, so
the lock records the requirements from its NeoForge descriptor. The two
spell-mod downloads differ only in the manifest build timestamp. The three
new libraries match across distribution channels byte for byte.

Player Animator is an exact beta under client acceptance. The pack’s sleeve
gesture uses a separate layer. The pinned [Entity Model Features](https://modrinth.com/mod/entity-model-features)
pause API yields during casting and the gesture, when [Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fresh-animations-player-extension)
would otherwise replace active player transforms. Both ordinary animation
resumption and first-person casting require a client check.

The pack retains the original magic archives and structure templates. Owned
loot adds two level-one choices and the required datapack sets travel spell
limits. The provider’s `enabled` value filters random acquisition but misses
cast initiation from a pre-existing item. The owned server handler cancels
those casts and caps the effective player teleport level after item bonuses.
Recall requires inscription because a dimension transition resets the native
casting state before its one-use scroll charge.
Exact upstream structure resources are adapted through the existing virtual
datapack.

## Theurgy alchemy

[Theurgy 1.79.0](https://modrinth.com/mod/theurgy/version/EBO0aQOY) and
[Modonomicon 1.123.0](https://modrinth.com/mod/modonomicon/version/OKyLanIn)
are installed on both sides. Theurgy requires Modonomicon and the existing
GeckoLib 4.9.3. Its native EMI integration provides the alchemy recipes. The
original CurseForge and Modrinth JARs match byte for byte.

The held models use independently authored geometry and reference the original
Theurgy sprites. Theurgy's pinned `REUSE.toml` and JAR metadata specify CC BY 4.0
for its first-party resources. The README names CC BY-SA 4.0 but links to CC BY
4.0. The original archive also contains files with separate notices.

## Astronomy, beekeeping and wakes

[Spyglass Astronomy for NeoForge 1.0.20](https://www.curseforge.com/minecraft/mc-mods/spyglass-astronomy-neoforge/files/8338853)
is the unchanged client port. Its native compatibility check used the Fabric
identifier for [Spyglass Improvements](https://modrinth.com/mod/spyglass-improvements).
The owned client adapter supplies the NeoForge identifier so the port can use
its existing tick-based drawing fallback. The interactive sky takes over from
the pack's decorative planets and accent stars while the port is installed.
The original Sun and Moon textures still come from the atmosphere resourcepack.
With Iris shaders active, the port draws its sky objects after the world color
pass. The owned sky shader masks that pass using the current non-shadow
Distant Horizons depth texture and the main target's terrain depth. Shaderless
rendering already draws those objects before the terrain.

[Buzzier Bees 7.0.1](https://modrinth.com/mod/buzzier-bees/version/Y12rtT96)
uses the existing [Blueprint 8.0.8](https://modrinth.com/mod/blueprint/version/YSWV62mG)
on both sides. Its flower placements and moobloom addition stay native. The
pack replaces the single cow-removal biome modifier and assigns its three
prepared foods to the applicable nutrition tags. Both honey apple results
remain in the recipe registry and use the ordinary
[Polymorph](https://modrinth.com/mod/polymorph) crafting selector when they match.

[Wakes Reforged 1.4.1](https://modrinth.com/mod/wakes-reforged/version/Z3Ic8n5N)
has a client entry point and client-only mixins, including the visual integration
with [Sable 2.0.6](https://modrinth.com/mod/sable/version/fg9dTRz9). The pack
installs it on the client. Its descriptor says MIT, while the official listings
declare GPLv3. The Forge config has no active consumers for the obsolete
MidnightConfig and YACL language keys in the JAR.

## Detailed construction and biome arrivals

[LittleTiles pre236](https://modrinth.com/mod/littletiles/version/c2SowFEz) is an
explicitly owner-approved exact beta on both sides, using the already pinned
[CreativeCore 2.13.52](https://modrinth.com/mod/creativecore/version/JS0K93bG).
The unchanged executable declares LGPL-3.0-only, matching its official paired
source archive; the CurseForge project-level LGPL-2.1 label differs. Its native
[Sable](https://modrinth.com/mod/sable/version/fg9dTRz9),
[Sodium](https://modrinth.com/mod/sodium/version/uMOpc5uV) and
[Iris](https://modrinth.com/mod/iris/version/KduFYu4t) integration remains upstream.
The provider's material-volume mass calculation remains native. Sable's
state-only collision classifiers sampled the origin and excluded nonempty
LittleTiles block entities from mass, so tile-only assemblies were deleted as
empty. Two exact-version adapters classify only LittleTiles at the supplied
position using its actual collision shape. Fullness uses that shape's occupied
volume, avoiding LittleTiles' visual-side metadata for hollow or noncolliding
tiles. Ordinary blocks retain Sable's native classifiers and cache. Empty-body
cleanup and material mass values are unchanged.

[Traveler's Titles 5.1.3](https://modrinth.com/mod/travelers-titles/version/2y01mBUy)
and [YUNG's API 5.1.9](https://modrinth.com/mod/yungs-api/version/2prKITKh) are
unchanged LGPLv3 client installations. No server content consumes this API here.
The selected title binary reads the full biome ID blacklist before displaying
text, recording a recent entry or playing sound. The config hides only
`aeroscapes_reimagine:void`, disables the skylight-only restriction for lower
islands, and keeps dimension/Waystone titles off. Existing language resources
supply biome names; no generator or terrain predicate is added.

Independently authored EN/RU supplements cover LittleTiles' native names and
interfaces, dynamic particle labels, CreativeCore configuration paths and
display-only literal captions. Parser operators, permission-group identities,
user-created structure names and numeric values remain native. Player-facing
conversion/optimization command results are sent as localized components. Exact source
comparisons, scope and resource hashes live in the owned mod's
`META-INF/localization-sources.json`; original terms and attribution remain in
its notices. Official CurseForge CDN bytes for all three new providers match
their selected Modrinth files exactly, as recorded in `curseforge.json`.

LittleTiles' new key registrations use semicolon for marking, apostrophe for
mirroring, backslash for configuration and equals for building mode. Secondary
configuration retains Shift on the same key. Exact native constructors are
adapted before saved Minecraft options load; existing player bindings, arrows,
undo/redo, modifier matching and key hints remain native. The two bracket keys
are reserved by SophisticatedCore's inventory transfer controls.

## Island placement and held maps

The owned generator's volcanic islands use native lava, blackstone, deepslate and magma, with a locally authored biome and surface rule. Selected bounded small/medium island bodies sample one native surface biome at their seeded centre; the native biome roster and larger mountain/ocean habitats remain. Granite and diorite retain their adopted configured features, rarity and biome guards, but their upper placements now sample below the actual island surface. The changed adopted-resource hashes and reasons live in `worldgen_adoption.json`.

[Sable 2.0.6](https://modrinth.com/mod/sable/version/fg9dTRz9)'s `LevelAccelerator` supplies only `BlockGetter`, while [Supplementaries 3.6.8](https://modrinth.com/mod/supplementaries)'s planter-offset hook skips support reads only for a server `LevelReader`. Sable's shape cache evaluates at `BlockPos.ZERO`; the wrapper could therefore synchronously request origin chunks from inside chunk-send block updates. The owned offset adapter restores the backing server level for this method alone, retaining the provider's native server guard. `SableOffsetRegression` exercises actual transformed offsets and shape classification with an accelerator that rejects all block reads.

[Punchy 3.0](https://modrinth.com/mod/punchy-fpa/version/83nCNSX4) selects its held-map path only after saved map data arrives. The owned client adapter selects `MapItem` subclasses immediately, matching the pinned native renderer's safe blank-map fallback. The atmosphere resource pack raises both map arms through native `armTransforms`; saved tuning and active profiles retain precedence. Grip and framing remain graphical acceptance, separate from compilation and server checks.

## Wildlife, archaeology and aerial places

[Naturalist](https://modrinth.com/mod/naturalist) and
[Critters and Companions](https://modrinth.com/mod/critters-and-companions) keep
their native taming, equipment and rewards. Scoped predicates use local basin
waterlines and exposed island surfaces. The bird head-perching goal is disabled
as a whole; native slow falling and parrot ascent are off. The three livestock
removal modifiers have empty biome selections, preserving ordinary farming.
Append-only fauna tags connect river fish, warm-water sharks and deep marine
species to the already selected physical aquatic habitats. They do not expand
the global ocean tags or change native spawn weights. The natural-water fixture
checks source, raw water, saved FULL biomes and effective native spawn entries
together; an isolated predicate call is not evidence of a reachable habitat.

[Wetland Whimsy](https://modrinth.com/mod/wetland-whimsy) keeps its marsh and
ruin/archaeology/ritual loop. Its continentalness deviation is adapted to the
retained climate delegate; global disk replacement is disabled, and its guarded
replacement witch hut owns new hut generation. The exact loader uplift also
selects [Biolith](https://modrinth.com/mod/biolith) 3.0.14 explicitly on both
sides. The delegate cache/clone contract remains enforced by the owned code.

[Better Archeology](https://www.curseforge.com/minecraft/mc-mods/better-archeology)
keeps its native generators, including assemblies larger than vanilla jigsaw
limits. Only the high-surface rejection is bypassed for the owned generator;
existing support wrappers inspect native pieces and projection. Native mobility
rewards remain enabled. Its descriptor says MIT, while the official source
license separates MIT code from All Rights Reserved artwork, sounds and
structures and allows unchanged modpack inclusion. No original templates are
copied into the owned mod. [Architectury API](https://modrinth.com/mod/architectury-api)
is an unchanged shared dependency.

The four intact static designs from
[Creat Aeronautics Structures](https://modrinth.com/mod/creat-aeronautics-structures/version/aMCksPon)
and [Sky Villages](https://www.curseforge.com/minecraft/mc-mods/sky-villages-forge)
use one assembled-pieces airspace wrapper. Whole bounds fit below Y384; native
pieces and their saved anchors move together. Missing optional village pool
references are excluded. Scoped template processing removes saved author maps,
unavailable offers, incidental entities and fixed container loot seeds, while
retaining crews and valid trades. The vessel provider's unsolicited login
advertisement is suppressed. The native artifacts and their licenses remain
unchanged; exact field edits and provider hashes are inventoried alongside the
earlier compatibility repairs.

## Aquatic, movement and survival providers

The added [Hybrid Aquatic](https://modrinth.com/mod/hybrid-aquatic) habitats are
selected inside existing island basins. Its native terrain changes and biome
injectors are excluded only for the owned source; other providers' injectors
remain active. [Lithostitched](https://modrinth.com/mod/lithostitched)'s exact
distributed API is authoritative: the author branch used for comparison still
declares an older version. Local seabed and waterline adapters retain native
decoration and fauna predicates. [Tide](https://modrinth.com/mod/tide) uses
physical water-column depth for a fishing line and waterline-relative position
for its native fish spawning. Encounter retrieval precedes the ordinary reward
loop, and pending conch summons persist with the dimension. Both native encounter
hooks, the three conditional hook perks and block-only aquatic crates retain
their provider behavior. Three missing fish records restore the complete native
52-item aquatic fishing selection without adding a separate ordinary-rod mode.

[Paragliders](https://www.curseforge.com/minecraft/mc-mods/paragliders) retains
native templates and rewards. Its surface and underground structures use the
existing support wrappers; a narrow native rotation repair updates template
bounds before generation checks and chunk references. [Yori3o’s Grappling Hooks](https://modrinth.com/mod/yori3os-grappling-hooks)
retains its own motion and ship compatibility.

The gliding-only stamina setup also guards the glider provider's client exhaustion path:
disabling running costs does not by itself stop the provider from cancelling
sprint/swimming every depleted tick. The client adapter conditions only those
two cancellation calls on the running-cost setting, active gliding or its exit
tick, preserving one-time pose cleanup after an exhausted landing. Native
glider pose transitions, stamina updates and elytra exhaustion remain intact.
`paragliderExhaustionTest` executes the pinned native update bytecode against a
client-free fixture, including the original failure and forty stable depleted
ticks with the guard.

[Legendary Survival Overhaul](https://www.curseforge.com/minecraft/mc-mods/legendary-survival-overhaul)
supplies thirst and temperature without its health or limb modules. Independently
authored minimal rules-pack resources remove the latter modules' recipes and
loot while retaining relevant temperature rewards. Its archive declares MIT,
the project page says All Rights Reserved and the linked license retains Forge
boilerplate; the artifact record and notices preserve that discrepancy.
[Nutritional Balance](https://www.curseforge.com/minecraft/mc-mods/nutritional-balance)
uses the owned nutrient tags and native food events, with a separate live display. Its native −2 lower bound is extended to −4 only for the malnourished attack-damage setting before configuration validation; the native ADD_VALUE effect remains authoritative.
Current food assignments derive from pinned ingredient tags and recipes rather
than item-name matching. Registered non-edible ingredient anchors remain where
recipe inheritance needs them; absent optional outputs are excluded. The server
regression reports effective foods and drinks and requires complete classification.

Provider binaries remain unchanged. Pins and channel parity live in the existing
artifact inventories; display sources and guarded corrections live in the owned
mod's localization inventory. The active dimension data supplies the aquatic
biome roster for future generation when a world loads; saved FULL chunks keep
their stored biomes and terrain.

### Equipment models and material artwork

The existing owned resource packs add held models for all thirteen
[Tide](https://modrinth.com/mod/tide) rods, the five held grappling hooks and
five deployed heads from [Yori3o’s Grappling Hooks](https://modrinth.com/mod/yori3os-grappling-hooks),
and the [Nutritional Balance](https://www.curseforge.com/minecraft/mc-mods/nutritional-balance)
lunch box. Active grappling handles remain separate model states. This does not
replace the complete catalog of fish, ingredients, bottles, armor icons or eggs.

The rod children reference the eight existing hand/cast parents from the pinned
[Refined Tools](https://modrinth.com/resourcepack/refined-tools/version/ndAFoNmO)
resource pack through [Modefite](https://modrinth.com/mod/qjlkcVfy), retaining
their display transforms while supplying independent geometry. Each rod follows
the palette and identifying motifs of its native inventory icon and has a solid
drum, flanges and mirrored crank. `RodModelContractTest` checks all 104 variants,
local atlas UVs and the actual Punchy tip detector. Cast meshes omit the stowed
tackle. The vanilla rod's four pinned cast parents receive a guarded runtime
removal of those five elements before baking; a full parsed-geometry fingerprint
leaves changed replacement models alone. No provider cuboids or textures are
copied. The original meshes and materials are composed by `EquipmentModels.java`
and `RodModels.java`, with no generative image model. Additional functional tools,
blades, ranged weapons, nets and brushes are composed by `ToolModels.java`; they
reference native icon textures and retain native use-state predicates. Workshop
supplies use `CreateEquipmentModels.java` for physical parts and generated material
fields in their native sprite palettes, without embedding the inventory artwork
on the held surfaces. `AquaticToolModels.java`, `ShellToolMeshes.java` and
`CoralToolMeshes.java` construct hollow shell valves, elongated shell bodies and
item-specific coral working parts on square shafts. `CoralToolMaterials.java`
and `ShellToolMaterials.java` compose their material fields deterministically
from native palette samples. `ConstructionEquipmentModels.java` owns the pouch,
tape measure, frame saw and physical brush/wrench children. It uses native
material fields, including the brush's tintable bristle overlay, and maps neutral
metal fields to the wrench's separate faces. `ConstructionIconModels.java`
retains [CreativeCore](https://modrinth.com/mod/creativecore/version/JS0K93bG)'s native layer model at the
root so its renderer can dispatch before selecting the nested hand model. Its
first layer selects the native sprite outside hand contexts; its second layer
retains the dynamic tile preview. Physical cuboid children use a block parent so
the item sprite generator does not replace their geometry. The scoped palm-frame
bridge also positions the pouch and paint brush in first person. The aquatic
creature net inherits
its provider's geometry and changes only held transforms. Spellbreaker's held
blade uses independently composed material fields sampled from the color palette
of Galosphere 1.5.3's Allurite block; the provider's artwork is not embedded.
The authored source
and independently composed pixel fields are GPL-3.0-or-later; referenced native
artwork and upstream packs retain their own terms. The source is included in the
corresponding-source archive. Run
`./gradlew generateEquipmentModels` from the owned mod project in the full
repository to regenerate the equipment resources; `equipmentModelsCheck` verifies
their exact bytes through `check`.

The additional native lunch-box model keeps the provider's custom renderer and
its food planes. Deployed grappling heads compensate for the native rotation and
retain the rope anchor. Exact-version client adapters select the actual cast rod,
restore the missing native Midas predicate and connect the fishing line to the
rendered three-dimensional tip. Native inventory rod icons remain available.
The grappling chain uses the emitted animated handle pose for its local
first-person endpoint, retaining native third-person/VR/fallback paths. Native
rope defaults are Shift/Ctrl. A version stored through the native Options
serializer migrates only the former unmodified Page Up/Page Down pair once,
after NeoForge's late binding reload; other preferences are retained.

## Language supplements and ore materials

The owned mod's [localization source inventory](../mods/aeroscapes-reimagine/src/main/resources/META-INF/localization-sources.json) records the exact selected upstream dictionaries, licenses, missing-key filters and authored supplements. Selected [BMP Translations](https://github.com/BMProjects-Development/BMP-Translations), [Create: Food](https://modrinth.com/mod/create-food), [Item Descriptions](https://modrinth.com/mod/item-descriptions) and [Jade](https://modrinth.com/mod/jade) translations retain their individual licenses in separate resources. Their notices and full license texts are shipped in the JAR and corresponding source archive. These additions do not replace provider JARs or their existing translations.

## Crafting and inventory planning

[Crafting Tweaks](https://modrinth.com/mod/crafting-tweaks/version/IhkQpEve) and
[Balm](https://modrinth.com/mod/balm/version/XC1JUHmA) use unchanged official files
under the author's [modpack permissions](https://mods.twelveiterations.com/permissions).
The independent `config/craftingtweaks/grids/toms_storage.json` targets the installed
[Tom's Simple Storage](https://modrinth.com/mod/toms-storage) menu package; the
provider's bundled registration still names its older GUI package. The owned
server fixture exercises rotate, spread and clear through native packet handlers
on ordinary crafting, player crafting and the actual terminal menu.

[EMI: Create Schematics](https://modrinth.com/mod/emi-create-schematics/version/opyfkMHl)
is an unchanged MIT client helper. Its import replaces only its own tagged
favorites, preserving ordinary entries and the original clipboard.
[Forgematica](https://modrinth.com/mod/forgematica/version/PFUOWUDF) and
[MaFgLib](https://modrinth.com/mod/mafglib/version/CgDQ0u0Q) remain unchanged LGPL
client artifacts. The former's provider marks its source archive as primary;
the pack deliberately pins the executable secondary file. Paired official source
archives were compared with the executable APIs. Their compatibility stub IDs
retain native configuration namespaces. The starting schematic configuration uses
Right Alt shortcuts, ordinary reach and no Easy Place.

[InvMove](https://modrinth.com/mod/invmove/version/C7ePwvWW) uses its stable native
file and public module API. The owned adapter recognizes recipe-browser focus,
storage text/modal input, terminal popups and held Ponder tutorials. Tutorial
targets come from live mouse coordinates and the native scene registry. Toolkit
configuration screens keep movement disabled. Small version-gated storage
accessors expose existing focus/modal state; the original providers are unchanged.

The owned localization inventory records independent missing text and guarded
Russian display corrections for these exact files. Raw configuration IDs and
changed provider/player translations are preserved. Native numeric placeholder
normalization is applied before matching correction expectations. Finite schematic
messages keep captured filenames and IDs as format arguments. Provider LGPL terms
still apply to original expected text; notices and licenses remain available in
the original archives and the corresponding-source inventory.

## Workstations, loot, locating and backups

The exact beta files of [JEI](https://modrinth.com/mod/jei/version/zHNxmOqp),
[Advanced Loot Info](https://modrinth.com/mod/advanced-loot-info/version/adxS0PUF)
and [Advanced Core Info](https://modrinth.com/mod/advanced-core-info/version/xaWwatoU)
are owner-selected exceptions. The existing browser supplies the interface;
the existing trade viewer retains trade pages while the new loot viewer supplies
block, creature and chest rules. Unsupported custom functions remain visible.

[Easy Anvils](https://modrinth.com/mod/easy-anvils/version/fSQSKhdF) and
[Easy Magic](https://modrinth.com/mod/easy-magic/version/MxbfrOEv) retain MPL-2.0
code and separate reserved asset terms. Display additions are independent.
Enum aliases use declaring-class identity before generic fallback, preserving
different meanings of FLAT. Exact FML 4.0.42 loads their SERVER defaults from
global `config/`; existing world-local overrides take precedence. A narrow comparator
adapter permits configured free standalone renames alongside the existing economy
mod while preserving custom repair/tome results and combined costs. The enchanting
point helper corrects float drift at integer boundaries, retaining fractional
budgets below the next whole point, so rounding does not
reject an otherwise affordable five-point reroll.
[Horseman](https://modrinth.com/mod/horseman/version/jJ93aLVn) retains its mobility,
hitching, recall and inventory mechanics. The owned mining guard restricts its
native bonus to horse-type mounts. Native instrument aliases share the latest
owned horse snapshot across UUID replacement and rebinding, preserving cargo
without resurrection. Duplicate swimming/HUD handlers have one owner.

[Simple Backups](https://modrinth.com/mod/simple-backups/version/6JQL4qSR) remains
unchanged Apache-2.0. The owned transaction saves and captures files synchronously,
then compresses, verifies and atomically publishes before retention. Its soft
budget preserves each world's newest complete copy. Unknown/legacy archives are
preserved; historical managed payloads are CRC-checked before retention admission.
The configured file exclusion omits the rebuildable live SQLite/WAL terrain cache
from world and dimension data folders. This verifies captured files, not every
third-party serializer's success.

[Accurate Block Placement Reborn](https://modrinth.com/mod/accurate-block-placement-reborn/version/rA95AHFG)
uses the existing bridge and unchanged MIT file. Scoped predicates repair current
interaction-signature recognition without copying its placement algorithm.
[Nature's Compass](https://modrinth.com/mod/natures-compass/version/nFniEtJV) and
[Explorer's Compass](https://modrinth.com/mod/explorers-compass/version/hIJ2Ev1Q)
retain CC-BY-NC-SA-4.0. Their Minecraft ranges use the exact loader's 1.21
compatibility mapping. Owned search requests enforce bounds, lifecycle and payment;
sampling does not promise every small biome or a dry destination.

## Tree felling, dropped items and paid travel

[HT's TreeChop](https://www.curseforge.com/minecraft/mc-mods/treechop/files/6254465),
[Panda's Falling Trees](https://www.curseforge.com/minecraft/mc-mods/pandas-falling-trees/files/7858908)
and [PandaLib](https://www.curseforge.com/minecraft/mc-mods/pandalib/files/8787579)
remain unchanged official files. The selected falling-tree beta and its library
are exact exceptions. The owned transaction retains logarithmic work, charges
cumulative nominal axe wear through native enchantment handling, and persists
the pending falling-tree snapshot and drops. Native default double charges and
the animation entity's unsaved state require these scoped adapters. Ships are
excluded from mass felling. PandaLib's available source declares 0.6.0, while
the selected file is 0.6.2; exact release-source identity is unproven.

[ItemPhysic Full](https://modrinth.com/mod/itemphysic/version/rYw9JKpB) needs
[CreativeCore](https://modrinth.com/mod/creativecore/version/JS0K93bG) on both sides.
The library update includes relevant Sable collision fixes. Exact paired source
archives identify configuration and pickup/toss behavior. Native API labels for
ItemPhysic say LGPL-2.1-only, while its actual descriptor says LGPL-3.0-only;
neither original archive is modified. Reference pickup exceptions are mapped to
registered pack tags. Empty tags, absent mods and a malformed block ingredient
that resolved to air are omitted. Owned server guards validate targeted pickup
and toss requests, preserve never-pickable display entities, and retain the
native ownership of cancelled-toss inventory handling.

[Waystones](https://modrinth.com/mod/waystones/version/6Z6MQ6os) retains its
author's [modpack terms](https://mods.twelveiterations.com/permissions), existing
[Balm](https://modrinth.com/mod/balm/version/XC1JUHmA) and ordinary recipes.
[Waystones: Sable](https://modrinth.com/mod/waystones-sable/version/Wot8Pf4C) is the
native ship integration. The owned policy uses per-player UUID activation,
loot-only sigils, physical normal stones and XP-point payment with failure
refunds. An independent loot modifier filters natural stone drops even for
direct loot-table consumers. The sigil has original stone-seal pixel art; its editable SVG is shipped beside
the PNG under the owned resource tree. No game texture is reused. The 25% sigil
roll matches 151 explicit, defined exploration-container root tables from the
pinned structure artifacts, including custom Minecraft-namespace aliases.
Nested tables use raw generation and do not receive another independent roll.
A vault-location exclusion separates two IDs shared by normal chests and vaults.
Village-prefixed tables, pots, dispensers, spawner rewards and undefined provider
IDs are excluded; a known village template reuses `simple_dungeon`, so this is
a table policy rather than a universal structure-location restriction.
The exact point badge replaces the native three-level sprite through the public
client renderer API; hover text derives minimum and post-payment balances from
real points. Disabled epitaph/dormant-shard recipes and native death handling
are suppressed; the common recipe-viewer tag and creative event hide these
items and the already disabled twinbound feather without removing registry IDs. Original
provider files and their notices remain unchanged. Exact pins, source comparison
limits and authored EN/RU supplements remain in the artifact and localization
inventories. Graphical and ship-control acceptance belongs to the owner.

## Distant terrain

[Distant Horizons 3.3.3](https://modrinth.com/mod/distanthorizons/version/9w34y8ai) is an unchanged official native NeoForge/Fabric file, installed on both sides for the logical-server generation and technical headless checks. This does not establish multiplayer support. Its source tag is `a54dd3eb1df0078e63a71266d042c10d9cda4493`; the actual JAR supplies the exact API and configuration evidence. The descriptor has no required third-party dependency and rejects Separate Sable Render Distance through 1.8.6. LGPL-3.0 and bundled library notices remain in the original JAR.

`config/DistantHorizons.toml` uses native schema 5. `CHUNKS_ONLY` avoids the rough heightmap surface generator; `INTERNAL_SERVER` schedules actual native FULL chunks, preserving the owned island generator, C2ME and structure hooks and saving generated terrain. This differs from the default isolated `FEATURES` path. Cave culling is disabled for lower and floating surfaces; medium vertical quality remains lossy. Radius 64, two workers and runtime ratio 0.5 are conservative starting settings, not measured Intel performance. Auto-updates and duplicate distant clouds are disabled. Config defaults belong to fresh imports, not an in-place settings replacement procedure.

The native serializer stores the runtime ratio as a TOML string. The pack keeps that representation. DH's first chunk callback otherwise attempts to bind the installed [Chunky](https://modrinth.com/mod/chunky) API before its `ServerStartingEvent` initialization. A version-gated common adapter leaves DH's listener flag unset until the current server is ready, then permits normal registration and generation coordination. It does not suppress post-start errors. The strict fresh-world test must exercise this boundary; a successful restart alone cannot reproduce the original early spawn callback.

The current JAR omits Russian. The owned supplement covers its 489 English keys plus 25 missing functional labels/tooltips and five literal display aliases. Selected older official Russian translations retain LGPL attribution; the inventory binds both provider files and resources. A version-gated native GUI helper adapter preserves numeric limits and configuration identities. External release-note bodies, diagnostic output and administrative command syntax remain provider technical text. Native SQLite/compression loading, FULL generation and saved-data checks belong to the disposable server test; rendering, vertical silhouettes, shader depth/clouds and moving-ship composition require owner-client acceptance. Static Iris member-reference compatibility and the shader archives' DH programs do not prove those visual contracts.

[Blackstone ore provenance](blackstone_ores.json) identifies the installed texture inputs and material-generation contract. The client composes those textures in memory; the owned JAR contains no copied Minecraft, [Create](https://modrinth.com/mod/create) or [Galosphere](https://modrinth.com/mod/galosphere) PNGs. Adapted non-asset zinc loot data retain the Create MIT notice.

In `worldgen_adoption.json`, `resources` binds incorporated data to its source and result hashes. `authored_resources` separately binds independently authored Minecraft-namespace tag extensions to their result hashes and reasons. Both lists participate in complete resource coverage, duplicate checks and shipped-byte validation; an authored extension does not imply that it came from Aero.

Unchanged files hosted on Modrinth are referenced through their official downloads under its [specific-file modpack policy](https://support.modrinth.com/en/articles/8797527-obtaining-modpack-permissions). Redistribution of a separate unchanged MIT artifact retains its embedded license; Flywheel's packaging is described below.

## Local visual effects

[Ambient Environment](https://modrinth.com/mod/ambient-environment),
[Eating Animations](https://modrinth.com/mod/eating-animations),
[Visuality: Reforged](https://modrinth.com/mod/visuality-forge) and
[Particle Interactions](https://modrinth.com/mod/particle-interactions) use unchanged
official client files. The owner approved the exact Particle Interactions beta.
Eating Animations requires [GroovyModLoader](https://modrinth.com/mod/gml), which
bundles CommonGroovyLibrary and its loader core. The eating and loader providers
label their files for 1.21; their descriptors allow Minecraft 1.21.1. Their actual
client loading and food-model interaction remain to be checked.

[Better Smoke](https://modrinth.com/mod/better-smoke) uses the owner-approved
[Connector 2.0.0-beta.17](https://modrinth.com/mod/connector/version/IITF0PRC)
and the existing client-only Forgified Fabric API. Its descriptor requires Fabric
Loader 0.18.4 or newer; the pinned Connector embeds 0.19.3. This bridge and its
rendering interactions require client acceptance.

[Atmospheric Fauna](https://modrinth.com/mod/atmospheric-fauna) uses the same
bridge plus [Connector Extras](https://modrinth.com/mod/connector-extras) and the
existing [Cloth Config](https://modrinth.com/mod/cloth-config). Its particle birds
can spawn below sea level and in the air. The pack uses the mod's own rendering
and distance behavior. Camera, zoom, shaders and ship behavior await client acceptance.

Visuality's selected ore emitters exclude amethyst so Subtle Effects keeps its
crystal sparkle; its water circles are disabled. Particle Interactions leaves axe
stripping, fire and campfire sparks to Subtle Effects and leaf-break particles to
Falling Leaves. Its extra particle physics are disabled for the existing particle
physics stack. These settings are initial defaults, subject to client acceptance.

## Rain puddles and direct thunder

[Puddles & Floods 1.1.6](https://modrinth.com/mod/puddles-floods/version/ba9E4XOp)
and its required
[Forgified Fabric API](https://modrinth.com/mod/forgified-fabric-api/version/K4hHKnnI)
are unchanged official native NeoForge downloads installed only on the client.
The API build is pinned to 2.3.4: its nested modules require NeoForge 21.1.219,
within the pack's 21.1.230. Later 2.3.5 and 2.3.6 raise that nested minimum to
21.1.248 and 21.1.262 despite retaining the outer archive's older minimum.
Their MIT and Apache-2.0 notices remain inside the original archives. No
[Connector](https://modrinth.com/mod/connector) or modified provider renderer is
supplied. Flooding and raised water are disabled. Drip puddles are disabled to
avoid the provider-documented overlap with
[Subtle Effects](https://modrinth.com/mod/subtle-effects) villager sweat. Cauldron,
flowing-water and melting-snow puddles are disabled so an integrated server does
not write puddle blocks. The shader-water material remains enabled; the
[Complementary](https://modrinth.com/shader/complementary-reimagined) profile
disables water vertex displacement, while
[MakeUp](https://modrinth.com/shader/makeup-ultra-fast-shaders) has no water vertex
displacement in its selected water program.

[ImmersiveThunder 1.3.0](https://www.curseforge.com/minecraft/mc-mods/immersivethunder/files/6711308)
uses its real CurseForge project/file identity in the packwiz metafile. The exact
native file is MIT, copyright 2025 netcatgirl, with no additional libraries. Its
embedded `LICENSE_Immersive Thunder` remains in the unchanged JAR, which packwiz
bundles at `client-overrides/mods/` under its download-host policy. The metadata
and export verifier bind its SHA-512, SHA-1, size and client-only destination.
The officially linked source branch contains Fabric code and is not used to
infer native configuration; the selected JAR has distance-based strike sounds
and no configuration class. Existing [Sounds](https://modrinth.com/mod/sound)
footsteps and interaction audio remain enabled.

## Local resource integration

### Decorative celestial layer

The owned client renderer adds three small planets, six accent stars and a fixed
northern guide star. `CelestialLayout` owns their decorative directions, gradual
world-day drift and visibility. The client-only `CelestialSkyMixin` draws before
the single blend-ending call in the pinned vanilla sky renderer. This keeps the
existing [Iris](https://modrinth.com/mod/iris) sky phase and selects its textured-sky
program naturally, including the Custom Sky branch of
[Complementary](https://modrinth.com/shader/complementary-reimagined) and the
textured path of [MakeUp](https://modrinth.com/shader/makeup-ultra-fast-shaders).
No provider shader files, internal phase setters or additional libraries are used.

The original atlas uses deterministic pixel composition in `CelestialAtlas.java`,
an AI-assisted Java script included in the corresponding-source archive. It uses
no third-party image inputs and shares the owned mod's GPL-3.0-or-later license.
Its provenance also appears in the JAR's `META-INF/NOTICE.txt`.

### Sun and Moon artwork

The atmosphere resourcepack replaces the native Sun and eight-phase Moon textures
with original golden and cool blue pixel artwork. The editable sources are
[`artwork/sun.svg`](../pack/resourcepacks/aeroscapes-reimagine-atmosphere/artwork/sun.svg)
and [`artwork/moon_phases.svg`](../pack/resourcepacks/aeroscapes-reimagine-atmosphere/artwork/moon_phases.svg).
They use AI-assisted deterministic vector composition, without third-party image
inputs or generative image models, under GPL-3.0-or-later. The resourcepack carries
the full license and the corresponding-source archive includes both SVGs and PNGs.

Transparent padding keeps the apparent diameters close to the native textures:
the Sun canvas is 128 × 128, and each Moon cell is 96 × 96. The Moon uses the
Minecraft 1.21.1 four-column, two-row order: full, waning gibbous, third quarter,
waning crescent, new, waxing crescent, first quarter, waxing gibbous. Every phase
masks the same surface map; the new Moon is transparent. Native additive blending
and weather fading remain unchanged. Shader styles that replace the native
textured sun and moon can bypass this artwork.

### Fresh Animations: Quivers

Quivers 2.2.0 is an unchanged official client download, selected directly above
Fresh Animations 1.11.1. FreshLX's published terms explicitly permit modpack use;
the export references the original file without bundling its artwork.

FA 1.11 merged the skeleton-family animation files. Quivers still names the old
stray and bogged files and lacks the new family flags. The owned
`QuiverEmfMixin` composes the original base JEM's parts with the original Quivers
body attachment in memory before EMF prepares the model. This retains the base
flags, transforms and bogged mushroom model/animation. It only applies when the
exact Quivers file directly overrides the exact base FA file at the same native
resource location; a different higher-priority JEM remains untouched. No models,
textures or animation expressions from either provider are redistributed.

### Atmosphere resources

The second atmosphere wave uses unchanged official client downloads: Fusion
1.3.16, Fusion Connected Glass 1.0.1, Falling Leaves 1.21.1-2.6.0,
Fresh Animations: Player Extension 1.1.0, LambDynamicLights 4.8.11+1.21.1 and
Extended Illumina NEA. Their exact identities and terms are in `artifacts.json`.
FreshLX explicitly allows modpack inclusion. Fusion and its glass pack retain
ARR; Lamb retains its embedded Lambda License. Official Modrinth download
references do not grant separate binary rehosting or modified redistribution.

The universal Lamb file's provider dependency list includes Fabric API, but its
native NeoForge descriptor loads a bundled runtime/API, SpruceUI and Yumi
libraries. This native route needs neither a separate Fabric API nor Connector;
the client Forgified Fabric API is selected independently for Puddles & Floods.
The raw provider list remains
in `provider_dependencies`; `dependencies` reflects this pack's native route.
Its bundled native requirements are checked by the artifact audit. Lamb's
native settings remain unchanged; shader handlight coordination and ship-local
light are not established by that audit.

The owned `pack/resourcepacks/aeroscapes-reimagine-atmosphere/` contains an
independent Falling Leaves setting for `minecraft:flowering_azalea_leaves`:
`spawnrate=0.0` leaves ambient petals to Subtle Effects. Leaf bursts from
attacks or decay are separate native behavior. Falling Leaves
already supplies that setting for cherry leaves. Its exact `LeafSettingsEntry`
codec and resource loader accept per-block settings at
`assets/<namespace>/fallingleaves/settings/<block>.json`. The small pack contains
no copied provider models or textures. It and the new third-party resourcepacks
are switchable initial selections for fresh profiles, not enforced resources.

The same pack supplies an independently authored milk-bucket sound profile at
`assets/minecraft/sounds/items/moo.json` for the unchanged
[Sounds 2.4.22+lts file](https://modrinth.com/mod/sound/version/kti7i9SG).
Its native item-profile codec requires `keys` and `soundEvent`; omitted pitch and
volume inherit the action settings. Milk uses `minecraft:item.bucket.fill`, as
does the provider's water-bucket profile, replacing its cow ambient event.
The existing file pack is selected above `mod_resources`, so native resource
priority resolves the whole profile without modifying the provider or mod order.
No provider sound samples, code or other item profiles are included.

### Furniture, building materials and leaves

Handcrafted 4.0.3 and Chipped 4.0.2 are unchanged official NeoForge files on
both sides. They require Resourceful Lib >=3.0.0; the selected native 3.0.12
file bundles YABN and ByteCodecs. Chipped also requires Athena >=4.0.0 on the
client; Athena 4.0.6 is client-only here. Fusion does not replace Athena.
No external dependency beyond these libraries is required by their native
descriptors. The two content mods add saved block IDs: keep the matching pack
and a backup when rolling back worlds that use them.

The content mods' JAR declarations and Modrinth API labels remain in the exact
records. Their version-matched source license is Terrarium v1: code/data MIT,
non-code artwork ARR. This does not license the complete archives as MIT.
Resourceful Lib and Athena use MIT. Resourceful Lib's matching 1.21 source is
commit `95968b13173937e9166b4224ff7528508bdb144c`; the similarly named v3.0.12
tag belongs to its later 1.21.3 lineage and is not this file's source audit.
All new files use official Modrinth download references under the specific-file
policy, with no extracted artwork or modified provider archive.

Motschen's Better Leaves 9.6 replaces Cubic Leaves, selected by the owner for
its appearance. Its original ZIP includes MIT and supported formats 15..255,
including this pack's format 34; Cull Leaves is optional and is not added.
Better covers vanilla, Quark, most Nature's Spirit and Chipped leaf blockstates.
Coconut retains its own Nature's Spirit model; Galosphere opal indirectly
inherits the changed vanilla leaf parent. Cubic is excluded rather than stacked.

Ordinary Handcrafted crafts can compete with Quark vertical planks and
Supplementaries item shelves. Polymorph selects matching ordinary crafting
results, including EMI transfer. Chipped variants use their own conversion menu;
its seven stations and six handheld tools share the catalog lookup. Neither
selector controls Create mechanical crafting or recovers overwritten recipe IDs.
Static recipe scans do not prove all expanded tag/condition matches at runtime.

First-person Model 2.7.3 is excluded after the owner's report of body clipping
and blocked downward flight view. Exact-file inspection found fixed camera/body
offsets and FA Player's animated whole-body transforms, with no verified native
setting that reconciles them. This is a static explanation, not a reproduced
render diagnosis. FA Player, Not Enough Animations and 3D Skin Layers remain.

### Atmosphere additions 0.1.3

The remaining selected content uses unchanged official Modrinth files: Fusion
Connected Blocks 1.1.0, Fusion Block Transitions 1.0.3, Create Style Sophisticated
Backpacks 1.2, Beautiful Enchanted Books MOD EDITION 6.0.0, Better Clouds 1.13.11,
Sound Physics Remastered 1.5.2, Exposure 1.9.19, Immersive Melodies 0.8.0 and
Visual Workbench 21.1.2. Puzzles Lib is updated from 21.1.39 to 21.1.66 for the
workbench's >=21.1.48 requirement; installed Diagonal Fences/Walls/Windows each
declare >=21.1.15 without an upper bound. Exact-file metadata owns every pin.

Exposure, Immersive Melodies, Visual Workbench and Puzzles Lib are installed on
both sides. Better Clouds, Sound Physics, the three resourcepacks and Beautiful
Enchanted Books are client-only. BEB's provider labels both sides required, but
its native entry point is empty and its renderer subscriber/mixin are client
scoped. Preserve the raw provider environment alongside the native-route note.
Sound Physics' optional dedicated-server range-broadcasting role is not required
for the selected singleplayer acoustics.

The owner explicitly approved the exact Sound Physics beta file `85fA7L1W`.
The validator exception binds project, version and release type; other beta files
remain unapproved. The owner-selected **Hear yourself** option is enabled through
the native `simple_voice_chat_hear_self` key. Other Sound Physics settings retain
their native defaults, and existing volume levels are unchanged. The provider
scopes this key to its Simple Voice Chat integration. Listener-relative ambient
beds are not evidence of spatial occlusion;
moving ship geometry is not covered by the static audit. No custom sound/ship
adapter or unsafe level access is enabled.

Better Clouds' native YACL range [3.5.0,4.0.0) includes installed 3.8.2.
Complementary r5.9.3 uses `CLOUD_STYLE_DEFINE=50` and MakeUp 9.6 retains
`V_CLOUDS=0`, both their supported Vanilla cloud routes. Setting shader clouds
to Off would disable the path used here. Minecraft's own Clouds Off preference
is still respected; no installed profile is changed.

Fusion Blocks requires Fusion >=1.2.12 and Transitions >=1.2.11; installed 1.3.16
fits. Their supported resource formats include 34, as does the backpack pack.
No effective asset-path collision was found with the current selected packs.
The backpack pack covers existing models and most upgrade icons; native fluid,
battery, straps and item-display geometry remains and needs visual acceptance.
These original resourcepacks and the BEB project retain ARR artwork terms.
BEB's native descriptor/source declares Apache-2.0 for code; that is not a blanket
artwork grant. The owner confirmed GitHub Releases as an accepted export channel.
Exports reference the original Modrinth downloads and do not rehost these JARs
or extract provider artwork. Retain both source and project-page terms.

Exposure retains MIT, Immersive Melodies GPL-3.0-only, Visual Workbench MPL-2.0,
Better Clouds MPL-2.0 and Sound Physics GPL-3.0-only. Their original archives
retain their notices. New photo/instrument/workbench data makes rollback a
matching-pack-and-save-backup operation, not a resourcepack toggle.

### Rain, wind chimes, camera and sky

Cool Rain Reforged is client-only here: its native client rain-sound injections
replace vanilla surface sounds and use the Weather category. It adds no rain
particles, recipes or network protocol. Version 1.0.2's original JAR contains the
Sable surface adapter; the older published source branch does not establish that
adapter's behavior. Its ARR provider label and MIT native descriptor are both
recorded. Distribution references the unchanged official Modrinth file.

The khazoda/hibi wind chimes project is separate from WindChime Unofficial
Continued. It adds three saved blocks on both sides under Unlicense. Its bundled
Sable companion 1.6.0 is byte-identical to Sable 2.0.6's copy. Recipes use ordinary
vanilla resources; future rollback retains a matching save backup.

Sable 2.0.6 targets Shoulder Surfing's 4.x internal classes, which 5.2.0 removes.
The selected 4.22.12 otherwise recalculates camera rotation even with zero sway,
reapplying Sable's ship orientation. The owned client plugin runs after the native
camera mixin and redirects only its final position-handler rotation through a
zero-sway guard. It preserves the existing quaternion and initial rotation under
the selected defaults, which explicitly disable sway; nonzero sway retains native behavior. Exact versions,
merged-mixin origin, method descriptor and call count fail explicitly on changes.
The offline transformer check does not establish graphical ship acceptance.
Otherworld's numeric framing is retained without its forced FOV or decoupled
movement. The new shoulder-swap binding starts unassigned to avoid Iris's O key.

Polytone supplies two independent atmosphere-resourcepack settings: modest
biome-preserving sky saturation and time-varying sunrise colors, plus 1800
attempted vanilla star points with smaller sizes. No provider artwork is copied.
Cloud, fog, horizon and dimension-effect replacements are not selected. Vanilla
weather/night dimming stays active. Complementary uses its own procedural stars
with nebulae disabled; MakeUp retains the vanilla star geometry. Polytone's actual
Supplementaries Team License 1.5 differs from the provider GPL label; section 5's
unchanged official-download route is used, without direct binary bundling.
The native root-level `polytone_options.json` disables its unrelated entity-light
interpolation and colored-light backend. Its shaderless leash renderer retains
the provider's vanilla-brown strip texture; no separate replacement is added.
The dimension/color keys and expressions were checked against the exact JAR and
the author's [dimension modifier](https://github.com/MehVahdJukaar/polytone/wiki/Dimension-Effects-Modifiers)
and [custom color](https://github.com/MehVahdJukaar/polytone/wiki/Custom-Colors) schemas.

### Hands, tools and precipitation

Punchy 3.0, Refined Tools 3.0, Modefite 1.0.1, Puzzle 2.3.0 and Particle Rain
4.0.2 use unchanged official Modrinth downloads, client-only. Puzzle bundles
MidnightLib 1.9.2; no separate library or cross-loader bridge is added. Punchy and
Refined retain ARR terms; Modefite retains GPL-3.0, Puzzle/MidnightLib and Particle
Rain retain MIT. Punchy's author permits Modrinth/CurseForge modpack inclusion
while prohibiting independent JAR rehosting. Exact provider/embedded declarations
and the native dependency closure remain in `artifacts.json`.

Refined's format-34 overlay needs Modefite's item-definition backport and Puzzle's
rotations. It also replaces two entity cutout fragment shaders. Four independently
written empty model parents in the separate owned Refined compatibility resourcepack resolve absent
Refined parent references without copying artwork or replacing the children's
cuboids/transforms. A generated-item parent would flatten those models. Refined
follows Illumina in the initial selection; its owned compatibility pack stays last.
Disable the Refined and compatibility packs together. Its composite trident branch
lets the vanilla entity renderer supply the held 3D trident instead of applying
Refined's incompatible flat-item guard. No upstream model or artwork is copied.

Punchy supplies its own automatically enabled animation pack. Initial defaults
retain tool actions while disabling camera tracks, extra walking/sprinting/falling
motion, overlays and particles. Inspect bindings start unassigned; cake and the two
birthday candle presentations use ordinary hands. Mob FA, FA Player and NEA remain.
Punchy's built-in EMF bridge references a retired API. The owned client wrapper
mounts EMF's current first-person state around its arm draw and unmounts in
`finally`, so `preventFirstPersonHandAnimating` applies without disabling other
FA rendering. This does not restore first-person torso/feet.

Flerovium 1.1.2 draws ordinary and shader item meshes before substituting an
empty model for the vanilla path where Punchy captures rod geometry. The owned
fast-render entry adapter forwards the original rod mesh and unchanged pose to
Punchy's public capture. It keeps both providers' drawing and line projection.
The third-person layer scopes the actual PlayerModel. During a recognized action
it suppresses only that player's native visual attack setup and the matching
FA root's swing query, preserving locomotion. It bypasses stale LOD/shadow replay
only while computing that neutral FA pose; all gates end in `finally`. The actual
attack clock and gameplay are untouched. The authored arm interpolation uses the
neutral torso frame and then composes the full resulting body transform. A
local matrix decomposition avoids the pinned JOML1.10.5 quaternion ZYX getter's
pitch-sign error, including singular orientations. Composed PlayerModel checks
cover both hands, contact, hip and clothing; exact provider contracts bind the
hooks. Owned fields restore before repeat animation and after all player layers.
Bounded draw samples compare a transformed arm reference with its neutral
baseline; they do not establish visual acceptance.

The administrative command imports Tommobean's complete Basic Aeronautics Airship
through Create's normal print helpers, including honey glue and its native swivel
child. The unchanged downloaded NBT remains separate from the GPL mod source in
`pack/config/aeroscapes_reimagine/test_airship.nbt`; `test_airship.json` pins its
provenance and owner-local reuse scope. No public redistribution grant is claimed.
Native source/network state is reset when printing. Radio identities are isolated
before network join, fuel is supplied through the real inventory, and gas is seeded
only on creation. Native controls, forces and saving continue afterward. Explicit
root/child ownership governs removal. Boarding waits until the client confirms
that every owned Sable sublevel has finished loading; a timeout leaves the player
at the starting position. Existing schema-1 platforms keep only their
old safety hold until explicit removal, preventing an upgrade from dropping them
onto terrain.

Particle Rain keeps its native gusts and strength; the owned adapter corrects
the disabled altitude-adjustment multiplier from zero to one. The initial profile
disables altitude scaling so lower islands retain wind. Its height scan uses each
dimension's actual bounds. Only CUSTOM/SKY rain, heavy rain, snow and heavy snow
opt out of Sable's extra movement solver; initial projection and PR's own clip
remain. Default precipitation does not use ship-local hit coordinates or normals
for placement. Other particles retain Sable behavior. Cool Rain keeps material
rain audio; PR's rain volume must remain nonzero to reach that path. Default splash
spawning stays enabled for Subtle Effects, while PR ripple/splatter are disabled.

Exact native versions gate these client mixins; offline bytecode checks validate
the provider boundary, not rendering or ship behavior. Shader, hand and moving-ship
acceptance remains an owner-client check. No new saved blocks/items are introduced.

### Generated Dramatic Doors recipes

Dramatic Doors 3.3.2 / `X5oMCwaA` generates its Chipped conversions in code.
Twelve inputs are absent from Chipped 4.0.2 / `eqVowbGc`; there are no verified
renamed equivalents. Its recipe-manager injection overwrites same-ID resource
overrides, so replacing JSON files cannot repair this boundary.

The owned `DramaticDoorsChippedRecipesMixin` adds NeoForge's `item_exists`
condition to each generated Chipped tall-door recipe before it enters the native
list. NeoForge evaluates it during recipe loading, after registries are ready.
This skips the twelve invalid conversions and retains the other 165, without
removing the provider's output IDs or artwork. A registry check while recipes
are generated would run too early in Dramatic Doors' registration event.
`compat_fixes.json` binds the adapter to both exact providers; no provider
recipe definition is copied into the pack.

### Existing world resources

The complete modified NBT and JSON payloads listed below have been removed from editable pack inputs. The owned mod reads unchanged installed originals and applies independent, minimal field operations in memory. It does not write generated provider data into the distributed pack. Hash-bound recipes guard original JSON identity before applying edits; native resource/registry loading consumes the results. Minimal empty pools, project predicates/tags and grounded-feature references remain project integrations rather than copied full definitions.

| Provider                                | Current local processing, including previously inherited structure wrappers                                                                                                                                                                                   |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| CTOV 3.6.3 / `ztzRUnQ7`                 | 28 NBT: 17 rice-ID farms and 11 Allay-listener cages. 93 JSON recipes: 78 structure definitions and 15 pools, including the tower alias. Four independently defined optional empty pools remain.                                                              |
| Dungeons and Taverns 4.4.4 / `BYUUUeZA` | 830 templates use the standard Minecraft 1.21.1/NeoForge 21.1.230 STRUCTURE DFU in `StructureTemplateManager.readStructure`; no project DFU cache or preconverted copies. 133 JSON recipes: 80 structures, 51 pools and two Minecraft-namespace advancements. |
| Philips Ruins 1.21.1-2.0 / `EQHGxQAv`   | Eight NBT material repairs; 23 JSON recipes: 17 structures, five pools, one biome tag.                                                                                                                                                                        |
| Galosphere 1.5.3 / `Y3deS8Po`           | Two NBT repairs: five missing inventory entries and obsolete silver block; four JSON recipes: two structures, one pool, one ore placement.                                                                                                                    |
| Minecraft 1.21.1                        | 57 ancient-city templates gain missing AIR above their bottom layer during local resource load. The unused old `data/minecraft/structures/end_city/ship.nbt` was also removed: 1.21.1 resolves the singular `structure/` path.                                |

There are 253 effective JSON recipes replacing 254 physical copies (one resource had two pack owners). Namespace does not identify an author: some Dungeons resources live under `minecraft:`. Parity fixtures in the owned mod contain only IDs and hashes. They compare complete typed repaired NBT and native loaded serialization with the retired baseline, and effective JSON with previous definitions. DFU cost is measured separately from world generation; preparation moves into first template loads and is not a claim about FPS or full startup time. See [diagnostic fixture practices](../CONTRIBUTING.md#diagnostic-fixture-practices) for opt-in checks.

## Aeroscapes and Aero Islands

The reference is [Isabel's Aeroscapes 1.0.8](https://modrinth.com/modpack/aeroscapes/version/q4P5coPI), author Isabel / nuclearobelisk, project `x308hQIU`, version `q4P5coPI`. Its internal manifest says 1.0.7; this is upstream identity. [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) applies to that author's contribution, not all dependency resources. Selected data are reorganized, descriptions/predicates updated and the obsolete full noise override omitted. Derivative datapacks retain notices. Otherworld D&D remains an inspected gameplay reference without copied code/assets; no reference logos, menus, profile state, music or textures are transferred.

The exact Aero Islands 1.1.1 JAR (`tGVwBATM` / `bzJ2edxy`) declares GNU GPL v3.0+. Direct archive comparison confirms **160 byte-identical and 37 changed Aero JSON**, plus one separately identified Minecraft resource, in the owned mod. The external Aero JAR/custom content is excluded. Retain GPL-3.0-or-later for the complete owned mod in 0.1.0 and preserve data origins. A differing CC0 public source branch cannot silently replace the selected artifact's identity or license.

The owner reports rewriting Java with Aero Islands as inspiration. Commit `977b4d6b9f9597da90d0dd6a2212427f58b3f3b0` introduced the seven compared files and historical adaptation labels together. Eight original class hashes map to those seven files; bytecode confirms the unusual pillar taper, selected parameter-draw/material-pass ordering and mineshaft initial assembly. These are substantive behavior correspondences, not proof of the writing process or a legal classification of all Java. The present layout/terrain/biome-source implementation was added later (`e75e0ec9f8fafc40e49dd9b59a373108ca0d4dac`) and is not in that comparison inventory. Blanket claims that the entire Java generator is a port, or that every subsequent RNG draw is unchanged, were too broad; grounded basalt checks intentionally alter later draws. Java inventory `mode=reference` keeps verified historical hashes without asserting copied expression. This does not remove the incorporated data or the selected GPL obligations.

The modified `aeroscapes:fluid_level_floodedness` JSON retains the original within a natural-density condition and returns −1 outside; final terrain density is not overridden. The separate Aeroscapes-derived datapack retains its recorded CC BY-SA attribution. Resource addresses required by consumers do not transfer ownership.

## Notices and corresponding source

The owned JAR includes the complete GPL text and `META-INF/NOTICE.txt`, with origins, changes and source instructions. `pack/licenses/aeroscapes-reimagine-source.zip` accompanies the binary as a pack override. It contains the whole editable mod project, main/regression/policy sources/resources, Gradle scripts/wrapper/verification, PMD config, licenses, build README and provenance inventories. `SOURCE-MANIFEST.json` hashes every included input and binds the exact binary SHA-256. Recipients need no private repository, promised future URL or limited seven-file excerpt. `make companion` regenerates it; `make source-archive` refreshes it explicitly; `make check` rejects stale/missing source. Independent build instructions require JDK 25 and JDK 21 and the pinned wrapper; no ignored research/cache directory is required. This implements the source-access route of [GPLv3 §§1, 5–6](https://www.gnu.org/licenses/gpl-3.0.en.html), without relicensing unrelated provider content.

## Terralith data

Terralith 2.5.8 / `MuJMtPGQ` retains the bundled [Stardust Labs license](https://github.com/Stardust-Labs-MC/license/blob/main/license.txt). The active integration includes 30 Terralith-namespace JSON resources: 25 structure definitions, three cave-biome tags and two village pools. The climate delegate in `minecraft/dimension/overworld.json`, the biome-gated surface restoration and the amethyst placement remain separate version-bound resources. Namespace does not identify the source author.

The selected climate table has 1,653 rows; 1,573 are identical to the pinned JAR's 1,713 native rows, 140 native rows are omitted and 80 selected rows are changed/added. The surface restoration wraps the complete native `surface_rule` in a biome condition. Keep these adaptation boundaries in the artifact review record and re-evaluate them on provider updates. General structure/loot/compatibility provisions must be read with their exact exclusions rather than treated as universal modification terms.

## Nature's Spirit data

The 18 adapted `data/natures_spirit/` JSON files from 2.2.5 / `MGjl80vc` use the MIT part of the [split license](https://github.com/Team-Hibiscus/NaturesSpiritFabric/blob/master/LICENSE): nine ore placements, four village definitions and five pools. Preserve the complete Team Hibiscus copyright, permission and warranty notice at `pack/licenses/Natures-Spirit-data-MIT.txt`. The unchanged original JAR's descriptor says ARR; assets and model packages retain their separate terms. Do not relabel the complete JAR or future releases MIT. The chert interval orders authored endpoints; its intended interval is inferred rather than an upstream design claim.

## TerraBlender fix provenance

The owned stateless End-area correction adapts the approach of Partonetrain's CC0-1.0 [TerrablEndFix](https://github.com/Partonetrain/terrablendfix/blob/8c29736c54a677742d4bb309713b82f229642be6/neoforge/src/main/java/info/partonetrain/terrablendfix/mixin/LayeredNoiseUtilMixin.java). It substitutes only the erroneous island-biome input and has no process-global first-call flag. The published TerrablEndFix JAR is not installed; inspection found that its stateful form fixed only the first initialization. The upstream defect is also described in [TerraBlender issue 179](https://github.com/Glitchfiend/TerraBlender/issues/179).

Compile-only API `com.github.glitchfiend:TerraBlender-neoforge:1.21.1-4.1.0.8` comes from official Forge Maven without transitives or bundling. Gradle strict verification pins its JAR/POM. Runtime TerraBlender remains the unchanged Modrinth file `6e8GCrLb`, with original LGPLv3. Maven/runtime ZIP entries match except `META-INF/MANIFEST.MF`, so archive hashes are not interchangeable. The [owned TerraBlender adapter](../mods/aeroscapes-reimagine/src/main/java/dev/aeroscapes/mixin/TerraBlenderEndIslandMixin.java) records the exact-version/config dependence.

## QoL additions

The remaining first QoL group uses unchanged official files for [Better Advancements](https://modrinth.com/mod/better-advancements), [Advancement Plaques](https://modrinth.com/mod/advancement-plaques), [Pick Up Notifier](https://modrinth.com/mod/pick-up-notifier), [Boat Item View](https://modrinth.com/mod/boat-item-view), [Trade Cycling](https://modrinth.com/mod/trade-cycling), [Dismount Entity](https://modrinth.com/mod/dismount-entity), [Clumps](https://modrinth.com/mod/clumps) and [EMI Trades Reborn](https://modrinth.com/mod/emi-trades-reborn). [Collective](https://modrinth.com/mod/collective) is the only additional library; existing libraries satisfy the other native requirements. Artifact records retain exact source revisions, hashes, ranges and comparison limits.

The selected [Boat Item View file](https://modrinth.com/mod/boat-item-view/version/6EYLZUOH) was compiled for Minecraft 1.21.1, but its descriptor erroneously excludes that version. The exact FML 4.0.42 `dependencyOverrides` mechanism removes only this mod's Minecraft dependency clause. The pack remains pinned to Minecraft 1.21.1 and that exact JAR; other dependency checks remain active. Reassess this override with either update. The native default displays filled maps; it does not enable every item.

[Pick Up Notifier](https://modrinth.com/mod/pick-up-notifier) is installed on both sides for actual partial-stack and backpack pickup packets. [Dismount Entity](https://modrinth.com/mod/dismount-entity), [Collective](https://modrinth.com/mod/collective) and [Clumps](https://modrinth.com/mod/clumps) are also installed on both sides for the integrated server. Clumps handles server-side orb behavior, but singleplayer needs its common entrypoint in the physical client installation. The remaining additions are client-only. Automatic mutable translation downloads in Collective are disabled in the starting config; shipped language supplements and display adapters preserve configuration identities and player values.

[EMI Trades Reborn](https://modrinth.com/mod/emi-trades-reborn/version/VPRhN8yf) is published as a release with MIT metadata, while its unchanged descriptor retains `2.0.0+alpha.1` and ARR. Its continuation build revision is unavailable. The inspected author-linked [upstream reference](https://github.com/Prismwork/EMITrades/tree/88c57761ffadda7be1c02d18c48d7719a9be2b43) and original MIT notice are retained separately; neither overrides the actual descriptor. Owned adapters repair wrapped-output item identity and shared fisherman payment lists, and translate display statistics. The viewer samples some custom offers and does not synchronize remote-server trade factories or cover every special merchant. Compile-only APIs are not bundled in the owned mod.

## Artifact metadata discrepancies

Treat the selected file's embedded terms and metadata as evidence alongside API labels; record discrepancies rather than silently normalizing them. Current notable cases include:

- Throwable Rope Connector 0.4.3: Modrinth API says MIT; selected JAR says All Rights Reserved.
- Iris/Flywheel Compat 2.4.0: API says CC0; selected file contains MIT.
- Moonlight 3.0.18: project API says LGPL with an extra condition; selected `LICENSE_Moonlight Lib.md` and manifest say Supplementaries Team License v1.5.

License files remain inside unchanged downloaded artifacts. Create retains MIT, Create Food GPL-3.0, Fungi Delight MIT and Delightful Creators CC0 in the recorded audit; these examples do not license the entire combined pack. Provider API dependency records can also differ from actual JAR declarations: preserve both and explain actual compatibility, as done for Rope Connector/Aeronautics and Supplementaries/Moonlight. Re-evaluate terms and actual dependency contracts when changing those selected files.

## Create: Diesel Generators

The owned oil marker records its native host stone and composes the installed host artwork with an independently authored oil-stain mask during atlas reload. Provider background pixels are not redistributed. The marker has no item or loot table; native relocation tags keep it fixed while allowing ordinary mining.

[Create: Diesel Generators 1.21.1-1.3.15](https://modrinth.com/mod/create-diesel-generators/version/UoPH8lO1) (`ZM3tt6p1` / `UoPH8lO1`) is the unchanged official both-side NeoForge file. Its locally recomputed SHA-512, SHA-1 and size match the provider record. The pinned packwiz fingerprint lookup matches CurseForge project `869316`, file `8542167`, with the same official SHA-1; that channel file was not separately downloaded. The JAR requires NeoForge `[21.1.174,)`, Minecraft `[1.21.1]` and Create `[6.0.7,6.1.0)`. Strut Your Stuff `[1.3.0,)` is optional and absent; its andesite girder strut registration is conditional. Provider metadata and the JAR descriptor declare MIT. The JAR omits a full license file, so the exact George VI MIT notice from the [author-linked source](https://github.com/george8188625/Create-Diesel-Generators/blob/79ce136730871b6f3f7b55d3cc748ff2bec2ad12/LICENSE) accompanies it at `pack/licenses/Create-Diesel-Generators-MIT.txt`.

The inspected [source revision](https://github.com/george8188625/Create-Diesel-Generators/tree/79ce136730871b6f3f7b55d3cc748ff2bec2ad12) is a reference rather than a claimed reproducible build: 925 source resource files match the JAR byte-for-byte, including English and Russian; Ukrainian and both Chinese files differ. Two source paths are absent: the Railways `sephamore_poles` tag and the correctly spelled mechanical-crafting chemical sprayer path. The latter recipe is present in the JAR under the misspelled native ID `mechanical_crafting/chemcial_sprayer`; its real grid remains obtainable and does not need a duplicate recipe. The distributed `CDGConfig` bytecode also omits the source's oil-barrel-length setting; its other configuration labels/comments match. Runtime contracts and recipes are checked against the JAR rather than inferred from that branch.

Owned EN/RU language supplements cover the actual client, common and server configuration labels/comments, all seven missing Russian provider entries, and dynamic distillation-height output. Expected-value corrections repair the translated creative-tab brand, fluid units, malformed concrete names and entity-filter descriptions while preserving changed resource-pack translations. The native `Alt` label is a key name. Configuration metadata, values and ranges remain native; the existing NeoForge/Catnip display adapters translate labels/comments. Runtime adapters translate the literal entity-filter sample name and the display source, which reads the authoritative shared reserve. Permission-gated oil-debug/admin commands are diagnostic output rather than ordinary gameplay interfaces. Sprayers retain native volumetric models and animated cogs; the canister retains its block volume. The source/JAR inventory also identifies custom-rendered flat hammer/cutter/lighter states, oil-scanner states and empty/filled track-layer bags for the separate held-item work.

## Explicit Flywheel provider

`pack/mods/flywheel.pw.toml` pins official Maven Flywheel 1.0.6, client-only. Its bytes match Create 6.0.10's nested copy and its embedded `META-INF/LICENSE.md` retains MIT and Jozufozu's copyright. Vanillin 1.1.3 contains 1.0.4 under another Maven coordinate; an explicit provider prevents NeoForge's JarJar selection from satisfying Sable/Simulated with that older mod. Create retains its nested server library.

The Maven coordinate, URL and hashes remain provenance. `metadata/artifacts.json` selects `distribution=bundle`. The pinned packwiz Modrinth exporter uses its normal domain restrictions to place this exact file at `client-overrides/mods/flywheel-neoforge-1.21.1-1.0.6.jar`, outside the remote download manifest. Python verification checks the reviewed export mode, exact side-specific archive path, size, SHA-512 and SHA-1; a common/server copy or a remote-manifest entry is rejected. Reconsider the explicit provider only when the replacement combination selects the required version without it.
