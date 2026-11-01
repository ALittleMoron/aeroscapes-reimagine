package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import de.melanx.simplebackups.config.CommonConfig;
import dev.aeroscapes.backup.SimpleBackupsCoordinator;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Two-process create/extract/reload test on disposable worlds; no client or real login. */
public final class BackupRoundtripRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final UUID PLAYER = UUID.fromString("5a3a5816-9fe4-4505-a797-3f021460ba5c");
  private static final String EXPECTED = "aeroscapes-backup-roundtrip.dat";
  private static final String NEWER_FILE = "aeroscapes-backup-newer-only.txt";
  private static final String CARGO = "aeroscapes_backup_cargo";
  private static final String BEARING = "aeroscapes_backup_bearing";
  private static final List<ResourceKey<Level>> DIMENSIONS = List.of(Level.OVERWORLD, Level.NETHER);
  private final MinecraftServer server;
  private final String phase;
  private final Path world;
  private TestShipPlayerSession session;
  private CompoundTag expected;
  private Set<Path> before;
  private int ticks;
  private int requested;
  private boolean done;

  private BackupRoundtripRegression(ServerLevel level, String phase) {
    require(List.of("prepare", "reload").contains(phase), "Unknown backup roundtrip phase");
    server = level.getServer();
    this.phase = phase;
    world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    require(server.getPlayerList().getPlayerCount() == 0, "Disposable server without real players");
    for (ResourceKey<Level> dimension : DIMENSIONS) {
      ServerLevel target = level(dimension);
      plots(target).physicsSystem().setPaused(true);
      target.getChunk(4, 4);
      target.setChunkForced(4, 4, true);
    }
  }

  public static void start(ServerLevel level, String phase) {
    BackupRoundtripRegression fixture = new BackupRoundtripRegression(level, phase);
    NeoForge.EVENT_BUS.addListener(fixture::tick);
    NeoForge.EVENT_BUS.addListener(fixture::stop);
  }

  private void tick(ServerTickEvent.Post event) {
    if (done || event.getServer() != server) return;
    if (++ticks > 1200) throw new IllegalStateException("Backup roundtrip timed out: " + phase);
    try {
      if (phase.equals("prepare")) prepare();
      else reload();
      if (session != null) session.tick();
    } catch (IOException failure) {
      throw new IllegalStateException("Backup roundtrip " + phase, failure);
    }
  }

  private void prepare() throws IOException {
    if (ticks == 1) {
      require(!Files.exists(world.resolve(EXPECTED)), "Prepare requires a new disposable world");
      createShip(level(Level.OVERWORLD), 240);
      createShip(level(Level.NETHER), 96);
      session =
          TestShipPlayerSession.create(
              server.overworld(), PLAYER, "BackupPilot", new Vec3(75.5, 241, 75.5), null);
      session.player.setGameMode(GameType.CREATIVE);
      session.player.setNoGravity(true);
      session.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 17));
      return;
    }
    if (ticks == 20) {
      createChild(level(Level.OVERWORLD));
      return;
    }
    if (ticks == 40) {
      expected = new CompoundTag();
      expected.putInt("schema", 1);
      for (ResourceKey<Level> dimension : DIMENSIONS) {
        ServerLevel target = level(dimension);
        CompoundTag entry = shipState(ship(target));
        if (dimension == Level.OVERWORLD) {
          SwivelBearingBlockEntity bearing = bearing(target);
          require(bearing.getSubLevelID() != null, "Native child was assembled");
          ServerSubLevel child =
              (ServerSubLevel) plots(target).getSubLevel(bearing.getSubLevelID());
          require(child != null, "Native child is loaded");
          entry.put("child", shipState(child));
        }
        expected.put(dimension.location().toString(), entry);
      }
      expected.putUUID("player", PLAYER);
      expected.putDouble("player_x", session.player.getX());
      expected.putDouble("player_y", session.player.getY());
      expected.putDouble("player_z", session.player.getZ());
      saveFixturePlayer(session.player);
      verifyPlayerNbt();
      NbtIo.writeCompressed(expected, world.resolve(EXPECTED));
      before = archives();
      SimpleBackupsCoordinator.manual(server, true);
      requested = ticks;
      // manual() returns only after the consistent raw copy; prove later state stays outside it.
      Files.writeString(world.resolve(NEWER_FILE), "This must not exist in the restored backup.\n");
      for (ResourceKey<Level> dimension : DIMENSIONS) {
        ServerLevel target = level(dimension);
        barrel(target, groundBarrel(target)).setItem(0, new ItemStack(Items.IRON_INGOT));
        barrel(target, cargo(ship(target))).setItem(0, new ItemStack(Items.IRON_INGOT));
      }
      ServerSubLevel child =
          (ServerSubLevel)
              plots(server.overworld()).getSubLevel(bearing(server.overworld()).getSubLevelID());
      barrel(server.overworld(), cargo(child)).setItem(0, new ItemStack(Items.IRON_INGOT));
      session.player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 31));
      session.player.absMoveTo(76.5, 241, 75.5, 0, 0);
      saveFixturePlayer(session.player);
      server.saveEverything(true, true, true);
      return;
    }
    if (requested == 0 || ticks % 10 != 0) return;
    Set<Path> added = archives();
    added.removeAll(before);
    if (added.isEmpty()) return;
    require(added.size() == 1, "Exactly one new backup should publish");
    Path archive = added.iterator().next();
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      require(
          zip.getComment() != null
              && zip.getComment().startsWith("aeroscapes_reimagine:full-backup:1:"),
          "Managed archive marker");
      String root = world.toRealPath().getFileName() + "/";
      require(zip.getEntry(root + EXPECTED) != null, "Expected-state fixture captured");
      require(
          zip.getEntry(root + "playerdata/" + PLAYER + ".dat") != null,
          "Native playerdata captured");
      require(zip.getEntry(root + NEWER_FILE) == null, "Post-snapshot file leaked into archive");
      boolean liveCache = false;
      for (String directory : List.of("data/", "DIM-1/data/", "DIM1/data/")) {
        for (String suffix : List.of("", "-wal", "-shm", "-journal")) {
          String relative = directory + "DistantHorizons.sqlite" + suffix;
          liveCache |= Files.exists(world.resolve(relative));
          require(
              zip.getEntry(root + relative) == null,
              "Rebuildable asynchronous terrain cache is excluded: " + relative);
        }
      }
      require(liveCache, "Actual live terrain caches existed outside the archive");
    }
    require(Files.exists(world.resolve(NEWER_FILE)), "Live world retains newer state");
    done = true;
    LOGGER.info(
        "Backup roundtrip capture ready: archive={} newerLiveState=true", archive.toAbsolutePath());
  }

  private void reload() throws IOException {
    if (ticks == 1) {
      require(
          !Files.exists(world.resolve(NEWER_FILE)), "Restore must use a new extracted directory");
      expected = NbtIo.readCompressed(world.resolve(EXPECTED), NbtAccounter.create(1024 * 1024));
      require(
          expected.getInt("schema") == 1 && expected.getUUID("player").equals(PLAYER),
          "Fixture metadata");
    }
    if (ticks < 40 || DIMENSIONS.stream().anyMatch(key -> findShip(level(key)) == null)) return;
    for (ResourceKey<Level> dimension : DIMENSIONS) {
      ServerLevel target = level(dimension);
      CompoundTag entry = expected.getCompound(dimension.location().toString());
      verifyShip(ship(target), entry);
      ItemStack ground = barrel(target, groundBarrel(target)).getItem(0);
      require(
          ground.is(dimension == Level.OVERWORLD ? Items.DIAMOND : Items.EMERALD)
              && ground.getCount() == (dimension == Level.OVERWORLD ? 13 : 11),
          "Restored world inventory");
      ItemStack cargo = barrel(target, cargo(ship(target))).getItem(0);
      require(
          cargo.is(dimension == Level.OVERWORLD ? Items.GOLD_INGOT : Items.COPPER_INGOT)
              && cargo.getCount() == (dimension == Level.OVERWORLD ? 7 : 9),
          "Restored root ship cargo");
      if (dimension == Level.OVERWORLD) {
        UUID childId = entry.getCompound("child").getUUID("uuid");
        require(
            childId.equals(bearing(target).getSubLevelID()),
            "Bearing retains exact child identity");
        ServerSubLevel child = (ServerSubLevel) plots(target).getSubLevel(childId);
        if (child == null) return;
        verifyShip(child, entry.getCompound("child"));
        ItemStack contents = barrel(target, cargo(child)).getItem(0);
        require(
            contents.is(Items.NETHERITE_SCRAP) && contents.getCount() == 3, "Restored child cargo");
      }
    }
    verifyPlayerNbt();
    session =
        TestShipPlayerSession.create(
            server.overworld(), PLAYER, "BackupPilot", new Vec3(0, 250, 0), null);
    require(
        server.getPlayerList().load(session.player).isPresent(),
        "Native PlayerList load found playerdata");
    require(
        session.player.getUUID().equals(PLAYER)
            && session.player.serverLevel().dimension() == Level.OVERWORLD,
        "Restored player UUID/dimension");
    require(
        session
                .player
                .position()
                .distanceToSqr(
                    new Vec3(
                        expected.getDouble("player_x"),
                        expected.getDouble("player_y"),
                        expected.getDouble("player_z")))
            < 0.000001,
        "Restored player position");
    ItemStack held = session.player.getInventory().getItem(0);
    require(held.is(Items.DIAMOND) && held.getCount() == 17, "Native player inventory restored");
    session.player.setNoGravity(true);
    done = true;
    LOGGER.info(
        "Backup roundtrip restore passed: dimensions=2 roots=2 children=1 nativePlayerData=true newerSentinelAbsent=true");
  }

  private void createShip(ServerLevel target, int y) {
    for (int x = 64; x <= 70; x++)
      for (int z = 64; z <= 70; z++)
        for (int dy = 0; dy <= 5; dy++) {
          boolean shell = x == 64 || x == 70 || z == 64 || z == 70 || dy == 0 || dy == 5;
          target.setBlock(
              new BlockPos(x, y + dy, z),
              (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(),
              3);
        }
    target.setBlockAndUpdate(new BlockPos(75, y, 75), Blocks.STONE.defaultBlockState());
    target.setBlockAndUpdate(groundBarrel(target), Blocks.BARREL.defaultBlockState());
    barrel(target, groundBarrel(target))
        .setItem(
            0,
            new ItemStack(
                target.dimension() == Level.OVERWORLD ? Items.DIAMOND : Items.EMERALD,
                target.dimension() == Level.OVERWORLD ? 13 : 11));
    BlockPos initialCargo = new BlockPos(66, y + 1, 66);
    target.setBlockAndUpdate(initialCargo, Blocks.BARREL.defaultBlockState());
    barrel(target, initialCargo)
        .setItem(
            0,
            new ItemStack(
                target.dimension() == Level.OVERWORLD ? Items.GOLD_INGOT : Items.COPPER_INGOT,
                target.dimension() == Level.OVERWORLD ? 7 : 9));
    ServerSubLevelContainer container = plots(target);
    List<ServerSubLevel> previous = List.copyOf(container.getAllSubLevels());
    server
        .getCommands()
        .performPrefixedCommand(
            server.createCommandSourceStack().withLevel(target),
            "sable assemble area 64 " + y + " 64 70 " + (y + 5) + " 70");
    List<ServerSubLevel> added =
        container.getAllSubLevels().stream().filter(body -> !previous.contains(body)).toList();
    require(added.size() == 1, "Small native area assembly produced exactly one ship");
    ServerSubLevel ship = added.getFirst();
    ship.setName(name(target));
    container.addForceLoadTicket(ship, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    var bounds = ship.getPlot().getBoundingBox();
    require(bounds != null, "Assembled ship bounds");
    CompoundTag data = new CompoundTag();
    data.putLong(
        CARGO, new BlockPos(bounds.minX() + 2, bounds.minY() + 1, bounds.minZ() + 2).asLong());
    ship.setUserDataTag(data);
  }

  private void createChild(ServerLevel target) {
    ServerSubLevel root = ship(target);
    var box = root.getPlot().getBoundingBox();
    require(box != null, "Root plot bounds");
    BlockPos position = new BlockPos(box.minX() + 3, box.minY() + 1, box.minZ() + 3);
    var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simulated:swivel_bearing"));
    target.setBlockAndUpdate(
        position, block.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    target.setBlockAndUpdate(position.above(), Blocks.SPRUCE_PLANKS.defaultBlockState());
    target.setBlockAndUpdate(position.above(2), Blocks.BARREL.defaultBlockState());
    barrel(target, position.above(2)).setItem(0, new ItemStack(Items.NETHERITE_SCRAP, 3));
    require(
        target.addFreshEntity(
            new SuperGlueEntity(target, SuperGlueEntity.span(position.above(), position.above(2)))),
        "Native glue binds the child plank and cargo barrel");
    root.getUserDataTag().putLong(BEARING, position.asLong());
    SwivelBearingBlockEntity bearing = (SwivelBearingBlockEntity) target.getBlockEntity(position);
    require(bearing != null, "Native swivel bearing block entity");
    bearing.assemble();
    require(bearing.getSubLevelID() != null, "Native bearing assembled child");
    ServerSubLevel child = (ServerSubLevel) plots(target).getSubLevel(bearing.getSubLevelID());
    require(child != null, "Child plot was allocated");
    child.setName("Aeroscapes backup roundtrip child");
    // The root ticket loads this dependency too; separate child tickets duplicate native loads.
    BlockPos plate = bearing.getPlatePos();
    require(plate != null, "Native bearing plate address");
    BlockPos childCargo = plate.above(2);
    require(
        Sable.HELPER.getContaining(target, childCargo) == child, "Cargo belongs to bearing child");
    ItemStack contents = barrel(target, childCargo).getItem(0);
    require(
        contents.is(Items.NETHERITE_SCRAP) && contents.getCount() == 3,
        "Native assembly retained child cargo");
    CompoundTag childData = new CompoundTag();
    childData.putLong(CARGO, childCargo.asLong());
    child.setUserDataTag(childData);
  }

  private static CompoundTag shipState(ServerSubLevel body) {
    barrel(body.getLevel(), cargo(body));
    CompoundTag tag = new CompoundTag();
    tag.putUUID("uuid", body.getUniqueId());
    tag.putString("name", body.getName());
    tag.putLong("cargo", cargo(body).asLong());
    tag.putDouble("x", body.logicalPose().position().x());
    tag.putDouble("y", body.logicalPose().position().y());
    tag.putDouble("z", body.logicalPose().position().z());
    return tag;
  }

  private static void verifyShip(ServerSubLevel body, CompoundTag expected) {
    require(
        body.getUniqueId().equals(expected.getUUID("uuid"))
            && body.getName().equals(expected.getString("name")),
        "Restored ship identity/name");
    require(cargo(body).asLong() == expected.getLong("cargo"), "Restored plot cargo address");
    require(
        Math.abs(body.logicalPose().position().x() - expected.getDouble("x")) < 0.00001
            && Math.abs(body.logicalPose().position().y() - expected.getDouble("y")) < 0.00001
            && Math.abs(body.logicalPose().position().z() - expected.getDouble("z")) < 0.00001,
        "Restored ship world position");
  }

  private void verifyPlayerNbt() throws IOException {
    CompoundTag saved =
        NbtIo.readCompressed(
            world.resolve("playerdata").resolve(PLAYER + ".dat"), NbtAccounter.create(1024 * 1024));
    require(
        saved.hasUUID("UUID") && saved.getUUID("UUID").equals(PLAYER),
        "Actual native playerdata UUID");
    require(
        saved.getString("Dimension").equals("minecraft:overworld"), "Actual playerdata dimension");
    var position = saved.getList("Pos", 6);
    require(
        position.size() == 3
            && Math.abs(position.getDouble(0) - expected.getDouble("player_x")) < 0.00001
            && Math.abs(position.getDouble(1) - expected.getDouble("player_y")) < 0.00001
            && Math.abs(position.getDouble(2) - expected.getDouble("player_z")) < 0.00001,
        "Actual native playerdata position");
  }

  private static void saveFixturePlayer(ServerPlayer player) {
    try {
      var save = PlayerList.class.getDeclaredMethod("save", ServerPlayer.class);
      require(
          save.trySetAccessible(), "Native player save is accessible to the regression fixture");
      save.invoke(player.server.getPlayerList(), player);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
      throw new IllegalStateException(
          "Cannot manually save the existing fixture through native PlayerList", failure);
    }
  }

  private Set<Path> archives() throws IOException {
    Path output = CommonConfig.getOutputPath(null);
    if (!Files.exists(output)) return new java.util.HashSet<>();
    try (var paths = Files.list(output)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(
              path ->
                  path.getFileName().toString().startsWith("aeroscapes-")
                      && path.getFileName().toString().endsWith(".zip"))
          .collect(Collectors.toSet());
    }
  }

  private ServerLevel level(ResourceKey<Level> key) {
    ServerLevel level = server.getLevel(key);
    require(level != null, "Required dimension " + key.location());
    return level;
  }

  private static ServerSubLevelContainer plots(ServerLevel level) {
    ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
    require(container != null, "Sable container exists");
    return container;
  }

  private static String name(ServerLevel level) {
    return "Aeroscapes backup roundtrip " + level.dimension().location();
  }

  private static ServerSubLevel findShip(ServerLevel level) {
    return plots(level).getAllSubLevels().stream()
        .filter(ship -> name(level).equals(ship.getName()))
        .findFirst()
        .orElse(null);
  }

  private static ServerSubLevel ship(ServerLevel level) {
    ServerSubLevel ship = findShip(level);
    require(ship != null, "Required backup ship " + level.dimension().location());
    return ship;
  }

  private static BlockPos cargo(ServerSubLevel ship) {
    CompoundTag data = ship.getUserDataTag();
    require(data != null && data.contains(CARGO), "Saved ship cargo reference");
    return BlockPos.of(data.getLong(CARGO));
  }

  private static BlockPos groundBarrel(ServerLevel level) {
    return new BlockPos(73, level.dimension() == Level.OVERWORLD ? 241 : 97, 73);
  }

  private static BarrelBlockEntity barrel(ServerLevel level, BlockPos position) {
    require(level.getBlockEntity(position) instanceof BarrelBlockEntity, "Barrel at " + position);
    return (BarrelBlockEntity) level.getBlockEntity(position);
  }

  private static SwivelBearingBlockEntity bearing(ServerLevel level) {
    CompoundTag data = ship(level).getUserDataTag();
    require(data != null && data.contains(BEARING), "Root bearing reference");
    var entity = level.getBlockEntity(BlockPos.of(data.getLong(BEARING)));
    require(entity instanceof SwivelBearingBlockEntity, "Restored native bearing block entity");
    return (SwivelBearingBlockEntity) entity;
  }

  private void stop(ServerStoppingEvent event) {
    if (event.getServer() == server && session != null) session.close();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
