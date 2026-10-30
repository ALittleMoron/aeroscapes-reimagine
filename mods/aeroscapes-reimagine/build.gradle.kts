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
  version = "21.1.230"
  mods { create("aeroscapes_reimagine") { sourceSet(sourceSets.main.get()) } }
}

repositories {
  mavenCentral()
  exclusiveContent {
    forRepository { maven("https://maven.minecraftforge.net/") }
    filter { includeModule("com.github.glitchfiend", "TerraBlender-neoforge") }
  }
  exclusiveContent {
    forRepository { maven("https://api.modrinth.com/maven") }
    filter {
      includeModule("maven.modrinth", "c2me-neoforge")
      includeModule("maven.modrinth", "c2me-ocl")
      includeModule("maven.modrinth", "sable")
      includeModule("maven.modrinth", "particle-rain")
      includeModule("maven.modrinth", "punchy-fpa")
      includeModule("maven.modrinth", "entity-model-features")
      includeModule("maven.modrinth", "entitytexturefeatures")
      includeModule("maven.modrinth", "puzzle")
      includeModule("maven.modrinth", "flerovium")
      includeModule("maven.modrinth", "create-aeronautics")
      includeModule("maven.modrinth", "create")
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
    "META-INF/jars/c2me-neoforge-opts-accel-opencl-*.jar",
    "META-INF/jars/lwjgl-opencl-3.3.3.jar",
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
val sableNestedApiDirectory = layout.buildDirectory.dir("sable-api")
val extractSableApi by
  tasks.registering(Sync::class) {
    from(sableApi.map { zipTree(it) })
    include(
      "META-INF/jarjar/veil-neoforge-1.21.1-4.3.2.jar",
      "META-INF/jarjar/sable-companion-common-1.21.1-1.6.0.jar",
    )
    eachFile { path = name }
    includeEmptyDirs = false
    into(sableNestedApiDirectory)
  }

dependencies {
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
  atmosphereApi("maven.modrinth:entity-model-features:197m3VoU")
  atmosphereApi("maven.modrinth:entitytexturefeatures:lwNx1nD1")
  atmosphereApi("maven.modrinth:puzzle:bZNDTLHq")
  atmosphereApi("maven.modrinth:flerovium:oU1q3eIj")
  compileOnly(files(atmosphereApi))
  compileOnly(
    files(fileTree(sableNestedApiDirectory) { include("*.jar") }).builtBy(extractSableApi)
  )
  c2meApi("maven.modrinth:c2me-neoforge:avwWYPbG")
  c2meApi("maven.modrinth:c2me-ocl:7rX3dlFf")
  compileOnly(files(fileTree(c2meApiDirectory) { include("*.jar") }).builtBy(extractC2meApi))
}

val policy by sourceSets.creating {
  compileClasspath =
    sourceSets.main.get().output +
      sourceSets.main.get().compileClasspath +
      configurations[compileClasspathConfigurationName]
  runtimeClasspath = output + compileClasspath
}

dependencies { add(policy.implementationConfigurationName, "org.ow2.asm:asm-tree:9.8") }

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
        (atmosphereApi.files + sableApi.files).joinToString(File.pathSeparator),
      )
    }
  }

tasks.check {
  dependsOn(
    tasks.spotlessCheck,
    policyTest,
    islandLayoutTest,
    sableTraceBufferTest,
    monitorControllerTest,
    shoulderCameraTransformerTest,
    atmosphereContractTest,
    worldgenRegressionJar,
  )
}
