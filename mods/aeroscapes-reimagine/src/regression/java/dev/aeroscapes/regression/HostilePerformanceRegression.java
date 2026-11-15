package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.dacite.skywhales.config.SWConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

/** Disposable server measurements with a native raid; does not stand in for client acceptance. */
public final class HostilePerformanceRegression {
  private static final String MANAGER = "dev.thebluetaco.hostileskies.raid.RaidManager";
  private final ServerLevel level;
  private final String mode;
  private final String shipId =
      System.getProperty("aeroscapes.regression.hostileShip", "hostile_skies:graf_t2");
  private final int warmup = Integer.getInteger("aeroscapes.regression.hostileWarmup", 400);
  private final int duration = Integer.getInteger("aeroscapes.regression.hostileSamples", 600);
  private final long deadline = System.nanoTime() + 240_000_000_000L;
  private final List<Sample> samples = new ArrayList<>();
  private final Consumer<ServerTickEvent.Pre> preListener = this::beforeTick;
  private final Consumer<ServerTickEvent.Post> postListener = this::afterTick;
  private final Consumer<ServerStoppingEvent> stopListener = this::stopping;
  private final boolean previousWhaleSpawning;
  private final boolean previousMobSpawning;
  @Nullable private final ModConfigSpec.BooleanValue automaticRaids;
  private final boolean previousAutomaticRaids;
  @Nullable private final Field trackedRaids;
  @Nullable private TestShipPlayerSession session;
  @Nullable private UUID raidId;
  @Nullable private Vector3d firstPosition;
  @Nullable private Vector3d previousPosition;
  private double travelled;
  private long tickStarted;
  private long spawnNanos;
  private int elapsed;
  private int crewCount;
  private boolean spawned;
  private boolean finished;

  private HostilePerformanceRegression(ServerLevel level, String mode)
      throws ReflectiveOperationException {
    this.level = level;
    this.mode = mode;
    require(List.of("baseline", "idle", "raid").contains(mode), "Unknown benchmark mode");
    require(warmup >= 40 && duration >= 100, "Benchmark window is too short");
    boolean installed = ModList.get().isLoaded("hostile_skies");
    require(installed == !mode.equals("baseline"), "Baseline must omit Hostile Skies");
    require(level.getServer().getPlayerList().getPlayerCount() == 0, "Disposable server only");
    require(level.players().isEmpty(), "Disposable empty level required");
    require(
        SubLevelContainer.getContainer(level).getAllSubLevels().isEmpty(),
        "Benchmark world must not contain ships");
    previousWhaleSpawning = SWConfig.SPAWN_ENABLED.get();
    previousMobSpawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    if (installed) {
      Class<?> config = Class.forName("dev.thebluetaco.hostileskies.raid.RaidConfig");
      Object setting = config.getField("enableRaidSpawns").get(null);
      require(setting instanceof ModConfigSpec.BooleanValue, "Unexpected raid config type");
      automaticRaids = (ModConfigSpec.BooleanValue) setting;
      previousAutomaticRaids = automaticRaids.get();
      trackedRaids = Class.forName(MANAGER).getDeclaredField("activeRaids");
      trackedRaids.setAccessible(true);
      require(raidObjects().isEmpty(), "Unexpected pre-existing raid");
    } else {
      automaticRaids = null;
      trackedRaids = null;
      previousAutomaticRaids = false;
    }
  }

  public static void start(ServerLevel level, String mode) {
    try {
      HostilePerformanceRegression fixture = new HostilePerformanceRegression(level, mode);
      fixture.initialize();
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Hostile Skies 0.2.6 API changed", failure);
    }
  }

