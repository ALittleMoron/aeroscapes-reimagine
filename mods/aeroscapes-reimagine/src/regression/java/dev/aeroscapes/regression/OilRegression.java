package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jesz.createdieselgenerators.CDGBlocks;
import com.jesz.createdieselgenerators.CDGDataComponents;
import com.jesz.createdieselgenerators.CDGItems;
import com.jesz.createdieselgenerators.content.pumpjack.PumpjackHoleBlockEntity;
import com.jesz.createdieselgenerators.world.OilChunksSavedData;
import dev.aeroscapes.engineering.OilFields;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Natural FULL witnesses plus the actual provider's shared pump and saved-data boundary. */
public final class OilRegression {
  private OilRegression() {}

  public static void verify(ServerLevel level, Path reportFile, boolean reload) {
    require(level.getServer().isSameThread(), "Oil fixture must run on the server thread");
    try {
      if (reload) {
        JsonObject previous =
            JsonParser.parseString(Files.readString(reportFile)).getAsJsonObject();
        for (var element : previous.getAsJsonArray("fields")) {
          JsonObject field = element.getAsJsonObject();
          ChunkPos position = new ChunkPos(field.get("x").getAsInt(), field.get("z").getAsInt());
          require(OilFields.findTarget(level, position) != null, "Physical target survives reload");
          require(
              OilChunksSavedData.getChunkOilAmount(level, position)
                  == field.get("remaining_mB").getAsInt(),
              "Exact partial or exhausted reserve must survive restart");
        }
      } else {
        JsonObject report = new JsonObject();
        report.addProperty("seed", level.getSeed());
        report.addProperty("minimum_mB", OilFields.MINIMUM_AMOUNT);
        report.addProperty("maximum_mB", OilFields.MAXIMUM_AMOUNT);
        JsonArray fields = new JsonArray();
        ChunkPos spawn = new ChunkPos(level.getSharedSpawnPos());
        ChunkPos markerless = markerless(level, spawn);
        scanner(level, markerless, 0);
        int examined = 0;
        int empty = 0;
        for (int radius = 0; radius < 24 && fields.size() < 3; radius++) {
          for (int offsetX = -radius; offsetX <= radius && fields.size() < 3; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius && fields.size() < 3; offsetZ++) {
              if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) continue;
              ChunkPos position = new ChunkPos(spawn.x + offsetX, spawn.z + offsetZ);
              if (OilFields.random(level.getSeed(), position).nextInt(8) != 0) continue;
              examined++;
              BlockPos target = OilFields.findTarget(level, position);
              int amount = OilChunksSavedData.getChunkOilAmount(level, position);
              if (target == null) {
                empty++;
                require(
                    amount == 0, "Scanner must not announce oil without a natural physical target");
                continue;
              }
              require(
                  amount >= OilFields.MINIMUM_AMOUNT && amount <= OilFields.MAXIMUM_AMOUNT,
                  "Finite natural field budget");
              require(
                  amount == OilFields.amount(level.getSeed(), position),
                  "Deterministic initial field budget");
              var targets = targets(level, position);
              require(targets.size() >= 2, "Natural field supports two separate wells");
              require(
                  targets.stream().anyMatch(marker -> level.getBlockState(marker.below()).isAir()),
                  "Natural underside has a visible oil marker");
              scanner(level, position, amount);
              movingScanner(level, position, markerless, 0);
              movingScanner(level, markerless, position, amount);
              boolean exhaust = fields.size() < 2;
              pumps(level, position, targets.get(0), targets.get(1), amount, exhaust);
              int remaining = exhaust ? 0 : amount - 9000;
              scanner(level, position, remaining);
              JsonObject field = new JsonObject();
              field.addProperty("x", position.x);
              field.addProperty("z", position.z);
              field.addProperty("initial_mB", amount);
              field.addProperty("remaining_mB", remaining);
              field.addProperty("target_x", target.getX());
              field.addProperty("target_y", target.getY());
              field.addProperty("target_z", target.getZ());
              field.addProperty("marker_count", targets.size());
              fields.add(field);
            }
          }
        }
        require(fields.size() == 3, "Three independent natural oil fields required");
        report.add("fields", fields);
        report.addProperty("eligible_chunks_examined", examined);
        report.addProperty("no_target_chunks", empty);
        report.addProperty("shared_wells_conserved_and_exhausted", true);
        Files.createDirectories(reportFile.getParent());
        Files.writeString(
            reportFile,
            new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n",
            StandardOpenOption.CREATE_NEW);
      }
    } catch (IOException failure) {
      throw new IllegalStateException("Oil regression report failed", failure);
    }
    com.mojang.logging.LogUtils.getLogger()
        .info("Finite oil regression passed: seed={} reload={}", level.getSeed(), reload);
  }

  private static ChunkPos markerless(ServerLevel level, ChunkPos start) {
    for (int offset = 0; offset < 64; offset++) {
      ChunkPos candidate = new ChunkPos(start.x + offset, start.z);
      if (OilFields.random(level.getSeed(), candidate).nextInt(8) == 0) continue;
      require(
          OilFields.findTarget(level, candidate) == null,
          "Ineligible FULL chunk cannot receive neighboring oil markers");
      require(
          OilChunksSavedData.getChunkOilAmount(level, candidate) == 0,
          "Markerless FULL chunk has no reserve");
      return candidate;
    }
    throw new IllegalStateException("No markerless control found");
  }

  private static void movingScanner(ServerLevel level, ChunkPos from, ChunkPos to, int expected) {
    var player = FakePlayerFactory.getMinecraft(level);
    var scanner = CDGItems.OIL_SCANNER.asStack();
    player.setItemInHand(InteractionHand.MAIN_HAND, scanner);
    player.setPos(from.getMiddleBlockX(), 200, from.getMiddleBlockZ());
    scanner.getItem().use(level, player, InteractionHand.MAIN_HAND);
    for (int tick = 0; tick < 20; tick++)
      scanner.getItem().inventoryTick(scanner, level, player, 0, true);
    player.setPos(to.getMiddleBlockX(), 200, to.getMiddleBlockZ());
    scanner.getItem().inventoryTick(scanner, level, player, 0, true);
    require(
        scanner.getOrDefault(CDGDataComponents.OIL_SCANNER_STATE, -1)
            == (expected == 0 ? 1 : expected >= 72_000 ? 3 : 2),
        "Completed native scan observes destination chunk, not starting chunk");
  }

  private static List<BlockPos> targets(ServerLevel level, ChunkPos chunk) {
    List<BlockPos> targets = new ArrayList<>();
    for (BlockPos position :
        BlockPos.betweenClosed(
            chunk.getMinBlockX(),
            level.getMinBuildHeight(),
            chunk.getMinBlockZ(),
            chunk.getMaxBlockX(),
            level.getMaxBuildHeight() - 1,
            chunk.getMaxBlockZ())) {
      if (level.getBlockState(position).is(OilFields.OIL_BEARING_ROCK.get()))
        targets.add(position.immutable());
    }
    return targets;
  }

  private static void scanner(ServerLevel level, ChunkPos chunk, int expected) {
    var player = FakePlayerFactory.getMinecraft(level);
    player.setPos(chunk.getMiddleBlockX(), 200, chunk.getMiddleBlockZ());
    var scanner = CDGItems.OIL_SCANNER.asStack();
    scanner.set(CDGDataComponents.OIL_SCANNER_STATE, 0);
    scanner.set(CDGDataComponents.OIL_SCANNER_PROGRESS, 0);
    scanner.getItem().inventoryTick(scanner, level, player, 0, true);
    require(
        scanner.getOrDefault(CDGDataComponents.OIL_SCANNER_STATE, -1)
            == (expected == 0 ? 1 : expected >= 72_000 ? 3 : 2),
        "Native scanner reads the current shared reserve");
  }

  private static void pumps(
      ServerLevel level,
      ChunkPos chunk,
      BlockPos first,
      BlockPos second,
      int initial,
      boolean exhaust) {
    BlockPos firstPump = first.above();
    BlockPos secondPump = second.above();
    BlockState beforeFirst = level.getBlockState(firstPump);
    BlockState beforeSecond = level.getBlockState(secondPump);
    try {
      var left = pump(level, firstPump);
      var right = pump(level, secondPump);
      IFluidHandler leftTank =
          level.getCapability(Capabilities.FluidHandler.BLOCK, firstPump, null);
      IFluidHandler rightTank =
          level.getCapability(Capabilities.FluidHandler.BLOCK, secondPump, null);
      require(leftTank != null && rightTank != null, "Native pump capabilities");
      left.pumpjackRotation(false);
      right.pumpjackRotation(false);
      require(
          leftTank.getFluidInTank(0).getAmount() == 1000
              && rightTank.getFluidInTank(0).getAmount() == 1000,
          "Both natural wells can extract");
      require(
          OilChunksSavedData.getChunkOilAmount(level, chunk) == initial - 2000,
          "Two wells debit one shared reserve");
      for (int stroke = 0; stroke < 9; stroke++) left.pumpjackRotation(false);
      require(leftTank.getFluidInTank(0).getAmount() == 8000, "Pump respects tank capacity");
      require(
          OilChunksSavedData.getChunkOilAmount(level, chunk) == initial - 9000,
          "Rejected tank fill does not consume oil");
      if (!exhaust) return;
      leftTank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
      rightTank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
      OilChunksSavedData.setChunkOilAmount(level, chunk, 137);
      left.pumpjackRotation(true);
      require(
          leftTank.getFluidInTank(0).getAmount() == 137,
          "Final stroke extracts exactly the finite residue");
      right.pumpjackRotation(true);
      require(
          rightTank.getFluidInTank(0).isEmpty(),
          "Second well stops when common reserve is exhausted");
      require(
          OilChunksSavedData.getChunkOilAmount(level, chunk) == 0,
          "Zero reserve remains authoritative with markers present");
    } finally {
      level.setBlockAndUpdate(firstPump, beforeFirst);
      level.setBlockAndUpdate(secondPump, beforeSecond);
    }
  }

  private static PumpjackHoleBlockEntity pump(ServerLevel level, BlockPos position) {
    level.setBlockAndUpdate(position, CDGBlocks.PUMPJACK_HOLE.getDefaultState());
    var pump = (PumpjackHoleBlockEntity) level.getBlockEntity(position);
    require(pump != null, "Native pump block entity");
    for (int tick = 0; tick < 20; tick++) pump.tick();
    require(
        pump.pipeLength == 1, "Native pipe validation accepts the physical target directly below");
    pump.headPos = 1;
    pump.bearingPos = 1;
    return pump;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
