package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Opt-in disposable-world test of saved Sable plot and Create auxiliary lighting. */
public final class SableLightingRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String SHIP_NAME = "Aeroscapes lighting regression";
  private static final String SOURCE_KEY = "aeroscapes_light_source";
  private final String phase;
  private int ticks;
  private int installed;
  private boolean done;

  private SableLightingRegression(String phase) {
    if (!List.of("prepare", "reload", "extinguish", "reload-dark").contains(phase)) {
      throw new IllegalArgumentException("Unknown Sable lighting phase " + phase);
    }
    this.phase = phase;
  }

  public static void start(String phase) {
    NeoForge.EVENT_BUS.addListener(new SableLightingRegression(phase)::tick);
  }

  private void tick(ServerTickEvent.Post event) {
    if (done) return;
    ticks++;
    MinecraftServer server = event.getServer();
    if (ticks == 5 && phase.equals("prepare")) {
      createShip(server, requireLevel(server, Level.OVERWORLD), 240);
      createShip(server, requireLevel(server, Level.NETHER), 100);
    }
    if (ticks == 165 && phase.equals("prepare")) {
      install(requireLevel(server, Level.OVERWORLD));
      install(requireLevel(server, Level.NETHER));
      installed = ticks;
    }
    if (ticks == 5 && phase.equals("extinguish")) {
      extinguish(requireLevel(server, Level.OVERWORLD));
      extinguish(requireLevel(server, Level.NETHER));
    }
    if ((phase.equals("prepare") && installed > 0 && ticks >= installed + 120)
        || (!phase.equals("prepare") && ticks >= 125)) {
      boolean dark = phase.equals("extinguish") || phase.equals("reload-dark");
      verify(requireLevel(server, Level.OVERWORLD), dark);
      verify(requireLevel(server, Level.NETHER), dark);
      done = true;
      LOGGER.info("Sable lighting regression passed: phase={}", phase);
    }
  }

  private static ServerLevel requireLevel(
      MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension) {
    ServerLevel level = server.getLevel(dimension);
    if (level == null) throw new IllegalStateException("Missing dimension " + dimension.location());
    return level;
  }

  private static ServerSubLevelContainer container(ServerLevel level) {
    ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
    if (container == null) throw new IllegalStateException("Missing Sable container");
    return container;
  }

  private static ServerSubLevel ship(ServerLevel level) {
    return container(level).getAllSubLevels().stream()
        .filter(ship -> SHIP_NAME.equals(ship.getName()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Lighting test ship missing in " + level.dimension().location()));
  }

  private static void createShip(MinecraftServer server, ServerLevel level, int y) {
    level.setChunkForced(4, 4, true);
    for (int x = 64; x <= 70; x++) {
      for (int z = 64; z <= 70; z++) {
        for (int dy = 0; dy <= 5; dy++) {
          boolean shell = x == 64 || x == 70 || z == 64 || z == 70 || dy == 0 || dy == 5;
          level.setBlock(
              new BlockPos(x, y + dy, z),
              (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(),
              3);
        }
      }
    }
    level.setBlock(new BlockPos(65, y + 2, 66), Blocks.GLOWSTONE.defaultBlockState(), 3);
    ServerSubLevelContainer plots = container(level);
    List<ServerSubLevel> before = List.copyOf(plots.getAllSubLevels());
    server
        .getCommands()
        .performPrefixedCommand(
            server.createCommandSourceStack().withLevel(level),
            "sable assemble area 64 " + y + " 64 70 " + (y + 5) + " 70");
    List<ServerSubLevel> added =
        plots.getAllSubLevels().stream().filter(ship -> !before.contains(ship)).toList();
    if (added.size() != 1) throw new IllegalStateException("Expected one assembled test ship");
    ServerSubLevel ship = added.getFirst();
    ship.setName(SHIP_NAME);
    plots.addForceLoadTicket(ship, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    var box = ship.getPlot().getBoundingBox();
    if (box == null) throw new IllegalStateException("Test ship has no bounds");
    BlockPos source = new BlockPos(box.minX() + 1, box.minY() + 2, box.minZ() + 2);
    CompoundTag data = ship.getUserDataTag();
    if (data == null) data = new CompoundTag();
    data.putLong(SOURCE_KEY, source.asLong());
    ship.setUserDataTag(data);
  }

  private static BlockPos source(ServerSubLevel ship) {
    CompoundTag data = ship.getUserDataTag();
    if (data == null || !data.contains(SOURCE_KEY))
      throw new IllegalStateException("Missing source position");
    return BlockPos.of(data.getLong(SOURCE_KEY));
  }

  private static BlockPos auxPos(BlockPos source, boolean alongX) {
    ChunkPos chunk = new ChunkPos(source);
    return alongX
        ? new BlockPos(chunk.getMaxBlockX() + 13, source.getY(), source.getZ())
        : new BlockPos(source.getX(), source.getY(), chunk.getMaxBlockZ() + 13);
  }

  private static void install(ServerLevel level) {
    BlockPos source = source(ship(level));
    level.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
    level.setBlock(source, Blocks.GLOWSTONE.defaultBlockState(), 3);
    for (boolean alongX : new boolean[] {true, false}) {
      BlockPos target = auxPos(source, alongX);
      if (alongX) {
        for (int x = source.getX() + 6; x < target.getX(); x++)
          level.setBlock(
              new BlockPos(x, source.getY(), source.getZ()), Blocks.STONE.defaultBlockState(), 3);
      } else {
        for (int z = source.getZ() + 5; z < target.getZ(); z++)
          level.setBlock(
              new BlockPos(source.getX(), source.getY(), z), Blocks.STONE.defaultBlockState(), 3);
      }
      var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:copycat_step"));
      level.setBlock(target, block.defaultBlockState(), 3);
      setCopycatMaterial(level, target, Blocks.GLOWSTONE.defaultBlockState());
    }
  }

  private static void extinguish(ServerLevel level) {
    BlockPos source = source(ship(level));
    for (boolean alongX : new boolean[] {true, false}) {
      setCopycatMaterial(level, auxPos(source, alongX), Blocks.STONE.defaultBlockState());
    }
  }

  private static void setCopycatMaterial(ServerLevel level, BlockPos pos, BlockState material) {
    BlockEntity entity = level.getBlockEntity(pos);
    if (entity == null) throw new IllegalStateException("Copycat block entity missing at " + pos);
    try {
      entity
          .getClass()
          .getMethod("setConsumedItem", ItemStack.class)
          .invoke(entity, new ItemStack(material.getBlock()));
      entity.getClass().getMethod("setMaterial", BlockState.class).invoke(entity, material);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException error) {
      throw new IllegalStateException("Cannot configure the pinned Create copycat", error);
    }
  }

  private static void verify(ServerLevel level, boolean dark) {
    ServerSubLevel ship = ship(level);
    ServerLevelPlot plot = ship.getPlot();
    BlockPos source = source(ship);
    var blockLight = plot.getLightEngine().getLayerListener(LightLayer.BLOCK);
    var skyLight = plot.getLightEngine().getLayerListener(LightLayer.SKY);
    if (!level.getBlockState(source).is(Blocks.GLOWSTONE)
        || blockLight.getLightValue(source) != 15
        || blockLight.getLightValue(source.east()) != 14
        || skyLight.getLightValue(source.east()) != 0) {
      throw new IllegalStateException("Incorrect ship light in " + level.dimension().location());
    }
    CompoundTag saved = plot.save();
    for (boolean alongX : new boolean[] {true, false}) {
      BlockPos pos = auxPos(source, alongX);
      var aux = level.getAuxLightManager(pos);
      int expected = dark ? 0 : 15;
      if (aux == null
          || aux.getLightAt(pos) != expected
          || blockLight.getLightValue(pos) != expected
          || blockLight.getLightValue(pos.above()) != (dark ? 0 : 14)) {
        throw new IllegalStateException("Incorrect Create auxiliary light at " + pos);
      }
      CompoundTag chunk =
          saved
              .getCompound("chunks")
              .getCompound(String.valueOf(plot.toLocal(new ChunkPos(pos)).toLong()));
      var entries = chunk.getList("neoforge:aux_lights", 10);
      boolean found = false;
      for (int i = 0; i < entries.size(); i++) {
        CompoundTag entry = entries.getCompound(i);
        if (entry.getLong("pos") == pos.asLong() && entry.getByte("level") == 15) found = true;
      }
      if (found == dark)
        throw new IllegalStateException("Incorrect saved auxiliary light at " + pos);
    }
  }
}