  private void initialize() {
    SWConfig.SPAWN_ENABLED.set(false);
    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
    if (automaticRaids != null) automaticRaids.set(false);
    BlockPos spawn = level.getSharedSpawnPos();
    level.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
    session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("4e8869d9-8e6c-4a06-b051-76f313f49b1a"),
            "RaidBenchmark",
            new Vec3(spawn.getX() + .5, spawn.getY() + 80, spawn.getZ() + .5),
            null);
    session.player.updateOptions(
        new ClientInformation(
            "en_us", 12, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, false));
    java.util.Objects.requireNonNull(
            session.player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.CREATIVE_FLIGHT))
        .setBaseValue(1.0);
    session.player.getAbilities().flying = true;
    session.player.getAbilities().invulnerable = true;
    level.getChunkSource().move(session.player);
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, preListener);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, postListener);
    NeoForge.EVENT_BUS.addListener(stopListener);
    LogUtils.getLogger()
        .info(
            "Hostile benchmark started mode={} ship={} simulation={} serverView={} playerView=12 warmup={} samples={}",
            mode,
            shipId,
            level.getServer().getPlayerList().getSimulationDistance(),
            level.getServer().getPlayerList().getViewDistance(),
            warmup,
            duration);
  }

  private void beforeTick(ServerTickEvent.Pre event) {
    if (!finished && event.getServer() == level.getServer()) tickStarted = System.nanoTime();
  }

  private void afterTick(ServerTickEvent.Post event) {
    if (finished || event.getServer() != level.getServer()) return;
    long completed = System.nanoTime();
    try {
      require(completed < deadline, "Hostile benchmark exceeded four minutes");
      require(
          level.getServer().getPlayerList().getPlayerCount() == 0, "Real player joined fixture");
      require(session != null, "Missing fixture player");
      session.tick();
      session.player.setHealth(session.player.getMaxHealth());
      session.player.getFoodData().setFoodLevel(20);
      elapsed++;
      if (!spawned && elapsed >= 100) {
        if (mode.equals("raid")) spawnRaid();
        spawned = true;
        elapsed = 0;
      }
      if (!spawned) return;
      RaidState state = observe();
      if (elapsed < warmup) return;
      long[] nativeTimes = level.getServer().getTickTimesNanos();
      samples.add(
          new Sample(
              level.getServer().getTickCount(),
              completed - tickStarted,
              nativeTimes[level.getServer().getTickCount() % nativeTimes.length],
              level.getChunkSource().getLoadedChunksCount(),
              state));
      if (samples.size() % 100 == 0)
        LogUtils.getLogger()
            .info("Hostile benchmark progress samples={} state={}", samples.size(), state);
      if (samples.size() == duration) finish();
    } catch (ReflectiveOperationException | CommandSyntaxException | IOException failure) {
      close();
      throw new IllegalStateException("Hostile benchmark failed", failure);
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  private void spawnRaid() throws CommandSyntaxException, ReflectiveOperationException {
    require(session != null, "Missing player");
    level.random.setSeed(78391L);
    long started = System.nanoTime();
    int result =
        level
            .getServer()
            .getCommands()
            .getDispatcher()
            .execute(
                "hostileskies spawnraid " + shipId + " 0",
                session.player.createCommandSourceStack().withPermission(2));
    spawnNanos = System.nanoTime() - started;
    require(result == 1, "Native raid spawn command failed");
    List<?> raids = raidObjects();
    require(raids.size() == 1, "Expected one native tracked raid");
    Object identity = field(raids.getFirst(), "subLevelId");
    require(identity instanceof UUID, "Invalid native raid identity");
    raidId = (UUID) identity;
    LogUtils.getLogger()
        .info("Hostile benchmark native raid={} spawnMs={}", raidId, spawnNanos / 1e6);
  }

  private RaidState observe() throws ReflectiveOperationException {
    if (!mode.equals("raid"))
      return new RaidState(
          mode.toUpperCase(java.util.Locale.ROOT), false, false, false, 0, 0, 0, 0, 0);
    List<?> raids = raidObjects();
    require(raids.size() == 1, "Native raid disappeared during measurement");
    Object raid = raids.getFirst();
    String phase = String.valueOf(field(raid, "phase"));
    boolean crewSpawned = Boolean.TRUE.equals(field(raid, "crewSpawned"));
    var body = SubLevelContainer.getContainer(level).getSubLevel(raidId);
    if (!(body instanceof ServerSubLevel ship) || body.isRemoved())
      return new RaidState(phase, false, false, crewSpawned, 0, 0, 0, 0, crewCount);
    Vector3d position = new Vector3d(ship.logicalPose().position());
    if (firstPosition == null) firstPosition = new Vector3d(position);
    if (previousPosition != null) travelled += previousPosition.distance(position);
    previousPosition = new Vector3d(position);
    if (elapsed % 20 == 0)
      crewCount =
          level.getEntitiesOfClass(Mob.class, ship.boundingBox().toMojang().inflate(16)).size();
    return new RaidState(
        phase,
        true,
        level.isPositionEntityTicking(BlockPos.containing(position.x, position.y, position.z)),
        crewSpawned,
        ship.getPlot().getLoadedChunks().size(),
        position.x,
        position.y,
        position.z,
        crewCount);
  }

  private List<?> raidObjects() throws IllegalAccessException {
    if (trackedRaids == null) return List.of();
    Object value = trackedRaids.get(null);
    require(value instanceof List<?>, "Native raid list changed");
    return (List<?>) value;
  }

  private static Object field(Object owner, String name) throws ReflectiveOperationException {
    Field declared = owner.getClass().getDeclaredField(name);
    declared.setAccessible(true);
    return declared.get(owner);
  }

  private void finish() throws IOException {
    long[] inclusive = samples.stream().mapToLong(Sample::inclusiveNanos).sorted().toArray();
    long[] core = samples.stream().mapToLong(Sample::nativeCoreNanos).sorted().toArray();
    Map<String, Integer> phases = new LinkedHashMap<>();
    for (Sample sample : samples) phases.merge(sample.raid.phase, 1, Integer::sum);
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("mode", mode);
    report.put("ship", shipId);
    report.put("seed", level.getSeed());
    report.put("simulationDistance", level.getServer().getPlayerList().getSimulationDistance());
    report.put("serverViewDistance", level.getServer().getPlayerList().getViewDistance());
    report.put("requestedPlayerViewDistance", 12);
    report.put("warmupTicks", warmup);
    report.put("sampleTicks", samples.size());
    report.put("spawnMs", spawnNanos / 1e6);
    report.put("inclusiveTickWorkMs", statistics(inclusive));
    report.put("nativeCoreTickMs", statistics(core));
    report.put(
        "metricBoundary",
        "HIGHEST Pre to LOWEST Post includes Hostile Post; native core excludes Post");
    report.put("raidPhaseSamples", phases);
    report.put("shipLoadedSamples", samples.stream().filter(value -> value.raid.loaded).count());
    report.put(
        "shipEntityTickingSamples",
        samples.stream().filter(value -> value.raid.entityTicking).count());
    report.put(
        "crewSpawnedSamples", samples.stream().filter(value -> value.raid.crewSpawned).count());
    report.put("shipTravelledBlocksIncludingWarmup", travelled);
    report.put(
        "shipNetDisplacementIncludingWarmup",
        firstPosition == null || previousPosition == null
            ? 0
            : firstPosition.distance(previousPosition));
    double sampleTravel = 0;
    RaidState previous = null;
    RaidState first = null;
    for (Sample sample : samples) {
      RaidState state = sample.raid;
      if (!state.loaded) continue;
      if (first == null) first = state;
      if (previous != null)
        sampleTravel +=
            new Vec3(previous.x, previous.y, previous.z)
                .distanceTo(new Vec3(state.x, state.y, state.z));
      previous = state;
    }
    report.put("shipTravelledBlocksDuringSamples", sampleTravel);
    report.put(
        "shipNetDisplacementDuringSamples",
        first == null || previous == null
            ? 0
            : new Vec3(first.x, first.y, first.z)
                .distanceTo(new Vec3(previous.x, previous.y, previous.z)));
    report.put("loadedChunksMin", samples.stream().mapToInt(Sample::loadedChunks).min().orElse(0));
    report.put("loadedChunksMax", samples.stream().mapToInt(Sample::loadedChunks).max().orElse(0));
    report.put("samples", samples);
    Path output =
        Path.of(
            System.getProperty(
                "aeroscapes.regression.hostileReport",
                level
                    .getServer()
                    .getWorldPath(LevelResource.ROOT)
                    .resolve("hostile-benchmark.json")
                    .toString()));
    Files.createDirectories(output.toAbsolutePath().getParent());
    Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    close();
    LogUtils.getLogger()
        .info(
            "Hostile benchmark complete mode={} report={} tickWork={} phases={}",
            mode,
            output,
            statistics(inclusive),
            phases);
    if (Boolean.parseBoolean(System.getProperty("aeroscapes.regression.hostileStop", "true")))
      level.getServer().halt(false);
  }

  private static Map<String, Double> statistics(long[] sorted) {
    return Map.of(
        "mean", Arrays.stream(sorted).average().orElse(0) / 1e6,
        "median", sorted[sorted.length / 2] / 1e6,
        "p95", sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6,
        "p99", sorted[(int) Math.ceil(sorted.length * .99) - 1] / 1e6,
        "max", sorted[sorted.length - 1] / 1e6);
  }

  private void stopping(ServerStoppingEvent event) {
    if (event.getServer() == level.getServer()) close();
  }

  private void close() {
    if (finished) return;
    finished = true;
    NeoForge.EVENT_BUS.unregister(preListener);
    NeoForge.EVENT_BUS.unregister(postListener);
    NeoForge.EVENT_BUS.unregister(stopListener);
    if (session != null) session.close();
    SWConfig.SPAWN_ENABLED.set(previousWhaleSpawning);
    level
        .getGameRules()
        .getRule(GameRules.RULE_DOMOBSPAWNING)
        .set(previousMobSpawning, level.getServer());
    if (automaticRaids != null) automaticRaids.set(previousAutomaticRaids);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private record Sample(
      int tick, long inclusiveNanos, long nativeCoreNanos, int loadedChunks, RaidState raid) {}

  private record RaidState(
      String phase,
      boolean loaded,
      boolean entityTicking,
      boolean crewSpawned,
      int plotChunks,
      double x,
      double y,
      double z,
      int nearbyCrew) {}
}
