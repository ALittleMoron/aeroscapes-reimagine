import java.io.File

plugins {
  java
  pmd
  id("net.neoforged.moddev") version "2.0.148"
  id("com.diffplug.spotless") version "8.10.4"
}

group = "dev.aeroscapes"

version = "0.1.0"

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

neoForge {
  version = "21.1.244"
  mods { create("aeroscapes_reimagine") { sourceSet(sourceSets.main.get()) } }
}

repositories {
  mavenCentral()
  exclusiveContent {
    forRepository { maven("https://cursemaven.com") }
    filter { includeGroup("curse.maven") }
  }
  exclusiveContent {
    forRepository { maven("https://maven.minecraftforge.net/") }
    filter { includeModule("com.github.glitchfiend", "TerraBlender-neoforge") }
  }
  exclusiveContent {
    forRepository { maven("https://api.modrinth.com/maven") }
    filter {
      includeModule("maven.modrinth", "c2me-neoforge")
      includeModule("maven.modrinth", "sable")
      includeModule("maven.modrinth", "particle-rain")
      includeModule("maven.modrinth", "punchy-fpa")
      includeModule("maven.modrinth", "playeranimator")
      includeModule("maven.modrinth", "irons-spells-n-spellbooks")
      includeModule("maven.modrinth", "entity-model-features")
      includeModule("maven.modrinth", "entitytexturefeatures")
      includeModule("maven.modrinth", "puzzle")
      includeModule("maven.modrinth", "puzzles-lib")
      includeModule("maven.modrinth", "flerovium")
      includeModule("maven.modrinth", "iceberg")
      includeModule("maven.modrinth", "emi")
      includeModule("maven.modrinth", "emi-ores")
      includeModule("maven.modrinth", "smart-tooltip-scroll")
      includeModule("maven.modrinth", "immersive-weather-renewed")
      includeModule("maven.modrinth", "moonlight")
      includeModule("maven.modrinth", "emi-trades-reborn")
      includeModule("maven.modrinth", "create-aeronautics")
      includeModule("maven.modrinth", "create")
      includeModule("maven.modrinth", "distanthorizons")
      includeModule("maven.modrinth", "better-clouds")
      includeModule("maven.modrinth", "invmove")
      includeModule("maven.modrinth", "mafglib")
      includeModule("maven.modrinth", "cloth-config")
      includeModule("maven.modrinth", "sophisticated-core")
      includeModule("maven.modrinth", "toms-storage")
      includeModule("maven.modrinth", "easy-anvils")
      includeModule("maven.modrinth", "easy-magic")
      includeModule("maven.modrinth", "horseman")
      includeModule("maven.modrinth", "simple-backups")
      includeModule("maven.modrinth", "natures-compass")
      includeModule("maven.modrinth", "explorers-compass")
      includeModule("maven.modrinth", "accurate-block-placement-reborn")
      includeModule("maven.modrinth", "creativecore")
      includeModule("maven.modrinth", "littletiles")
      includeModule("maven.modrinth", "itemphysic")
      includeModule("maven.modrinth", "waystones")
      includeModule("maven.modrinth", "waystones-sable")
      includeModule("maven.modrinth", "balm")
      includeModule("maven.modrinth", "hybrid-aquatic")
      includeModule("maven.modrinth", "hybrid-api")
      includeModule("maven.modrinth", "geckolib")
      includeModule("maven.modrinth", "tide")
      includeModule("maven.modrinth", "yori3os-grappling-hooks")
      includeModule("maven.modrinth", "heros-tiered")
      includeModule("maven.modrinth", "spawn-mod")
      includeModule("maven.modrinth", "teallib")
      includeModule("maven.modrinth", "farmers-delight")
      includeModule("maven.modrinth", "biolith")
      includeModule("maven.modrinth", "wetland-whimsy")
      includeModule("maven.modrinth", "naturalist")
      includeModule("maven.modrinth", "critters-and-companions")
      includeModule("maven.modrinth", "better-archeology")
      includeModule("maven.modrinth", "architectury-api")
      includeModule("maven.modrinth", "quark")
      includeModule("maven.modrinth", "supplementaries")
      includeModule("maven.modrinth", "handcrafted")
      includeModule("maven.modrinth", "chipped")
      includeModule("maven.modrinth", "galosphere")
      includeModule("maven.modrinth", "sophisticated-storage")
      includeModule("maven.modrinth", "create-aeronautics-throwable-rope-connector")
      includeModule("maven.modrinth", "exposure")
    }
  }
}

