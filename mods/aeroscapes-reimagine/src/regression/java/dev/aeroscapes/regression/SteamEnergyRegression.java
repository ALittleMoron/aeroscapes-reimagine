package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.teammoeg.steampowered.content.boiler.BoilerTileEntity;
import com.teammoeg.steampowered.content.burner.BurnerBlock;
import com.teammoeg.steampowered.content.burner.BurnerBlockEntity;
import com.teammoeg.steampowered.content.engine.SteamEngineTileEntity;
import com.teammoeg.steampowered.oldcreatestuff.OldEngineBlock;
import com.teammoeg.steampowered.oldcreatestuff.OldFlywheelBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Runs loaded native boilers and matched engine/flywheel pairs, without a client. */
public final class SteamEnergyRegression {
  private SteamEnergyRegression() {}

  public static void verifyPersistence(ServerLevel level, boolean reload) {
    BlockPos spawn = level.getSharedSpawnPos();
    BlockPos piped =
        new BlockPos(
            spawn.getX() + 64,
            Math.min(spawn.getY() + 32, level.getMaxBuildHeight() - 4),
            (spawn.getZ() & ~15) + 15);
    BlockPos direct = piped.east(6);
    BlockPos source = direct.south();
    if (!reload) {
      level.getServer().tickRateManager().setFrozen(true);
      var steam = BuiltInRegistries.FLUID.get(ResourceLocation.parse("steampowered:steam"));
      for (BlockPos pos : new BlockPos[] {piped, direct}) {
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.north(2), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.north(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(
            pos.north(2),
            block("bronze_flywheel").setValue(OldFlywheelBlock.HORIZONTAL_FACING, Direction.EAST));
        level.setBlockAndUpdate(
            pos, block("bronze_steam_engine").setValue(OldEngineBlock.FACING, Direction.NORTH));
      }
      fluidHandler(level, piped)
          .fill(new FluidStack(steam, 5000), IFluidHandler.FluidAction.EXECUTE);
      level.setBlockAndUpdate(source, block("bronze_boiler"));
      var boiler = (BoilerTileEntity) level.getBlockEntity(source);
      require(boiler != null, "Persistent direct boiler");
      boiler.output.setFluid(new FluidStack(steam, 10000));
      var ownEngine = (SteamEngineTileEntity) level.getBlockEntity(piped);
      var directEngine = (SteamEngineTileEntity) level.getBlockEntity(direct);
      require(ownEngine != null && directEngine != null, "Persistent native engines");
      for (int tick = 0; tick < 61; tick++) {
        ownEngine.tick();
        directEngine.tick();
      }
      level.getChunkSource().save(true);
      require(
          !level.getChunkAt(piped).isUnsaved() && !level.getChunkAt(source).isUnsaved(),
          "Baseline chunks saved before steady-state debit");
      ownEngine.tick();
      directEngine.tick();
    }
    require(
        fluidHandler(level, piped).getFluidInTank(0).getAmount() == 5000 - 62 * 12,
        "Piped reserve retains exact steady-state steam debit across disk reload");
    var boiler = (BoilerTileEntity) level.getBlockEntity(source);
    require(
        boiler != null && boiler.output.getFluidAmount() == 10000 - 62 * 12,
        "Separate source chunk retains exact direct debit across disk reload");
    if (reload) level.getServer().tickRateManager().setFrozen(false);
  }

  public static void verify(ServerLevel level) {
    BlockPos spawn = level.getSharedSpawnPos();
    BlockPos pos =
        new BlockPos(
            spawn.getX() + 8,
            Math.min(spawn.getY() + 24, level.getMaxBuildHeight() - 4),
            (spawn.getZ() & ~15) + 15);
    Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
    for (BlockPos location :
        new BlockPos[] {pos, pos.below(), pos.south(), pos.north(), pos.north(2)})
      previous.put(location, level.getBlockState(location));
    float[] heat = {0};
    boolean[] testing = {true};
    BoilerHeater heater = (world, location, state) -> heat[0];
    BoilerHeater.REGISTRY.registerProvider(
        block -> block == Blocks.GOLD_BLOCK && testing[0] ? heater : null);
    try {
      String[] tiers = {"bronze", "cast_iron", "steel"};
      int[] maximum = {12, 24, 48};
      for (int index = 0; index < tiers.length; index++) {
        verifyBoiler(level, pos, tiers[index], maximum[index], heat);
        verifyEngine(level, pos, tiers[index], maximum[index], 16384 << index);
      }
      verifySteamCanister(level);
      verifyCanisterEngine(level, pos);
    } finally {
      testing[0] = false;
      BoilerHeater.REGISTRY.invalidate();
      previous.forEach(level::setBlockAndUpdate);
    }
  }

  private static void verifyBoiler(
      ServerLevel level, BlockPos pos, String tier, int maximum, float[] suppliedHeat) {
    level.setBlockAndUpdate(pos.below(), Blocks.GOLD_BLOCK.defaultBlockState());
    level.setBlockAndUpdate(pos, block(tier + "_boiler"));
    var boiler = (BoilerTileEntity) level.getBlockEntity(pos);
    require(boiler != null, "Native boiler " + tier);
    var handler = fluidHandler(level, pos);
    require(
        handler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
        "Boiler rejects non-water");
    for (float heat : new float[] {-1, 0, 0.25F, 1, 2, 4}) {
      suppliedHeat[0] = heat;
      boiler.output.setFluid(FluidStack.EMPTY);
      int before = handler.getFluidInTank(0).getAmount();
      handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
      int water = handler.getFluidInTank(0).getAmount();
      boiler.tick();
      int expected = heat <= 0 ? 0 : (int) Math.min(maximum, maximum * heat * 0.75F);
      require(
          boiler.output.getFluidAmount() == expected,
          tier
              + " heat="
              + heat
              + " expected="
              + expected
              + " actual="
              + boiler.output.getFluidAmount());
      require(
          expected == 0
              || boiler
                  .output
                  .getFluid()
                  .is(BuiltInRegistries.FLUID.get(ResourceLocation.parse("steampowered:steam"))),
          "Boilers produce source steam for native fluid orders");
      int waterSpent = water - handler.getFluidInTank(0).getAmount();
      require(
          waterSpent == (heat <= 0 ? 0 : (int) Math.ceil(expected / 12D)), "Whole-mB water debit");
      require(water >= before, "Water input accepted");
    }
    var legacySteam =
        new FluidStack(
            BuiltInRegistries.FLUID.get(ResourceLocation.parse("steampowered:flowing_steam")),
            boiler.output.getFluidAmount());
    legacySteam.set(DataComponents.CUSTOM_NAME, Component.literal("Stored steam"));
    boiler.output.setFluid(legacySteam);
    var saved = boiler.saveWithoutMetadata(level.registryAccess());
    int amount = boiler.output.getFluidAmount();
    boiler.output.setFluid(FluidStack.EMPTY);
    boiler.loadWithComponents(saved, level.registryAccess());
    require(boiler.output.getFluidAmount() == amount, "Boiler steam NBT round trip");
    require(
        boiler
                .output
                .getFluid()
                .is(BuiltInRegistries.FLUID.get(ResourceLocation.parse("steampowered:steam")))
            && boiler
                .output
                .getFluid()
                .getComponentsPatch()
                .equals(legacySteam.getComponentsPatch()),
        "Stored boiler steam normalizes its identity without changing components or volume");
    handler.drain(10000, IFluidHandler.FluidAction.EXECUTE);
    level.setBlockAndUpdate(
        pos.below(), block(tier + "_burner").setValue(BurnerBlock.REDSTONE_LOCKED, false));
    boiler.commitHeat(maximum * 10);
    boiler.tick();
    require(boiler.output.getFluidAmount() == maximum, "Native burner direct heat remains full");
    var burner = (BurnerBlockEntity) level.getBlockEntity(pos.below());
    require(burner != null, "Native fuel chamber");
    var fuel = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.below(), Direction.UP);
    require(
        fuel != null && fuel.insertItem(0, new ItemStack(Items.COAL), false).isEmpty(),
        "Coal insertion");
    boiler.output.setFluid(FluidStack.EMPTY);
    burner.tick();
    boiler.tick();
    int efficiency = tier.equals("bronze") ? 80 : tier.equals("cast_iron") ? 90 : 100;
    require(
        fuel.getStackInSlot(0).isEmpty(),
        "Exactly one coal consumed: "
            + tier
            + " state="
            + burner.getBlockState()
            + " hu="
            + burner.saveWithoutMetadata(level.registryAccess()).getInt("hu")
            + " remaining="
            + fuel.getStackInSlot(0));
    require(
        burner.saveWithoutMetadata(level.registryAccess()).getInt("hu")
            == 1600 * 24 * efficiency / 100 - maximum * 10,
        "Paid native chamber heat budget");
    require(boiler.output.getFluidAmount() == maximum, "Solid fuel produces native full output");
    for (var state :
        new BlazeBurnerBlock.HeatLevel[] {
          BlazeBurnerBlock.HeatLevel.SMOULDERING,
          BlazeBurnerBlock.HeatLevel.KINDLED,
          BlazeBurnerBlock.HeatLevel.SEETHING
        }) {
      level.setBlockAndUpdate(
          pos.below(),
          BuiltInRegistries.BLOCK
              .get(ResourceLocation.parse("create:blaze_burner"))
              .defaultBlockState()
              .setValue(BlazeBurnerBlock.HEAT_LEVEL, state));
      boiler.output.setFluid(FluidStack.EMPTY);
      boiler.tick();
      int expected =
          state == BlazeBurnerBlock.HeatLevel.SMOULDERING
              ? 0
              : state == BlazeBurnerBlock.HeatLevel.KINDLED ? maximum * 3 / 4 : maximum;
      require(
          boiler.output.getFluidAmount() == expected,
          "Actual blaze state " + state + " in " + tier);
    }
  }

