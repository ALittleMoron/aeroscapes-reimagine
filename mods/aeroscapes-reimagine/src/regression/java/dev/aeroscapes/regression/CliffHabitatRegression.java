package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.ecology.cliff.CliffHabitatFeature;
import dev.aeroscapes.ecology.cliff.CliffHabitats;
import dev.aeroscapes.ecology.cliff.CliffSite;
import dev.aeroscapes.mixin.ecology.FowlCliffFlightAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class CliffHabitatRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final int CROP_RADIUS = 10;
  private static final int CROP_BELOW = 13;
  private static final int CROP_ABOVE = 8;

  private CliffHabitatRegression() {}

  static void verifyLegacy(ServerLevel level) {
    var root = new BlockPos(138, 213, 617);
    var gull = new BlockPos(-86, 88, 634);
    var bat = new BlockPos(245, 141, 805);
    legacySite(level, root, CliffSite.Kind.ROOTS);
    require(level.getBlockState(root).is(Blocks.HANGING_ROOTS), "Legacy root was replaced");
    verifyResidentsWhenLoaded(
        level,
        List.of(
            legacySite(level, gull, CliffSite.Kind.GULLS),
            legacySite(level, bat, CliffSite.Kind.BATS)),
        "Cliff legacy habitats regression passed");
  }

  private static CliffSite legacySite(ServerLevel level, BlockPos position, CliffSite.Kind kind) {
    var site = CliffHabitats.site(chunk(level, position), kind);
    require(
        site != null && site.anchor().equals(position),
        "Missing legacy " + kind + " at " + position);
    return site;
  }

  public static void verify(ServerLevel level, String marker) {
    var matches = new EnumMap<CliffSite.Kind, List<CliffSite>>(CliffSite.Kind.class);
    for (var kind : CliffSite.Kind.values()) matches.put(kind, new ArrayList<>());
    var center = new ChunkPos(level.getSharedSpawnPos());
    int radius = Integer.getInteger("aeroscapes.regression.cliffRadius", 15);
    long attemptsBefore = CliffHabitatFeature.ATTEMPTS.sum();
    long featureBefore = CliffHabitatFeature.NANOS.sum();
    long started = System.nanoTime();
    int inspected = 0;
    int allAir = 0;
    int relocatedRoosts = 0;
    int unavailableRoosts = 0;
    int unavailableColonies = 0;
    for (ChunkPos pos : ChunkPos.rangeClosed(center, radius).toList()) {
      var chunk = level.getChunk(pos.x, pos.z);
      inspected++;
      if (chunk.isYSpaceEmpty(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1)) allAir++;
      for (var kind : CliffSite.Kind.values()) {
        var site = CliffHabitats.site(chunk, kind);
        if (site == null) continue;
        if (kind == CliffSite.Kind.GULLS && !CliffHabitats.validateColony(level, chunk, site)) {
          unavailableColonies++;
          continue;
        }
        if (kind == CliffSite.Kind.BATS) {
          var valid = CliffHabitats.resolveRoost(level, chunk, site);
          if (valid == null) {
            unavailableRoosts++;
            continue;
          }
          if (!site.anchor().equals(valid.anchor()) || site.facing() != valid.facing())
            relocatedRoosts++;
          site = valid;
        }
        require(
            pos.equals(new ChunkPos(site.anchor())), "Cliff site was stored in the wrong chunk");
        matches.get(kind).add(site);
      }
    }
    long generatedFor = System.nanoTime() - started;

    var report = new JsonObject();
    report.addProperty("seed", level.getSeed());
    report.addProperty("inspectedFullChunks", inspected);
    report.addProperty("allAirControlChunks", allAir);
    report.addProperty("relocatedRoosts", relocatedRoosts);
    report.addProperty("unavailableRoosts", unavailableRoosts);
    report.addProperty("unavailableColonies", unavailableColonies);
    report.addProperty("generationElapsedMs", generatedFor / 1_000_000.0);
    report.addProperty("featureAttempts", CliffHabitatFeature.ATTEMPTS.sum() - attemptsBefore);
    report.addProperty(
        "featureElapsedMs", (CliffHabitatFeature.NANOS.sum() - featureBefore) / 1_000_000.0);
    var sites = new JsonObject();
    for (var entry : matches.entrySet()) {
      var samples = new JsonArray();
      for (var site : entry.getValue()) samples.add(siteJson(site));
      sites.add(entry.getKey().getSerializedName(), samples);
    }
    report.add("sites", sites);
    if (radius >= 13) report.add("flightSightlines", flightSightlines(level, center, matches));

    var crops = new JsonArray();
    for (var kind : CliffSite.Kind.values()) {
      var found = matches.get(kind);
      if (!found.isEmpty()) crops.add(crop(level, found.getFirst()));
    }
    matches.get(CliffSite.Kind.ROOTS).stream()
        .filter(CliffSite::lit)
        .findFirst()
        .ifPresent(site -> crops.add(crop(level, site)));
    report.add("crops", crops);
    String output = System.getProperty("aeroscapes.regression.cliffReport", "cliff-habitats.json");
    try {
      Files.writeString(Path.of(output), report.toString());
    } catch (IOException error) {
      throw new UncheckedIOException("Could not write cliff habitat evidence", error);
    }
    LOGGER.info(
        "Cliff sample counts {}, void {}, attempts {}, cost {} ms",
        counts(matches),
        allAir,
        report.get("featureAttempts"),
        report.get("featureElapsedMs"));
    require(allAir > 0, "Cliff sample has no all-air controls");
    require(
        expectedKinds().stream().noneMatch(kind -> matches.get(kind).isEmpty()),
        "Missing generated cliff habitat samples " + counts(matches));

    for (var site : matches.get(CliffSite.Kind.ROOTS)) verifyRoots(level, site);
    for (var site : matches.get(CliffSite.Kind.GULLS)) verifyLedge(level, site);
    for (var site : matches.get(CliffSite.Kind.BATS)) verifyRoof(level, site);
    var residents = new ArrayList<CliffSite>();
    for (var kind : List.of(CliffSite.Kind.GULLS, CliffSite.Kind.BATS)) {
      matches.get(kind).stream()
          .filter(site -> site.stockedDay() < 0)
          .findFirst()
          .ifPresent(residents::add);
    }
    verifyResidentsWhenLoaded(level, residents, marker);
  }

  private static void verifyResidentsWhenLoaded(
      ServerLevel level, List<CliffSite> sites, String marker) {
    var forced = new ArrayList<ChunkPos>();
    for (var site : sites) {
      var pos = new ChunkPos(site.anchor());
      if (level.setChunkForced(pos.x, pos.z, true)) forced.add(pos);
    }
    NeoForge.EVENT_BUS.addListener(
        new Consumer<ServerTickEvent.Post>() {
          private int ticks;

          @Override
          public void accept(ServerTickEvent.Post event) {
            require(++ticks < 240, "Cliff entity sections failed to load");
            for (var site : sites) {
              if (!level.areEntitiesLoaded(new ChunkPos(site.anchor()).toLong())
                  || !level.isPositionEntityTicking(site.anchor())) return;
            }
            NeoForge.EVENT_BUS.unregister(this);
            long originalDay = level.getDayTime();
            try {
              level.setDayTime(6000);
              level.updateSkyBrightness();
              for (var site : sites) {
                if (site.kind() == CliffSite.Kind.GULLS) verifyGulls(level, site);
                if (site.kind() == CliffSite.Kind.BATS) verifyBats(level, site);
              }
              LOGGER.info("{}", marker);
            } finally {
              level.setDayTime(originalDay);
              level.updateSkyBrightness();
              forced.forEach(pos -> level.setChunkForced(pos.x, pos.z, false));
            }
          }
        });
  }

  private static EnumSet<CliffSite.Kind> expectedKinds() {
    var value = System.getProperty("aeroscapes.regression.cliffExpected");
    if (value == null) return EnumSet.allOf(CliffSite.Kind.class);
    var expected = EnumSet.noneOf(CliffSite.Kind.class);
    for (String kind : value.split(",", -1)) {
      expected.add(CliffSite.Kind.valueOf(kind.strip().toUpperCase(Locale.ROOT)));
    }
    return expected;
  }

  private static Map<CliffSite.Kind, Integer> counts(Map<CliffSite.Kind, List<CliffSite>> matches) {
    var counts = new EnumMap<CliffSite.Kind, Integer>(CliffSite.Kind.class);
    matches.forEach((kind, sites) -> counts.put(kind, sites.size()));
    return counts;
  }

  private static JsonArray flightSightlines(
      ServerLevel level, ChunkPos center, Map<CliffSite.Kind, List<CliffSite>> sites) {
    var routes = new JsonArray();
    var origin = center.getMiddleBlockPosition(0);
    for (int y : new int[] {-32, 85, 145, 205}) {
      for (int offset : new int[] {-96, 96}) {
        routes.add(scanFlight(level, origin.offset(-192, y, offset), Direction.EAST, sites));
        routes.add(scanFlight(level, origin.offset(offset, y, -192), Direction.SOUTH, sites));
      }
    }
    return routes;
  }

  private static JsonObject scanFlight(
      ServerLevel level,
      BlockPos start,
      Direction facing,
      Map<CliffSite.Kind, List<CliffSite>> sites) {
    Set<CliffSite> visible = new HashSet<>();
    int openSegments = 0;
    for (int length = 0; length < 384; length += 8) {
      var point = start.relative(facing, length);
      if (!level.isEmptyBlock(point) || !level.isEmptyBlock(point.above())) continue;
      Vec3 eye = Vec3.atCenterOf(point.above());
      if (!unobstructed(level, eye, Vec3.atCenterOf(point.relative(facing, 8).above()))) continue;
      openSegments++;
      for (var cohort : sites.values()) {
        for (var site : cohort) {
          var target = site.kind() == CliffSite.Kind.ROOTS ? site.anchor().below(2) : site.anchor();
          if (!visible.contains(site)
              && target.closerToCenterThan(eye, 48)
              && unobstructed(level, eye, Vec3.atCenterOf(target))) visible.add(site);
        }
      }
    }
    var result = new JsonObject();
    result.add("start", coordinate(start));
    result.addProperty("facing", facing.getSerializedName());
    result.addProperty("openSegments", openSegments);
    var seen = new JsonArray();
    visible.stream()
        .sorted(java.util.Comparator.comparingLong(site -> site.anchor().asLong()))
        .forEach(site -> seen.add(siteJson(site)));
    result.add("withinView", seen);
    return result;
  }

  private static boolean unobstructed(ServerLevel level, Vec3 from, Vec3 to) {
    return level
            .clip(
                new ClipContext(
                    from,
                    to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY,
                    CollisionContext.empty()))
            .getType()
        == HitResult.Type.MISS;
  }

  private static void verifyRoots(ServerLevel level, CliffSite site) {
    int roots = 0;
    int vines = 0;
    for (int offset = -3; offset <= 3; offset++) {
      boolean vine = offset != 0 && offset % 2 == 0;
      for (int dy = 2; dy >= -2; dy--) {
        var column = site.ledge(offset).above(dy);
        var state = level.getBlockState(column);
        if (!vine && state.is(Blocks.HANGING_ROOTS)) {
          verifyPlant(level, column);
          roots++;
          break;
        }
        if (vine
            && state.is(Blocks.CAVE_VINES_PLANT)
            && level.getBlockState(column.below()).is(Blocks.CAVE_VINES_PLANT)
            && (level.getBlockState(column.below(2)).is(Blocks.CAVE_VINES_PLANT)
                || level.getBlockState(column.below(2)).is(Blocks.CAVE_VINES))) {
          for (int depth = 0; depth < 3; depth++) verifyPlant(level, column.below(depth));
          vines++;
          break;
        }
      }
    }
    require(roots >= 3 && vines >= 2, "Curtain lost its silhouette at " + site.anchor());
    if (site.lit()) verifyCurtainLight(level, site);
  }

  private static void verifyPlant(ServerLevel level, BlockPos pos) {
    var state = level.getBlockState(pos);
    require(state.canSurvive(level, pos), "Unsupported curtain at " + pos);
    require(state.getCollisionShape(level, pos).isEmpty(), "Curtain obstructs flight at " + pos);
  }

  private static void verifyCurtainLight(ServerLevel level, CliffSite site) {
    for (Direction facing : new Direction[] {site.facing(), site.facing().getOpposite()}) {
      for (int dy = -2; dy <= 2; dy++) {
        var pos = site.anchor().relative(facing).above(dy);
        var state = level.getBlockState(pos);
        if (state.is(Blocks.GLOW_LICHEN) && level.getBrightness(LightLayer.BLOCK, pos) >= 7) {
          verifyPlant(level, pos);
          return;
        }
      }
    }
    throw new IllegalStateException("FULL light did not register curtain at " + site.anchor());
  }

  private static void verifyLedge(ServerLevel level, CliffSite site) {
    for (int offset = -1; offset <= 1; offset++) {
      var stand = site.ledge(offset);
      var floor = stand.below();
      require(
          openFlightCell(level, stand)
              && openFlightCell(level, stand.above())
              && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP),
          "Gulls have no intact ledge at " + stand);
    }
    require(
        openFlightCell(level, site.anchor().relative(site.facing()))
            && openFlightCell(level, site.anchor().relative(site.facing()).above())
            && level.isEmptyBlock(site.anchor().relative(site.facing(), 2).below(5)),
        "Gull flight route is blocked at " + site.anchor());
  }

  private static boolean openFlightCell(ServerLevel level, BlockPos pos) {
    var state = level.getBlockState(pos);
    return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
  }

  private static void verifyRoof(ServerLevel level, CliffSite site) {
    for (int offset = -1; offset <= 1; offset++) {
      var rest = site.ledge(offset);
      var roof = rest.above();
      require(
          level.isEmptyBlock(rest) && level.getBlockState(roof).isRedstoneConductor(level, rest),
          "Bats have no resting ceiling at " + rest);
    }
    require(
        level.isEmptyBlock(site.anchor().relative(site.facing()))
            && level.isEmptyBlock(site.anchor().relative(site.facing(), 2).below(5)),
        "Bat departure is blocked at " + site.anchor());
  }

  private static void verifyGulls(ServerLevel level, CliffSite ledge) {
    var gullChunk = chunk(level, ledge.anchor());
    var gullArea = new AABB(ledge.anchor()).inflate(4);
    int initialGulls = gulls(level, gullArea).size();
    CliffHabitats.visit(level, gullChunk, ledge);
    var spawnedGulls = gulls(level, gullArea);
    require(
        spawnedGulls.size() == initialGulls + 3,
        "Gull colony did not place three real birds at " + ledge.anchor());
    require(
        spawnedGulls.stream().allMatch(gull -> gull instanceof FowlCliffFlightAccess),
        "Native gull flight adapter was absent");
    CliffHabitats.visit(level, gullChunk, CliffHabitats.site(gullChunk, ledge.kind()));
    require(
        gulls(level, gullArea).size() == spawnedGulls.size(),
        "Second visit duplicated a gull colony");
  }

  private static void verifyBats(ServerLevel level, CliffSite roof) {
    var roostChunk = chunk(level, roof.anchor());
    var batArea = new AABB(roof.anchor()).inflate(4);
    int initialBats = level.getEntitiesOfClass(Bat.class, batArea).size();
    long previousTime = level.getDayTime();
    try {
      level.setDayTime(6000);
      CliffHabitats.visit(level, roostChunk, roof);
      var bats = level.getEntitiesOfClass(Bat.class, batArea);
      require(
          bats.size() == initialBats + 3 && bats.stream().filter(Bat::isResting).count() >= 3,
          "Daytime bats failed to roost at " + roof.anchor());
      level.setDayTime(12300);
      CliffHabitats.visit(level, roostChunk, CliffHabitats.site(roostChunk, roof.kind()));
      var direction = Vec3.atLowerCornerOf(roof.facing().getNormal());
      require(
          bats.stream()
                  .filter(bat -> !bat.isResting() && bat.getDeltaMovement().dot(direction) > 0)
                  .count()
              >= 3,
          "Dusk failed to launch the roost outwards");
      CliffHabitats.visit(level, roostChunk, CliffHabitats.site(roostChunk, roof.kind()));
      require(
          level.getEntitiesOfClass(Bat.class, batArea).size() == bats.size(),
          "Second dusk visit repeated a bat flight");
    } finally {
      level.setDayTime(previousTime);
    }
  }

  private static List<Mob> gulls(ServerLevel level, AABB area) {
    return level.getEntitiesOfClass(
        Mob.class,
        area,
        mob -> BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).equals(CliffHabitats.GULL));
  }

  private static LevelChunk chunk(ServerLevel level, BlockPos position) {
    var pos = new ChunkPos(position);
    return level.getChunk(pos.x, pos.z);
  }

  private static JsonObject siteJson(CliffSite site) {
    var sample = new JsonObject();
    sample.addProperty("kind", site.kind().getSerializedName());
    sample.add("anchor", coordinate(site.anchor()));
    sample.addProperty("facing", site.facing().getSerializedName());
    sample.addProperty("lit", site.lit());
    return sample;
  }

  private static JsonObject crop(ServerLevel level, CliffSite site) {
    var crop = siteJson(site);
    var min = site.anchor().offset(-CROP_RADIUS, -CROP_BELOW, -CROP_RADIUS);
    var max = site.anchor().offset(CROP_RADIUS, CROP_ABOVE, CROP_RADIUS);
    int width = CROP_RADIUS * 2 + 1;
    int height = CROP_BELOW + CROP_ABOVE + 1;
    var palette = new LinkedHashMap<String, Integer>();
    var blocks = new JsonArray();
    for (int y = min.getY(); y <= max.getY(); y++) {
      for (int z = min.getZ(); z <= max.getZ(); z++) {
        for (int x = min.getX(); x <= max.getX(); x++) {
          var state = level.getBlockState(new BlockPos(x, y, z));
          String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
          blocks.add(palette.computeIfAbsent(id, ignored -> palette.size()));
        }
      }
    }
    var paletteJson = new JsonArray();
    palette.keySet().forEach(paletteJson::add);
    crop.add("min", coordinate(min));
    crop.add("size", coordinate(new BlockPos(width, height, width)));
    crop.add("palette", paletteJson);
    crop.add("blocksYZX", blocks);
    return crop;
  }

  private static JsonArray coordinate(BlockPos position) {
    var xyz = new JsonArray(3);
    xyz.add(position.getX());
    xyz.add(position.getY());
    xyz.add(position.getZ());
    return xyz;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