val c2meApi by configurations.creating {
  isCanBeConsumed = false
  isTransitive = false
}
val c2meApiDirectory = layout.buildDirectory.dir("c2me-api")
val c2meApiEntries =
  listOf(
    "META-INF/jars/c2me-neoforge-base-*.jar",
    "META-INF/jars/c2me-neoforge-opts-dfc-*.jar",
  )
val extractC2meApi by
  tasks.registering(Sync::class) {
    from(c2meApi.map { zipTree(it) })
    inputs.property("nestedApiEntries", c2meApiEntries)
    include(c2meApiEntries)
    eachFile { path = name }
    includeEmptyDirs = false
    into(c2meApiDirectory)
  }

val sableApi by configurations.creating {
  isCanBeConsumed = false
  isTransitive = false
}
val shipApi by configurations.creating {
  isCanBeConsumed = false
  isTransitive = false
}
val shipNestedApiDirectory = layout.buildDirectory.dir("ship-api")
val extractShipApi by
  tasks.registering(Sync::class) {
    from(shipApi.map { zipTree(it) })
    include(
      "META-INF/jarjar/dev.eriksonn.aeronautics.aeronautics-neoforge-1.21.1-1.3.2.jar",
      "META-INF/jarjar/dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar",
      "META-INF/jarjar/Registrate-MC1.21-1.3.0+67.jar",
      "META-INF/jarjar/ponder-neoforge-1.0.82+mc1.21.1.jar",
    )
    eachFile { path = name }
    includeEmptyDirs = false
    into(shipNestedApiDirectory)
  }
val atmosphereApi by configurations.creating {
  isCanBeConsumed = false
  isTransitive = false
}
val explorationApi by configurations.creating {
  isCanBeConsumed = false
  isTransitive = false
}
val equipmentAssets by configurations.creating {
  isCanBeConsumed = false
  isTransitive = false
}
val sableNestedApiDirectory = layout.buildDirectory.dir("sable-api")
val extractSableApi by
  tasks.registering(Sync::class) {
    from(sableApi.map { zipTree(it) })
    include(
      "META-INF/jarjar/veil-neoforge-1.21.1-4.3.2.jar",
      "META-INF/jarjar/sable-companion-common-1.21.1-1.6.0.jar",
      "META-INF/jarjar/dev.ryanhcode.sable.sable-sable_rapier-1.21.1-2.0.6.jar",
    )
    eachFile { path = name }
    includeEmptyDirs = false
    into(sableNestedApiDirectory)
  }

