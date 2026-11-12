package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.jesz.createdieselgenerators.CDGBlocks;
import com.jesz.createdieselgenerators.content.pumpjack.PumpjackHoleBlockEntity;
import com.jesz.createdieselgenerators.world.OilChunksSavedData;
import com.simibubi.create.AllBlocks;
import dev.aeroscapes.engineering.OilFields;
import dev.aeroscapes.engineering.OilRockBlock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Real void, native Nether pipe targets and copied pre-oil FULL chunks. */
public final class OilControlsRegression {
  private OilControlsRegression() {}

  public static void verify(ServerLevel overworld, Path reportFile) {
    require(overworld.getSeed() == 1, "Oil controls require host seed1");
    JsonObject report = new JsonObject();
    ChunkPos voidChunk = voidChunk(overworld);
    require(
        OilFields.findTarget(overworld, voidChunk) == null, "Natural FULL void has no oil marker");
    require(
        OilChunksSavedData.getChunkOilAmount(overworld, voidChunk) == 0,
        "An eligible empty chunk has no abstract oil reserve");
    BlockPos marker = voidChunk.getMiddleBlockPosition(64);
    try {
      var rock = OilFields.OIL_BEARING_ROCK.get().defaultBlockState();
      overworld.setBlockAndUpdate(marker, rock);
      require(
          !PistonBaseBlock.isPushable(
              rock, overworld, marker, Direction.EAST, false, Direction.EAST),
          "Native piston cannot relocate the fixed marker");
    } finally {
      overworld.setBlockAndUpdate(marker, Blocks.AIR.defaultBlockState());
    }
    mineableTargets(overworld, marker);
    report.addProperty("mineable_host_variants", OilRockBlock.Host.values().length);
    report.addProperty("last_target_removal_empties_reserve", true);
    report.addProperty("natural_void_x", voidChunk.x);
    report.addProperty("natural_void_z", voidChunk.z);
    ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
    ServerLevel end = overworld.getServer().getLevel(Level.END);
    require(nether != null && end != null, "Native secondary dimensions exist");
    report.add("nether", netherWell(nether));
    require(
        OilChunksSavedData.getChunkOilAmount(end, new ChunkPos(0, 0)) == 0,
        "End initializes without oil");
    report.addProperty("end_oil_mB", 0);
    try {
      Files.writeString(
          reportFile,
          new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n",
          StandardOpenOption.CREATE_NEW);
    } catch (IOException failure) {
      throw new IllegalStateException("Could not write oil controls report", failure);
    }
    com.mojang.logging.LogUtils.getLogger().info("Oil dimension and void controls passed");
  }

  public static void verifyReload(ServerLevel level, Path reportFile) {
    JsonObject report;
    try (var reader = Files.newBufferedReader(reportFile)) {
      report = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
    } catch (IOException failure) {
      throw new IllegalStateException("Could not read oil controls report", failure);
    }
    ChunkPos chunk =
        new ChunkPos(
            report.get("natural_void_x").getAsInt(), report.get("natural_void_z").getAsInt());
    var saved = OilChunksSavedData.load(level).save(new CompoundTag(), level.registryAccess());
    boolean storedZero = false;
    for (Tag entry : saved.getList("OilChunks", Tag.TAG_COMPOUND)) {
      var field = (CompoundTag) entry;
      if (field.getInt("x") == chunk.x && field.getInt("z") == chunk.z)
        storedZero = field.getInt("Amountmb") == 0;
    }
    require(storedZero, "Last-target removal persists explicit zero before any scanner query");
    require(
        OilFields.findTarget(level, chunk) == null
            && OilChunksSavedData.getChunkOilAmount(level, chunk) == 0,
        "Removed targets do not restore a reserve after save/reload");
    com.mojang.logging.LogUtils.getLogger().info("Mined oil target persistence passed");
  }