  private static void verifySteamCanister(ServerLevel level) {
    var empty =
        BuiltInRegistries.ITEM.get(
            ResourceLocation.parse("steampowered:pressurized_gas_container"));
    var filled =
        BuiltInRegistries.ITEM.get(
            ResourceLocation.parse("steampowered:pressurized_steam_container"));
    var steam = BuiltInRegistries.FLUID.get(ResourceLocation.parse("steampowered:steam"));
    var input = new ItemStack(empty);
    var supply = new FluidStack(steam, 1100);
    require(
        FillingBySpout.getRequiredAmountForItem(level, input, supply) == 1000,
        "Canister fill cost");
    var result = FillingBySpout.fillItem(level, 1000, input, supply);
    require(
        result.is(filled) && result.getCount() == 1 && supply.getAmount() == 100,
        "Native filling conserves steam");
    var simulated = GenericItemEmptying.emptyItem(level, result, true);
    require(
        simulated.getFirst().getAmount() == 1000 && result.getCount() == 1,
        "Canister empty simulation");
    var emptied = GenericItemEmptying.emptyItem(level, result, false);
    require(
        emptied.getFirst().getFluid() == steam
            && emptied.getFirst().getAmount() == 1000
            && emptied.getSecond().is(empty)
            && emptied.getSecond().getCount() == 1,
        "Native emptying returns exact steam and container");
  }

