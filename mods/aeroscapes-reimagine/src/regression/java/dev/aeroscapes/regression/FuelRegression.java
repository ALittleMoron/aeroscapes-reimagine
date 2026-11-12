package dev.aeroscapes.regression;

import com.forsteri.createliquidfuel.core.BurnerStomachHandler;
import com.forsteri.createliquidfuel.core.IHasStomach;
import com.forsteri.createliquidfuel.core.LiquidFuels;
import com.forsteri.createliquidfuel.util.Triplet;
import com.jesz.createdieselgenerators.CDGConfig;
import com.jesz.createdieselgenerators.CDGDataComponents;
import com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlockEntity;
import com.jesz.createdieselgenerators.content.diesel_engine.normal.DieselEngineBlock;
import com.jesz.createdieselgenerators.content.tools.ChemicalSprayerItem;
import com.jesz.createdieselgenerators.content.tools.ChemicalSprayerProjectileEntity;
import com.jesz.createdieselgenerators.content.tools.FueledToolItem;
import com.jesz.createdieselgenerators.content.tools.lighter.LighterState;
import com.jesz.createdieselgenerators.content.turret.ChemicalTurretBlockEntity;
import com.jesz.createdieselgenerators.content.turret.TurretBlockEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import dev.aeroscapes.mixin.engineering.fuel.BurnerFuelAccess;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Exercises transformed native interactions, fluid remainders, and paid heat/projectiles. */
public final class FuelRegression {
  private FuelRegression() {}