dependencies {
  equipmentAssets("maven.modrinth:quark:qcDWHGnS")
  equipmentAssets("maven.modrinth:supplementaries:Agn1kmUY")
  equipmentAssets("maven.modrinth:handcrafted:JfqnpP2Z")
  equipmentAssets("maven.modrinth:chipped:eqVowbGc")
  equipmentAssets("maven.modrinth:galosphere:Y3deS8Po")
  equipmentAssets("maven.modrinth:sophisticated-storage:wVZfDecJ")
  equipmentAssets("maven.modrinth:create-aeronautics-throwable-rope-connector:EDzeTV17")
  equipmentAssets("maven.modrinth:exposure:iBvaR6AK")
  equipmentAssets("maven.modrinth:littletiles:c2SowFEz")
  explorationApi("maven.modrinth:hybrid-aquatic:jdOU7Pzu")
  explorationApi("maven.modrinth:hybrid-api:4HsWNhJz")
  explorationApi("maven.modrinth:geckolib:Grwn5rUB")
  explorationApi("maven.modrinth:tide:1ki6UyqV")
  explorationApi("maven.modrinth:yori3os-grappling-hooks:lf4DqQBf")
  explorationApi("maven.modrinth:heros-tiered:GY1XViBt")
  explorationApi("maven.modrinth:spawn-mod:OgmUYgeL")
  explorationApi("maven.modrinth:biolith:EAjbdreT")
  explorationApi("maven.modrinth:wetland-whimsy:rSs37OB4")
  explorationApi("maven.modrinth:naturalist:9Yn8ig4c")
  explorationApi("maven.modrinth:critters-and-companions:aeDUpTJS")
  explorationApi("maven.modrinth:better-archeology:zTMR6t3J")
  explorationApi("maven.modrinth:architectury-api:1IiqEQGl")
  explorationApi("maven.modrinth:teallib:lZTBRemW")
  explorationApi("maven.modrinth:farmers-delight:rOESN0jP")
  explorationApi("curse.maven:paragliders-289240:7795089")
  explorationApi("curse.maven:legendary-survival-overhaul-840254:8597620")
  explorationApi("curse.maven:nutritional-balance-448222:7980604")
  compileOnly(files(explorationApi))
  compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.3.0") { isTransitive = false }
  // Compile-only API: classes match the pinned Modrinth runtime; only MANIFEST.MF differs.
  compileOnly("com.github.glitchfiend:TerraBlender-neoforge:1.21.1-4.1.0.8") {
    isTransitive = false
  }
  shipApi("maven.modrinth:create-aeronautics:44pLdPGg")
  shipApi("maven.modrinth:create:UjX6dr61")
  compileOnly(files(shipApi))
  compileOnly(files(fileTree(shipNestedApiDirectory) { include("*.jar") }).builtBy(extractShipApi))
  sableApi("maven.modrinth:sable:fg9dTRz9")
  compileOnly(files(sableApi))
  atmosphereApi("maven.modrinth:particle-rain:RxfaR4ud")
  atmosphereApi("maven.modrinth:punchy-fpa:83nCNSX4")
  atmosphereApi("maven.modrinth:playeranimator:HJZB6bmA")
  atmosphereApi("maven.modrinth:irons-spells-n-spellbooks:slKLosTb")
  atmosphereApi("maven.modrinth:entity-model-features:197m3VoU")
  atmosphereApi("maven.modrinth:entitytexturefeatures:lwNx1nD1")
  atmosphereApi("maven.modrinth:puzzle:bZNDTLHq")
  atmosphereApi("maven.modrinth:puzzles-lib:kmN5PWk9")
  atmosphereApi("maven.modrinth:flerovium:oU1q3eIj")
  atmosphereApi("maven.modrinth:iceberg:IMssx9du")
  atmosphereApi("maven.modrinth:emi:5sIPA1To")
  atmosphereApi("maven.modrinth:emi-ores:V2MXZKsX")
  atmosphereApi("maven.modrinth:smart-tooltip-scroll:5yxY9Gl4")
  atmosphereApi("maven.modrinth:immersive-weather-renewed:n3NgrJOg")
  atmosphereApi("maven.modrinth:moonlight:CO2fjXOk")
  atmosphereApi("maven.modrinth:emi-trades-reborn:VPRhN8yf")
  atmosphereApi("maven.modrinth:distanthorizons:9w34y8ai")
  atmosphereApi("maven.modrinth:better-clouds:UvRCtZxT")
  atmosphereApi("maven.modrinth:invmove:C7ePwvWW")
  atmosphereApi("maven.modrinth:mafglib:CgDQ0u0Q")
  atmosphereApi("maven.modrinth:cloth-config:izKINKFg")
  atmosphereApi("maven.modrinth:sophisticated-core:gtPF4VTh")
  atmosphereApi("maven.modrinth:toms-storage:WQAMoIMC")
  atmosphereApi("maven.modrinth:easy-anvils:fSQSKhdF")
  atmosphereApi("maven.modrinth:easy-magic:MxbfrOEv")
  atmosphereApi("maven.modrinth:horseman:jJ93aLVn")
  atmosphereApi("maven.modrinth:simple-backups:6JQL4qSR")
  atmosphereApi("maven.modrinth:natures-compass:nFniEtJV")
  atmosphereApi("maven.modrinth:explorers-compass:hIJ2Ev1Q")
  atmosphereApi("maven.modrinth:accurate-block-placement-reborn:rA95AHFG")
  atmosphereApi("maven.modrinth:creativecore:JS0K93bG")
  atmosphereApi("maven.modrinth:littletiles:c2SowFEz")
  atmosphereApi("maven.modrinth:itemphysic:rYw9JKpB")
  atmosphereApi("maven.modrinth:waystones:6Z6MQ6os")
  atmosphereApi("maven.modrinth:waystones-sable:Wot8Pf4C")
  atmosphereApi("maven.modrinth:balm:XC1JUHmA")
  atmosphereApi("curse.maven:treechop-421377:6254465")
  atmosphereApi("curse.maven:pandas-falling-trees-880630:7858908")
  atmosphereApi("curse.maven:pandalib-975460:8787579")
  compileOnly(files(atmosphereApi))
  compileOnly(
    files(fileTree(sableNestedApiDirectory) { include("*.jar") }).builtBy(extractSableApi)
  )
  c2meApi("maven.modrinth:c2me-neoforge:avwWYPbG")
  compileOnly(files(fileTree(c2meApiDirectory) { include("*.jar") }).builtBy(extractC2meApi))
}