  private static void verifyCanisterEngine(ServerLevel level, BlockPos pos) {
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(
        pos, block("bronze_steam_engine").setValue(OldEngineBlock.FACING, Direction.NORTH));
    var steam = BuiltInRegistries.FLUID.get(ResourceLocation.parse("steampowered:steam"));
    var tank = fluidHandler(level, pos);
    var player =
        new FakePlayer(
            level,
            new GameProfile(
                UUID.fromString("c265463d-5fce-4c72-9a48-0f6110981540"), "SteamCanister"));
    var full =
        BuiltInRegistries.ITEM.get(
            ResourceLocation.parse("steampowered:pressurized_steam_container"));
    var empty =
        BuiltInRegistries.ITEM.get(
            ResourceLocation.parse("steampowered:pressurized_gas_container"));
    int initial = tank.getTankCapacity(0) - 500;
    require(
        tank.fill(new FluidStack(steam, initial), IFluidHandler.FluidAction.EXECUTE) == initial,
        "Almost full engine steam reserve");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(full));
    var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    var result =
        level
            .getBlockState(pos)
            .useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    require(
        result == ItemInteractionResult.FAIL
            && tank.getFluidInTank(0).getAmount() == initial
            && player.getMainHandItem().is(full)
            && player.getInventory().countItem(empty) == 0,
        "Insufficient engine space preserves the complete canister and steam reserve");
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(
        pos, block("bronze_steam_engine").setValue(OldEngineBlock.FACING, Direction.NORTH));
    tank = fluidHandler(level, pos);
    tank.fill(new FluidStack(steam, 1500), IFluidHandler.FluidAction.EXECUTE);
    result =
        level
            .getBlockState(pos)
            .useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    require(
        result == ItemInteractionResult.SUCCESS
            && tank.getFluidInTank(0).getAmount() == 2500
            && tank.getFluidInTank(0).is(steam)
            && player.getInventory().countItem(full) == 0
            && player.getInventory().countItem(empty) == 1,
        "Native canister adds exactly 1000 source steam and returns one empty container");
  }

  private static void verifyEngine(
      ServerLevel level, BlockPos pos, String tier, int consumption, int stress) {
    level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(pos.north(), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(
        pos.north(2),
        block(tier + "_flywheel").setValue(OldFlywheelBlock.HORIZONTAL_FACING, Direction.EAST));
    level.setBlockAndUpdate(
        pos, block(tier + "_steam_engine").setValue(OldEngineBlock.FACING, Direction.NORTH));
    var engine = (SteamEngineTileEntity) level.getBlockEntity(pos);
    require(engine != null, "Native engine " + tier);
    var handler = fluidHandler(level, pos);
    require(
        handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
        "Engine rejects water");
    var steam = BuiltInRegistries.FLUID.get(ResourceLocation.parse("steampowered:steam"));
    require(
        handler.fill(new FluidStack(steam, consumption * 100), IFluidHandler.FluidAction.EXECUTE)
            == consumption * 100,
        "Piped steam reserve");
    engine.attachWheel();
    for (int tick = 0; tick < 61; tick++) engine.tick();
    require(
        handler.getFluidInTank(0).getAmount() == consumption * 39,
        "Exact engine steam consumption");
    require(engine.appliedSpeed == 32, "Native generating speed");
    require(engine.appliedCapacity * engine.appliedSpeed == stress, "Tier stress capacity " + tier);
    var engineChunk = level.getChunkAt(pos);
    engineChunk.setUnsaved(false);
    engine.tick();
    require(engineChunk.isUnsaved(), "Stable engine steam debit marks its chunk for saving");
    var saved = engine.saveWithoutMetadata(level.registryAccess());
    engine.loadWithComponents(saved, level.registryAccess());
    require(
        handler.getFluidInTank(0).getAmount() == consumption * 38, "Engine steam NBT round trip");
    var legacySteam =
        new FluidStack(
            BuiltInRegistries.FLUID.get(ResourceLocation.parse("steampowered:flowing_steam")),
            consumption * 38);
    legacySteam.set(DataComponents.CUSTOM_NAME, Component.literal("Stored engine steam"));
    var legacyTank = new FluidTank(handler.getTankCapacity(0));
    legacyTank.setFluid(legacySteam);
    saved.put("TankContent", legacyTank.writeToNBT(level.registryAccess(), new CompoundTag()));
    engine.loadWithComponents(saved, level.registryAccess());
    require(
        handler.getFluidInTank(0).is(steam)
            && handler.getFluidInTank(0).getAmount() == consumption * 38
            && handler
                .getFluidInTank(0)
                .getComponentsPatch()
                .equals(legacySteam.getComponentsPatch()),
        "Stored engine steam normalizes without changing components or volume");
    saved.put("TankContent", new CompoundTag());
    engine.loadWithComponents(saved, level.registryAccess());
    BlockPos boilerPos = pos.south();
    level.setBlockAndUpdate(boilerPos, block(tier + "_boiler"));
    var boiler = (BoilerTileEntity) level.getBlockEntity(boilerPos);
    require(boiler != null, "Direct steam source");
    boiler.output.setFluid(new FluidStack(steam, consumption * 100));
    var boilerChunk = level.getChunkAt(boilerPos);
    require(boilerChunk != engineChunk, "Direct source crosses a real chunk boundary");
    boilerChunk.setUnsaved(false);
    engine.tick();
    require(boiler.output.getFluidAmount() == consumption * 99, "Exact direct boiler steam debit");
    require(boilerChunk.isUnsaved(), "Direct boiler steam debit marks source chunk for saving");
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(pos.north(2), Blocks.AIR.defaultBlockState());
  }

  private static BlockState block(String path) {
    var id = ResourceLocation.fromNamespaceAndPath("steampowered", path);
    require(BuiltInRegistries.BLOCK.containsKey(id), "Registered " + id);
    return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
  }

  private static IFluidHandler fluidHandler(ServerLevel level, BlockPos pos) {
    var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
    if (handler == null) throw new IllegalStateException("Missing fluid capability " + pos);
    return handler;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