  public static void verify(ServerLevel level) {
    BlockPos spawn = level.getSharedSpawnPos();
    BlockPos pos =
        new BlockPos(
            spawn.getX() + 12,
            Math.min(spawn.getY() + 32, level.getMaxBuildHeight() - 4),
            spawn.getZ() + 12);
    var previous = level.getBlockState(pos);
    var above = level.getBlockState(pos.above());
    var player =
        new FakePlayer(
            level,
            new GameProfile(UUID.fromString("da8d67d7-b929-4ad3-988e-a2429520c788"), "AeroFuel"));
    player.setPos(Vec3.atCenterOf(pos).add(0, 1, 0));
    try {
      verifyMappings();
      level.setBlockAndUpdate(
          pos,
          block("create:blaze_burner")
              .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
      var burner = (BlazeBurnerBlockEntity) level.getBlockEntity(pos);
      require(burner != null, "Native blaze burner exists");
      verifyBurnerContainers(level, pos, burner);
      verifyHeat(burner);
      verifyBurnIntervals(burner);
      verifyPipePersistence(burner);
      for (String engine : List.of("diesel_engine", "large_diesel_engine", "huge_diesel_engine"))
        verifyEngine(level, pos, player, engine);
      verifyModularEngineConsumption(level, pos);
      verifyTools(level, pos, player);
      verifyTurret(level, pos);
    } finally {
      for (var projectile :
          level.getEntitiesOfClass(
              ChemicalSprayerProjectileEntity.class, new AABB(pos).inflate(10)))
        projectile.discard();
      level.setBlockAndUpdate(pos, previous);
      level.setBlockAndUpdate(pos.above(), above);
    }
  }

  private static void verifyMappings() {
    String[] names = {"plant_oil", "ethanol", "biodiesel", "diesel", "gasoline"};
    int[] times = {10, 12, 20, 30, 24};
    for (int index = 0; index < names.length; index++) {
      var property = LiquidFuels.get(fluid(names[index]));
      require(
          property != null
              && !property.superHeat()
              && property.burnTime() == times[index]
              && property.amountConsumedPerTick() == 1,
          "Explicit normal liquid fuel " + names[index]);
    }
    require(!LiquidFuels.isFuel(fluid("crude_oil")), "Crude oil is not burner fuel");
    require(LiquidFuels.get(Fluids.LAVA).burnTime() == 20, "Native lava value stays 20");
  }

  private static void verifyBurnerContainers(
      ServerLevel level, BlockPos pos, BlazeBurnerBlockEntity burner) {
    var tank = ((IHasStomach) burner).getCapability();
    tank.setFluid(FluidStack.EMPTY);
    var bucket = new ItemStack(Items.LAVA_BUCKET);
    var result = insert(level, pos, bucket, false, false);
    require(
        result.getResult().consumesAction()
            && bucket.isEmpty()
            && result.getObject().is(Items.BUCKET)
            && tank.getFluidAmount() == 1000,
        "Native tryInsert debits bucket and returns empty bucket");
    bucket = new ItemStack(Items.LAVA_BUCKET);
    require(
        !insert(level, pos, bucket, false, false).getResult().consumesAction()
            && bucket.getCount() == 1
            && tank.getFluidAmount() == 1000,
        "Full burner rejects without consumption");
    tank.setFluid(new FluidStack(Fluids.LAVA, 200));
    require(
        !insert(level, pos, bucket, false, false).getResult().consumesAction()
            && bucket.getCount() == 1
            && tank.getFluidAmount() == 200,
        "Indivisible bucket cannot lose partial contents or overfill");
    var simulatedBucket = bucket.copy();
    require(
        !insert(level, pos, simulatedBucket, false, true).getResult().consumesAction()
            && simulatedBucket.getCount() == 1
            && tank.getFluidAmount() == 200,
        "Partial bucket simulation agrees with actual transfer rejection");
    tank.setFluid(new FluidStack(fluid("diesel"), 1));
    require(
        !insert(level, pos, bucket, false, false).getResult().consumesAction()
            && tank.getFluidAmount() == 1,
        "Mismatched fuel is rejected");
    tank.setFluid(FluidStack.EMPTY);
    var canister = container("canister", fluid("diesel"), 1250);
    var realInput = canister.copy();
    result = insert(level, pos, canister, false, true);
    require(
        canister.isEmpty()
            && amount(result.getObject()) == 250
            && tank.isEmpty()
            && amount(realInput) == 1250,
        "Mechanical-arm simulation predicts residual and count without real mutation");
    result = insert(level, pos, realInput, false, false);
    require(
        realInput.isEmpty() && amount(result.getObject()) == 250 && tank.getFluidAmount() == 1000,
        "Partly drained canister conserves 1250mB");
    tank.setFluid(FluidStack.EMPTY);
    canister = container("canister", fluid("diesel"), 123);
    var directUpdate =
        new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>(
            "tryUpdateFuel", true, false);
    com.forsteri.createliquidfuel.core.BurnerStomachHandler.tryUpdateFuel(
        burner, canister, true, false, directUpdate);
    require(
        !directUpdate.getReturnValue() && tank.isEmpty() && amount(canister) == 123,
        "Direct native fuel update cannot copy canister contents without returning its remainder");
    canister = container("canister", fluid("diesel"), 321);
    result = insert(level, pos, canister, true, false);
    require(
        canister.getCount() == 1
            && amount(canister) == 321
            && result.getObject().isEmpty()
            && tank.getFluidAmount() == 321,
        "Creative burner supply leaves the input intact");
    var saved = burner.saveCustomOnly(level.registryAccess());
    tank.setFluid(FluidStack.EMPTY);
    burner.loadCustomOnly(saved, level.registryAccess());
    require(tank.getFluidAmount() == 321, "Pipe/container tank changes survive serialization");
    tank.setFluid(FluidStack.EMPTY);
    var coal = new ItemStack(Items.COAL);
    require(
        insert(level, pos, coal, false, false).getResult().consumesAction()
            && coal.isEmpty()
            && burner.getRemainingBurnTime() > 0,
        "Solid fuel still uses native Create behavior");
  }

  private static void verifyHeat(BlazeBurnerBlockEntity burner) {
    var tank = ((IHasStomach) burner).getCapability();
    var access = (BurnerFuelAccess) burner;
    access.aeroscapes$setActiveFuel(BlazeBurnerBlockEntity.FuelType.NONE);
    access.aeroscapes$setRemainingBurnTime(0);
    tank.setFluid(new FluidStack(fluid("diesel"), 2));
    BurnerStomachHandler.tick(burner);
    require(
        tank.getFluidAmount() == 1
            && burner.getRemainingBurnTime() == 30
            && burner.getActiveFuel() == BlazeBurnerBlockEntity.FuelType.NORMAL
            && burner.getHeatLevelFromBlock() == BlazeBurnerBlock.HeatLevel.KINDLED,
        "Paid diesel quantum credits normal KINDLED heat");
    access.aeroscapes$setRemainingBurnTime(BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY - 5);
    access.aeroscapes$setBlockHeat(BlazeBurnerBlock.HeatLevel.SMOULDERING);
    BurnerStomachHandler.tick(burner);
    require(
        tank.getFluidAmount() == 1
            && burner.getRemainingBurnTime() == BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY - 5
            && burner.getHeatLevelFromBlock() == BlazeBurnerBlock.HeatLevel.SMOULDERING,
        "Insufficient budget space cannot consume fuel or grant heat");
    access.aeroscapes$setActiveFuel(BlazeBurnerBlockEntity.FuelType.NONE);
    access.aeroscapes$setRemainingBurnTime(0);
    var cake =
        new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:blaze_cake")));
    var cakeResult =
        BlazeBurnerBlock.tryInsert(
            burner.getBlockState(),
            burner.getLevel(),
            burner.getBlockPos(),
            cake,
            false,
            true,
            false);
    int specialBudget = burner.getRemainingBurnTime();
    require(
        cakeResult.getResult().consumesAction() && cake.isEmpty() && specialBudget > 0,
        "Native Blaze Cake purchases the separate special budget");
    BurnerStomachHandler.tick(burner);
    require(
        tank.getFluidAmount() == 1
            && burner.getRemainingBurnTime() == specialBudget
            && burner.getActiveFuel() == BlazeBurnerBlockEntity.FuelType.SPECIAL
            && burner.getHeatLevelFromBlock() == BlazeBurnerBlock.HeatLevel.SEETHING,
        "Liquid fuel preserves paid Blaze Cake heat and its separate budget");
    var old =
        BurnerStomachHandler.LIQUID_BURNER_FUEL_MAP.put(
            Fluids.WATER,
            Pair.of(
                ResourceLocation.parse("aeroscapes_reimagine:regression_quantum"),
                new Triplet<>(20, false, 2)));
    try {
      access.aeroscapes$setActiveFuel(BlazeBurnerBlockEntity.FuelType.NONE);
      access.aeroscapes$setRemainingBurnTime(0);
      tank.setFluid(new FluidStack(Fluids.WATER, 1));
      BurnerStomachHandler.tick(burner);
      require(
          tank.getFluidAmount() == 1 && burner.getRemainingBurnTime() == 0,
          "Sub-quantum residual remains stored without free heat");
      tank.setFluid(new FluidStack(Fluids.WATER, 2));
      BurnerStomachHandler.tick(burner);
      require(
          tank.isEmpty() && burner.getRemainingBurnTime() == 20,
          "Whole quantum consumes exact amount");
      burner.isCreative = true;
      tank.setFluid(new FluidStack(fluid("diesel"), 2));
      BurnerStomachHandler.tick(burner);
      require(
          tank.getFluidAmount() == 2 && burner.getRemainingBurnTime() == 20,
          "Creative burner is untouched");
    } finally {
      burner.isCreative = false;
      if (old == null) BurnerStomachHandler.LIQUID_BURNER_FUEL_MAP.remove(Fluids.WATER);
      else BurnerStomachHandler.LIQUID_BURNER_FUEL_MAP.put(Fluids.WATER, old);
    }
  }

  private static void verifyBurnIntervals(BlazeBurnerBlockEntity burner) {
    var tank = ((IHasStomach) burner).getCapability();
    var access = (BurnerFuelAccess) burner;
    for (String name : List.of("plant_oil", "ethanol", "biodiesel", "diesel", "gasoline")) {
      int burnTime = LiquidFuels.get(fluid(name)).burnTime();
      access.aeroscapes$setActiveFuel(BlazeBurnerBlockEntity.FuelType.NONE);
      access.aeroscapes$setRemainingBurnTime(0);
      access.aeroscapes$setBlockHeat(BlazeBurnerBlock.HeatLevel.SMOULDERING);
      tank.setFluid(new FluidStack(fluid(name), 3));
      for (int tick = 1; tick <= burnTime * 3 + 10; tick++) {
        burner.tick();
        int consumed = Math.min(3, 1 + (tick - 1) / burnTime);
        int remaining = Math.max(0, consumed * burnTime - (tick - 1));
        require(
            tank.getFluidAmount() == 3 - consumed && burner.getRemainingBurnTime() == remaining,
            "Transformed burner tick conserves paid interval for "
                + name
                + " at "
                + tick
                + ": expected tank="
                + (3 - consumed)
                + ", budget="
                + remaining
                + "; actual tank="
                + tank.getFluidAmount()
                + ", budget="
                + burner.getRemainingBurnTime()
                + ", activeFuel="
                + burner.getActiveFuel()
                + ", heat="
                + burner.getHeatLevelFromBlock());
        if (remaining == 0) {
          require(
              burner.getActiveFuel() == BlazeBurnerBlockEntity.FuelType.NONE
                  && burner.getHeatLevelFromBlock() == BlazeBurnerBlock.HeatLevel.SMOULDERING,
              "Exhausted liquid fuel grants no further heat " + name);
        } else {
          require(
              burner.getActiveFuel() == BlazeBurnerBlockEntity.FuelType.NORMAL
                  && HeatCondition.HEATED.testBlazeBurner(burner.getHeatLevelFromBlock())
                  && !HeatCondition.SUPERHEATED.testBlazeBurner(burner.getHeatLevelFromBlock()),
              "Liquid fuel remains normal throughout its paid interval " + name);
        }
      }
    }
  }

  private static void verifyPipePersistence(BlazeBurnerBlockEntity burner) {
    var level = (ServerLevel) burner.getLevel();
    long originalTime = level.getGameTime();
    var chunk = level.getChunkAt(burner.getBlockPos());
    boolean previouslyUnsaved = chunk.isUnsaved();
    var access = (BurnerFuelAccess) burner;
    var tank = ((IHasStomach) burner).getCapability();
    access.aeroscapes$setActiveFuel(BlazeBurnerBlockEntity.FuelType.NORMAL);
    access.aeroscapes$setRemainingBurnTime(2000);
    access.aeroscapes$setBlockHeat(BlazeBurnerBlock.HeatLevel.KINDLED);
    tank.setFluid(FluidStack.EMPTY);
    try {
      var pendingSync = burner.getClass().getDeclaredField("aeroscapes$fluidChanged");
      pendingSync.setAccessible(true);
      pendingSync.setBoolean(burner, false);
      chunk.setUnsaved(false);
      var pipe =
          level.getCapability(
              Capabilities.FluidHandler.BLOCK, burner.getBlockPos(), Direction.DOWN);
      require(
          pipe != null
              && pipe.fill(new FluidStack(fluid("diesel"), 3), IFluidHandler.FluidAction.EXECUTE)
                  == 3,
          "Native pipe capability accepts liquid while solid fuel is burning");
      require(
          chunk.isUnsaved() && pendingSync.getBoolean(burner),
          "Pipe fill immediately marks storage dirty and queues synchronization");
      long syncTick = originalTime - Math.floorMod(originalTime, 20);
      ((ServerLevelData) level.getLevelData()).setGameTime(syncTick + 1);
      burner.tick();
      require(
          pendingSync.getBoolean(burner) && burner.getRemainingBurnTime() == 1999,
          "Early-return burn tick preserves pending notification before throttle boundary");
      ((ServerLevelData) level.getLevelData()).setGameTime(syncTick + 20);
      burner.tick();
      require(
          !pendingSync.getBoolean(burner)
              && burner.getRemainingBurnTime() == 1998
              && tank.getFluidAmount() == 3,
          "Early-return solid burn dispatches tank synchronization at throttle boundary");
      var packet = burner.getUpdatePacket();
      var restored =
          (BlazeBurnerBlockEntity)
              burner.getType().create(burner.getBlockPos(), burner.getBlockState());
      require(restored != null && packet != null, "Native burner synchronization packet exists");
      restored.setLevel(level);
      restored.handleUpdateTag(packet.getTag(), level.registryAccess());
      require(
          ((IHasStomach) restored).getCapability().getFluidAmount() == 3,
          "Native synchronization payload restores the pipe-filled tank");
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Could not observe pending burner synchronization", exception);
    } finally {
      ((ServerLevelData) level.getLevelData()).setGameTime(originalTime);
      chunk.setUnsaved(previouslyUnsaved || chunk.isUnsaved());
    }
  }

  private static void verifyEngine(
      ServerLevel level, BlockPos pos, FakePlayer player, String name) {
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(pos, block("createdieselgenerators:" + name));
    var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
    require(tank != null, "Native engine capability " + name);
    tank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
    var dieselBucket =
        BuiltInRegistries.ITEM.get(ResourceLocation.parse("createdieselgenerators:diesel_bucket"));
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dieselBucket));
    useBlock(level, pos, player);
    if (tank.getTankCapacity(0) >= 1000) {
      require(
          player.getMainHandItem().is(Items.BUCKET) && tank.getFluidInTank(0).getAmount() == 1000,
          "Engine accepts complete diesel bucket and returns empty bucket " + name);
    } else {
      require(
          player.getMainHandItem().is(dieselBucket) && tank.getFluidInTank(0).isEmpty(),
          "Small engine tank rejects indivisible diesel bucket " + name);
    }
    tank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
    tank.fill(new FluidStack(fluid("diesel"), 1), IFluidHandler.FluidAction.EXECUTE);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dieselBucket));
    useBlock(level, pos, player);
    require(
        player.getMainHandItem().is(dieselBucket) && tank.getFluidInTank(0).getAmount() == 1,
        "Engine rejects partial indivisible bucket transfer " + name);
    tank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
    var input = container("canister", fluid("diesel"), 1250);
    player.setItemInHand(InteractionHand.MAIN_HAND, input);
    useBlock(level, pos, player);
    int stored = tank.getFluidInTank(0).getAmount();
    require(
        stored > 0 && stored + amount(player.getMainHandItem()) == 1250,
        "Engine transfer conserves native canister residual " + name);
    require(stored == tank.getTankCapacity(0), "Canister fills engine tank " + name);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dieselBucket));
    useBlock(level, pos, player);
    require(
        player.getMainHandItem().is(dieselBucket) && tank.getFluidInTank(0).getAmount() == stored,
        "Full engine tank rejects diesel bucket without loss " + name);
    input = new ItemStack(Items.LAVA_BUCKET);
    player.setItemInHand(InteractionHand.MAIN_HAND, input);
    useBlock(level, pos, player);
    require(
        player.getMainHandItem().is(Items.LAVA_BUCKET)
            && tank.getFluidInTank(0).getAmount() == stored,
        "Engine rejects mismatched bucket without loss " + name);
    tank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    require(player.isCreative(), "Native server game mode enables creative supply");
    input = container("canister", fluid("diesel"), 75);
    player.setItemInHand(InteractionHand.MAIN_HAND, input);
    useBlock(level, pos, player);
    require(
        amount(player.getMainHandItem()) == 75 && tank.getFluidInTank(0).getAmount() == 75,
        "Creative engine supply retains native canister " + name);
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
  }

  private static void verifyModularEngineConsumption(ServerLevel level, BlockPos pos) {
    boolean analogControl = CDGConfig.ANALOG_SPEED_CONTROL.get();
    boolean disableWithRedstone = CDGConfig.ENGINES_DISABLED_WITH_REDSTONE.get();
    try {
      CDGConfig.ANALOG_SPEED_CONTROL.set(false);
      CDGConfig.ENGINES_DISABLED_WITH_REDSTONE.set(true);
      level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
      var state = block("createdieselgenerators:large_diesel_engine");
      level.setBlockAndUpdate(pos, state.setValue(DieselEngineBlock.POWERED, true));
      var engine = (ModularDieselEngineBlockEntity) level.getBlockEntity(pos);
      require(engine != null, "Native modular engine exists for fuel consumption check");
      engine.getTank().setFluid(new FluidStack(fluid("diesel"), 1000));
      require(!engine.enabled(), "Redstone disables the modular engine");
      for (int tick = 0; tick < 200; tick++) engine.tick();
      require(
          engine.getTank().getFluidAmount() == 1000,
          "Disabled modular engine cannot accumulate or debit fuel debt");
      level.setBlockAndUpdate(pos, state.setValue(DieselEngineBlock.POWERED, false));
      require(
          engine.enabled() && engine.getFuelThrottle() == 1,
          "Unpowered modular engine runs at full fuel throttle");
      float rate = engine.getFuelBurnRate() * engine.getHeight();
      require(rate > 0, "Native diesel has a positive modular burn rate");
      float expectedDebt = 0;
      int expectedConsumed = 0;
      for (int tick = 0; tick < 200; tick++) {
        expectedDebt += rate;
        while (expectedDebt >= 1) {
          expectedConsumed++;
          expectedDebt--;
        }
        engine.tick();
      }
      require(
          expectedConsumed > 0
              && expectedConsumed < 1000
              && engine.getTank().getFluidAmount() == 1000 - expectedConsumed,
          "Enabled modular engine preserves its exact native full-throttle consumption");
    } finally {
      CDGConfig.ANALOG_SPEED_CONTROL.set(analogControl);
      CDGConfig.ENGINES_DISABLED_WITH_REDSTONE.set(disableWithRedstone);
    }
  }

  private static void verifyTools(ServerLevel level, BlockPos pos, Player player) {
    for (String name : List.of("chemical_sprayer", "chemical_sprayer_lighter")) {
      var sprayer = container(name, fluid("diesel"), 4);
      var item = (ChemicalSprayerItem) sprayer.getItem();
      item.onUseTick(level, player, sprayer, 998);
      require(amount(sprayer) == 3, "One-shot short burst pays immediately " + name);
      item.onUseTick(level, player, sprayer, 997);
      require(amount(sprayer) == 3, "No shot does not debit fuel " + name);
      item.onUseTick(level, player, sprayer, 996);
      require(amount(sprayer) == 2, "Second shot pays independently " + name);
      item.onUseTick(level, player, sprayer, 1000);
      require(amount(sprayer) == 1, "Periodic boundary shot is charged only once " + name);
      item.onUseTick(level, player, sprayer, 975);
      require(amount(sprayer) == 1, "Odd periodic boundary cannot consume fuel " + name);
      item.onUseTick(level, player, sprayer, 950);
      require(amount(sprayer) == 0, "Last millibucket pays for exactly one shot " + name);
      int projectiles =
          level
              .getEntitiesOfClass(ChemicalSprayerProjectileEntity.class, new AABB(pos).inflate(10))
              .size();
      item.onUseTick(level, player, sprayer, 948);
      require(
          amount(sprayer) == 0
              && projectiles
                  == level
                      .getEntitiesOfClass(
                          ChemicalSprayerProjectileEntity.class, new AABB(pos).inflate(10))
                      .size(),
          "Empty sprayer cannot emit another projectile " + name);
    }
    level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
    var lighter = container("lighter", fluid("diesel"), 0);
    lighter.set(CDGDataComponents.LIGHTER_STATE, LighterState.OPEN_IGNITED);
    player.setItemInHand(InteractionHand.MAIN_HAND, lighter);
    var context =
        new UseOnContext(
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    lighter.useOn(context);
    require(level.getBlockState(pos.above()).isAir(), "Empty ignited lighter cannot place fire");
    ((FueledToolItem) lighter.getItem()).writeFluid(lighter, new FluidStack(fluid("diesel"), 2));
    lighter.set(CDGDataComponents.LIGHTER_STATE, LighterState.OPEN_IGNITED);
    lighter.useOn(context);
    require(
        !level.getBlockState(pos.above()).isAir() && amount(lighter) == 1,
        "Native successful lighter ignition costs exactly 1mB");
  }

  private static void verifyTurret(ServerLevel level, BlockPos pos) {
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(pos, block("createdieselgenerators:chemical_turret"));
    var turret = (ChemicalTurretBlockEntity) level.getBlockEntity(pos);
    require(turret != null, "Native chemical turret exists");
    turret.setSpeed(64);
    SmartFluidTank tank = turret.tank.getPrimaryHandler();
    tank.setFluid(new FluidStack(fluid("diesel"), 10));
    turret.shootFluids();
    require(tank.getFluidAmount() == 7, "Turret first shot costs 3mB outside periodic tick");
    try {
      var periodicTick = TurretBlockEntity.class.getDeclaredField("t");
      periodicTick.setAccessible(true);
      periodicTick.setInt(turret, 1);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Could not exercise native turret periodic debit", exception);
    }
    turret.shootFluids();
    require(tank.getFluidAmount() == 4, "Native periodic tick still charges 3mB, not 6mB");
    turret.shootFluids();
    require(tank.getFluidAmount() == 1, "Turret next shot also costs 3mB");
    turret.shootFluids();
    require(tank.getFluidAmount() == 1, "Turret preserves insufficient shot residual");
  }

  private static void useBlock(ServerLevel level, BlockPos pos, Player player) {
    level
        .getBlockState(pos)
        .useItemOn(
            player.getMainHandItem(),
            level,
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
  }

  private static InteractionResultHolder<ItemStack> insert(
      ServerLevel level, BlockPos pos, ItemStack stack, boolean creative, boolean simulate) {
    return BlazeBurnerBlock.tryInsert(
        level.getBlockState(pos), level, pos, stack, creative, true, simulate);
  }

  private static ItemStack container(String name, Fluid fluid, int amount) {
    var stack =
        new ItemStack(
            BuiltInRegistries.ITEM.get(ResourceLocation.parse("createdieselgenerators:" + name)));
    ((FueledToolItem) stack.getItem()).writeFluid(stack, new FluidStack(fluid, amount));
    return stack;
  }

  private static int amount(ItemStack stack) {
    return ((FueledToolItem) stack.getItem()).readFluid(stack).getAmount();
  }

  private static Fluid fluid(String name) {
    return BuiltInRegistries.FLUID.get(ResourceLocation.parse("createdieselgenerators:" + name));
  }

  private static BlockState block(String id) {
    return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