val policy by sourceSets.creating {
  compileClasspath =
    sourceSets.main.get().output +
      sourceSets.main.get().compileClasspath +
      configurations[compileClasspathConfigurationName]
  runtimeClasspath = output + compileClasspath
}

dependencies {
  add(policy.implementationConfigurationName, "org.ow2.asm:asm-tree:9.8")
  add(policy.implementationConfigurationName, "org.ow2.asm:asm-analysis:9.8")
}

val regression by sourceSets.creating {
  compileClasspath = sourceSets.main.get().compileClasspath + sourceSets.main.get().output
  runtimeClasspath = output + compileClasspath
}

spotless {
  java {
    target("src/**/*.java")
    googleJavaFormat("1.36.1")
  }
  kotlinGradle { ktfmt("0.64").googleStyle() }
}

pmd {
  toolVersion = "7.28.0"
  isConsoleOutput = true
  ruleSets = emptyList()
  ruleSetFiles = files("config/pmd.xml")
}

tasks.withType<JavaCompile>().configureEach {
  options.release = 21
  options.encoding = "UTF-8"
  options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Jar>().configureEach {
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
}

tasks.withType<org.gradle.api.plugins.quality.Pmd>().configureEach {
  classpath =
    (classpath ?: files()) +
      files(
        javaToolchains
          .launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
          }
          .map { it.metadata.installationPath.file("lib/jrt-fs.jar") }
      )
}

val policyTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks island footprint policy without starting Minecraft."
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.worldgen.IslandFootprintTest"
  }

val worldgenRegressionJar by
  tasks.registering(Jar::class) {
    group = "verification"
    description = "Builds the server-only worldgen regression fixture; never shipped in the pack."
    dependsOn(tasks.named(regression.classesTaskName))
    from(regression.output)
    archiveBaseName = "aeroscapes-worldgen-regression"
  }

val islandLayoutTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.worldgen.IslandLayoutTest"
  }

val exposedIslandSurfacesTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.worldgen.ExposedIslandSurfacesTest"
  }

val ecologyContractTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.ecology.EcologyContractTest"
  }

val spawnTintTransformerTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.ecology.SpawnTintTransformerTest"
  }

val templateStructureSupportTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.worldgen.TemplateStructureSupportTest"
  }

val biomeTerrainMaskTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.worldgen.BiomeTerrainMaskTest"
  }

val sableTraceBufferTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.diagnostics.SableTraceBufferTest"
  }

val monitorControllerTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.monitor.MonitorControllerTest"
  }

val frameIntervalStatsTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.diagnostics.FrameIntervalStatsTest"
  }

val awaitTrackerTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.diagnostics.AwaitTrackerTest"
  }

val shoulderCameraTransformerTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.camera.ShoulderCameraTransformerTest"
  }

val atmosphereContractTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.atmosphere.AtmosphereContractTest"
    doFirst {
      systemProperty(
        "aeroscapes.atmosphere.apis",
        (atmosphereApi.files +
            sableApi.files +
            fileTree(shipNestedApiDirectory) { include("*.jar") }.files)
          .joinToString(File.pathSeparator),
      )
    }
  }

val punchyMapProfileTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.atmosphere.PunchyMapProfileTest"
    systemProperty(
      "aeroscapes.map.profile",
      layout.projectDirectory
        .file(
          "../../pack/resourcepacks/aeroscapes-reimagine-atmosphere/assets/minecraft/punchy/compat/aeroscapes_map.json"
        )
        .asFile
        .absolutePath,
    )
  }

val celestialLayoutTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.atmosphere.CelestialLayoutTest"
  }

val tradeRecipeContractTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath =
      policy.runtimeClasspath +
        files(
          tasks
            .named<net.neoforged.nfrtgradle.CreateMinecraftArtifacts>("createMinecraftArtifacts")
            .flatMap { it.resourcesArtifact }
        )
    mainClass = "dev.aeroscapes.qualityoflife.TradeRecipeContractTest"
  }

val managedBackupArchiveTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.backup.ManagedBackupArchiveTest"
  }

val distantHorizonsBackupPolicyTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.backup.DistantHorizonsBackupPolicyTest"
    args(layout.projectDirectory.file("../../pack/config/simplebackups-common.toml").asFile)
  }

tasks.register<JavaExec>("generateCelestialAtlas") {
  group = "build"
  dependsOn(tasks.named(policy.classesTaskName))
  classpath = policy.runtimeClasspath
  mainClass = "dev.aeroscapes.atmosphere.CelestialAtlas"
  args(
    layout.projectDirectory
      .file("src/main/resources/assets/aeroscapes_reimagine/textures/environment/celestials.png")
      .asFile
      .absolutePath
  )
}

val effectDescriptionPagesTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.atmosphere.EffectDescriptionPagesTest"
  }

val equipmentModelsCheck by
  tasks.registering(JavaExec::class) {
    group = "verification"
    val minecraftResources =
      tasks
        .named<net.neoforged.nfrtgradle.CreateMinecraftArtifacts>("createMinecraftArtifacts")
        .flatMap { it.resourcesArtifact }
    dependsOn(tasks.named(policy.classesTaskName), minecraftResources)
    classpath = policy.runtimeClasspath + files(minecraftResources) + files(equipmentAssets)
    mainClass = "dev.aeroscapes.aquatic.EquipmentModels"
    args(layout.projectDirectory.dir("../..").asFile.absolutePath, "--check")
    systemProperty("java.awt.headless", "true")
  }

val hookCompatibilityTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.movement.HookCompatibilityTest"
    args(
      layout.projectDirectory
        .dir(
          "../../pack/resourcepacks/aeroscapes-reimagine-atmosphere/assets/yo_hooks/models/item/equipment/held"
        )
        .asFile
        .absolutePath,
      layout.projectDirectory
        .file(
          "../../pack/resourcepacks/aeroscapes-reimagine-atmosphere/assets/yo_hooks/textures/entity/hook_rope.png"
        )
        .asFile
        .absolutePath,
    )
  }

val toolModelContractTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    val minecraftResources =
      tasks
        .named<net.neoforged.nfrtgradle.CreateMinecraftArtifacts>("createMinecraftArtifacts")
        .flatMap { it.resourcesArtifact }
    dependsOn(tasks.named(policy.classesTaskName), minecraftResources)
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.aquatic.ToolModelContractTest"
    classpath += files(minecraftResources) + files(equipmentAssets)
    argumentProviders.add(
      CommandLineArgumentProvider { listOf(minecraftResources.get().asFile.absolutePath) }
    )
  }

val equipmentArtworkTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.atmosphere.EquipmentArtworkTest"
    systemProperty("java.awt.headless", "true")
  }

val vanillaEquipmentModelTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    val minecraftResources =
      tasks
        .named<net.neoforged.nfrtgradle.CreateMinecraftArtifacts>("createMinecraftArtifacts")
        .flatMap { it.resourcesArtifact }
    dependsOn(tasks.named(policy.classesTaskName), minecraftResources)
    classpath = policy.runtimeClasspath + files(minecraftResources) + files(equipmentAssets)
    classpath += sourceSets.main.get().runtimeClasspath
    mainClass = "dev.aeroscapes.aquatic.VanillaEquipmentModelTest"
    args(layout.projectDirectory.dir("../..").asFile.absolutePath)
  }

val rodModelContractTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.aquatic.RodModelContractTest"
  }

val reviewedEquipmentModelTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(vanillaEquipmentModelTest)
    classpath = vanillaEquipmentModelTest.get().classpath
    mainClass = "dev.aeroscapes.aquatic.ReviewedEquipmentModelTest"
    args(layout.projectDirectory.dir("../..").asFile.absolutePath)
  }

val knifeEquipmentModelTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(vanillaEquipmentModelTest)
    classpath = vanillaEquipmentModelTest.get().classpath
    mainClass = "dev.aeroscapes.aquatic.KnifeEquipmentModelTest"
    args(layout.projectDirectory.dir("../..").asFile.absolutePath)
  }

val constructionEquipmentModelTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(vanillaEquipmentModelTest)
    classpath = vanillaEquipmentModelTest.get().classpath
    mainClass = "dev.aeroscapes.aquatic.ConstructionEquipmentModelTest"
    args(layout.projectDirectory.dir("../..").asFile.absolutePath)
  }

val aquaticToolModelTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(vanillaEquipmentModelTest)
    classpath = vanillaEquipmentModelTest.get().classpath
    mainClass = "dev.aeroscapes.aquatic.AquaticToolModelTest"
    args(layout.projectDirectory.dir("../..").asFile.absolutePath)
  }

val constructionPlacementTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(vanillaEquipmentModelTest)
    classpath = vanillaEquipmentModelTest.get().classpath
    mainClass = "dev.aeroscapes.aquatic.ConstructionPlacementTest"
    args(layout.projectDirectory.dir("../..").asFile.absolutePath)
  }

val heldEquipmentRenderingTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.atmosphere.HeldEquipmentRenderingTest"
  }

val utilityEquipmentModelTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(reviewedEquipmentModelTest)
    classpath = vanillaEquipmentModelTest.get().classpath
    mainClass = "dev.aeroscapes.aquatic.UtilityEquipmentModelTest"
    args(layout.projectDirectory.dir("../..").asFile.absolutePath)
  }

val weatheringTextureTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = vanillaEquipmentModelTest.get().classpath
    mainClass = "dev.aeroscapes.weathering.WeatheringTextureTest"
    systemProperty("java.awt.headless", "true")
  }

val orePresentationContractTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = vanillaEquipmentModelTest.get().classpath
    mainClass = "dev.aeroscapes.atmosphere.OrePresentationContractTest"
    systemProperty("java.awt.headless", "true")
  }

val paragliderExhaustionTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.movement.ParagliderExhaustionTest"
  }

val seatedRiderAnimationTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.fauna.SeatedRiderAnimationTest"
  }

val hookHangMathTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.movement.HookHangMathTest"
  }

val spellMouseGestureTest by
  tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.named(policy.classesTaskName))
    classpath = policy.runtimeClasspath
    mainClass = "dev.aeroscapes.magic.SpellMouseGestureTest"
  }

tasks.register<JavaExec>("generateEquipmentModels") {
  group = "build"
  val minecraftResources =
    tasks
      .named<net.neoforged.nfrtgradle.CreateMinecraftArtifacts>("createMinecraftArtifacts")
      .flatMap { it.resourcesArtifact }
  dependsOn(tasks.named(policy.classesTaskName), minecraftResources)
  classpath = policy.runtimeClasspath + files(minecraftResources) + files(equipmentAssets)
  mainClass = "dev.aeroscapes.aquatic.EquipmentModels"
  args(layout.projectDirectory.dir("../..").asFile.absolutePath)
  systemProperty("java.awt.headless", "true")
}

tasks.check {
  dependsOn(
    tasks.spotlessCheck,
    effectDescriptionPagesTest,
    equipmentModelsCheck,
    hookCompatibilityTest,
    toolModelContractTest,
    equipmentArtworkTest,
    vanillaEquipmentModelTest,
    reviewedEquipmentModelTest,
    knifeEquipmentModelTest,
    constructionEquipmentModelTest,
    aquaticToolModelTest,
    constructionPlacementTest,
    utilityEquipmentModelTest,
    weatheringTextureTest,
    orePresentationContractTest,
    heldEquipmentRenderingTest,
    rodModelContractTest,
    paragliderExhaustionTest,
    seatedRiderAnimationTest,
    hookHangMathTest,
    spellMouseGestureTest,
    policyTest,
    islandLayoutTest,
    exposedIslandSurfacesTest,
    ecologyContractTest,
    spawnTintTransformerTest,
    templateStructureSupportTest,
    biomeTerrainMaskTest,
    sableTraceBufferTest,
    monitorControllerTest,
    frameIntervalStatsTest,
    awaitTrackerTest,
    shoulderCameraTransformerTest,
    atmosphereContractTest,
    punchyMapProfileTest,
    celestialLayoutTest,
    tradeRecipeContractTest,
    managedBackupArchiveTest,
    distantHorizonsBackupPolicyTest,
    worldgenRegressionJar,
  )
}