  private static void mineableTargets(ServerLevel level, BlockPos position) {
    for (var block : BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.BASE_STONE_OVERWORLD))
      require(
          OilRockBlock.Host.of(block.value().defaultBlockState()) != null,
          "Every admitted native stone has an oil host texture: " + block.getRegisteredName());
    var player = FakePlayerFactory.getMinecraft(level);
    player.setGameMode(GameType.SURVIVAL);
    player.setPos(position.getX(), position.getY(), position.getZ());
    var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    for (var host : OilRockBlock.Host.values()) {
      require(
          host.nativeState().is(BlockTags.BASE_STONE_OVERWORLD),
          "Every oil host resolves to a registered, admitted native stone: " + host);
      var rock =
          OilFields.OIL_BEARING_ROCK.get().defaultBlockState().setValue(OilRockBlock.HOST, host);
      require(rock.getBlock().asItem() == Items.AIR, "Markers have no obtainable BlockItem");
      require(
          rock.is(BlockTags.MINEABLE_WITH_PICKAXE), "Native pickaxe handling recognizes oil rock");
      require(
          !new AssemblyProbe().accepts(rock, level, position),
          "The native Simulated assembler rejects a mineable oil target");
      for (String tag :
          new String[] {
            "create:non_movable", "simulated:non_movable", "neoforge:relocation_not_supported"
          })
        require(
            rock.is(TagKey.create(Registries.BLOCK, ResourceLocation.parse(tag))),
            "Mineable oil targets retain the native relocation barrier: " + tag);
      for (int mode = 0; mode < 3; mode++) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        if (mode == 1) tool.enchant(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        if (mode == 2) tool.enchant(enchantments.getHolderOrThrow(Enchantments.FORTUNE), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        require(
            rock.getDestroyProgress(player, level, position)
                == host.nativeState().getDestroyProgress(player, level, position),
            "Manual mining retains the native host's pickaxe progress");
        require(
            Block.getDrops(rock, level, position, null, player, tool).isEmpty(),
            "Normal, Silk Touch and Fortune mining never yield oil-bearing rock");
        level.setBlockAndUpdate(position, rock);
        require(
            player.gameMode.destroyBlock(position) && level.getBlockState(position).isAir(),
            "A survival player can remove each host variant");
      }
    }
    BlockPos second = position.east();
    var rock = OilFields.OIL_BEARING_ROCK.get().defaultBlockState();
    level.setBlockAndUpdate(position, rock);
    level.setBlockAndUpdate(second, rock);
    ChunkPos chunk = new ChunkPos(position);
    OilChunksSavedData.setChunkOilAmount(level, chunk, 7319);
    require(player.gameMode.destroyBlock(position), "The first shared target can be mined");
    require(
        OilChunksSavedData.getChunkOilAmount(level, chunk) == 7319,
        "Removing one target preserves the shared reserve while another remains");
    BlockPos pumpPosition = second.above(3);
    var pipe =
        AllBlocks.GLASS_FLUID_PIPE
            .getDefaultState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    level.setBlockAndUpdate(second.above(), pipe);
    level.setBlockAndUpdate(second.above(2), pipe);
    level.setBlockAndUpdate(pumpPosition, CDGBlocks.PUMPJACK_HOLE.getDefaultState());
    var pump = (PumpjackHoleBlockEntity) level.getBlockEntity(pumpPosition);
    require(pump != null, "Loaded well remains above the removable target");
    for (int tick = 0; tick < 20; tick++) pump.tick();
    pump.headPos = 1;
    pump.bearingPos = 1;
    pump.pumpjackRotation(false);
    var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pumpPosition, null);
    require(
        tank != null && tank.getFluidInTank(0).getAmount() == 1000,
        "Well extracts before its final marker is mined");
    require(
        player.gameMode.destroyBlock(second), "The last target can be mined below a loaded well");
    for (int tick = 0; tick < 20; tick++) pump.tick();
    pump.pumpjackRotation(false);
    require(
        pump.pipeLength == 0
            && tank.getFluidInTank(0).getAmount() == 1000
            && OilChunksSavedData.getChunkOilAmount(level, chunk) == 0,
        "Loaded well stops extraction cleanly after the final target is mined");
    level.setBlockAndUpdate(pumpPosition, Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(second.above(), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(second.above(2), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(second, rock);
    OilChunksSavedData.setChunkOilAmount(level, chunk, 411);
    level.explode(
        null,
        second.getX() + .5,
        second.getY() + .5,
        second.getZ() + .5,
        4,
        false,
        Level.ExplosionInteraction.BLOCK);
    require(level.getBlockState(second).isAir(), "An explosion can remove the last target");
    require(
        OilChunksSavedData.getChunkOilAmount(level, chunk) == 0,
        "Removing the final target retires the reserve rather than announcing phantom oil");
    require(OilFields.findTarget(level, chunk) == null, "No physical target remains");
  }

  public static void verifyOldFull(ServerLevel level) {
    ChunkPos old = new ChunkPos(8, 41);
    require(
        level.getChunkSource().chunkMap.read(old).join().isPresent(),
        "The old-pack control must already exist on disk");
    require(
        OilFields.findTarget(level, old) == null,
        "Pre-oil saved FULL chunk receives no retroactive markers");
    require(
        OilChunksSavedData.getChunkOilAmount(level, old) == 0,
        "Pre-oil saved FULL chunk receives no phantom reserve");
    com.mojang.logging.LogUtils.getLogger().info("Pre-oil FULL chunk control passed");
  }

  private static ChunkPos voidChunk(ServerLevel level) {
    var generator = level.getChunkSource().getGenerator();
    for (int index = 0; index < 256; index++) {
      ChunkPos candidate = new ChunkPos(64 + index * 11, -64 + index * 5);
      if (OilFields.random(level.getSeed(), candidate).nextInt(8) != 0) continue;
      int height =
          generator.getBaseHeight(
              candidate.getMiddleBlockX(),
              candidate.getMiddleBlockZ(),
              Heightmap.Types.WORLD_SURFACE_WG,
              level,
              level.getChunkSource().randomState());
      if (height > level.getMinBuildHeight()) continue;
      var full = level.getChunk(candidate.x, candidate.z);
      if (Arrays.stream(full.getSections()).allMatch(section -> section.hasOnlyAir()))
        return candidate;
    }
    throw new IllegalStateException("No natural FULL void control found");
  }

  private static JsonObject netherWell(ServerLevel level) {
    ChunkPos field = null;
    for (int index = 0; index < 64; index++) {
      ChunkPos candidate = new ChunkPos(index, 0);
      if (OilFields.random(level.getSeed(), candidate).nextInt(8) == 0) {
        field = candidate;
        break;
      }
    }
    require(field != null, "Finite Nether field candidate exists");
    int initial = OilChunksSavedData.getChunkOilAmount(level, field);
    require(
        initial >= OilFields.MINIMUM_AMOUNT && initial <= OilFields.MAXIMUM_AMOUNT,
        "Native Nether field has finite initial volume");
    BlockPos target = field.getMiddleBlockPosition(level.getMinBuildHeight());
    for (int y = level.getMinBuildHeight(); y < level.getMinBuildHeight() + 8; y++) {
      BlockPos candidate = new BlockPos(target.getX(), y, target.getZ());
      if (level.getBlockState(candidate).is(Blocks.BEDROCK)) target = candidate;
    }
    require(level.getBlockState(target).is(Blocks.BEDROCK), "Natural lower bedrock target exists");
    BlockPos firstPipe = target.above();
    BlockPos secondPipe = target.above(2);
    BlockPos pumpPosition = target.above(3);
    BlockState firstBefore = level.getBlockState(firstPipe);
    BlockState secondBefore = level.getBlockState(secondPipe);
    BlockState pumpBefore = level.getBlockState(pumpPosition);
    try {
      var pipe =
          AllBlocks.GLASS_FLUID_PIPE
              .getDefaultState()
              .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
      level.setBlockAndUpdate(firstPipe, pipe);
      level.setBlockAndUpdate(secondPipe, pipe);
      level.setBlockAndUpdate(pumpPosition, CDGBlocks.PUMPJACK_HOLE.getDefaultState());
      var pump = (PumpjackHoleBlockEntity) level.getBlockEntity(pumpPosition);
      require(pump != null, "Native Nether pump exists");
      for (int tick = 0; tick < 20; tick++) pump.tick();
      require(pump.pipeLength == 3, "Real vertical pipe reaches native bedrock");
      pump.headPos = 1;
      pump.bearingPos = 1;
      pump.pumpjackRotation(false);
      var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pumpPosition, null);
      require(
          tank != null && tank.getFluidInTank(0).getAmount() == 1000,
          "Nether well extracts actual oil");
      require(
          OilChunksSavedData.getChunkOilAmount(level, field) == initial - 1000,
          "Native bedrock well debits the finite shared volume");
      level.setBlockAndUpdate(secondPipe, Blocks.STONE.defaultBlockState());
      for (int tick = 0; tick < 20; tick++) pump.tick();
      pump.pumpjackRotation(false);
      require(
          pump.pipeLength == 0
              && tank.getFluidInTank(0).getAmount() == 1000
              && OilChunksSavedData.getChunkOilAmount(level, field) == initial - 1000,
          "Interrupted vertical pipe cannot extract or debit oil");
      JsonObject result = new JsonObject();
      result.addProperty("x", field.x);
      result.addProperty("z", field.z);
      result.addProperty("initial_mB", initial);
      result.addProperty("remaining_mB", initial - 1000);
      result.addProperty("bedrock_y", target.getY());
      return result;
    } finally {
      level.setBlockAndUpdate(pumpPosition, pumpBefore);
      level.setBlockAndUpdate(secondPipe, secondBefore);
      level.setBlockAndUpdate(firstPipe, firstBefore);
    }
  }

  private static final class AssemblyProbe
      extends dev.simulated_team.simulated.util.assembly.SimAssemblyContraption {
    private AssemblyProbe() {
      super(BlockPos.ZERO, false);
    }

    private boolean accepts(BlockState state, ServerLevel level, BlockPos position) {
      return movementAllowed(state, level, position);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
