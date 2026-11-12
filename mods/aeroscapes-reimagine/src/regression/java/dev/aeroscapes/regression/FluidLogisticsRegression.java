package dev.aeroscapes.regression;

import com.jesz.createdieselgenerators.content.diesel_engine.normal.DieselEngineBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.teammoeg.steampowered.content.boiler.BoilerTileEntity;
import com.yision.fluidlogistics.api.packager.PackageResourceTypes;
import com.yision.fluidlogistics.api.packager.ResourcePackagers;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidSupplyRules;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidTankItem;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageContentHelper;
import com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerBlockEntity;
import com.yision.fluidlogistics.content.logistics.fluidPackager.repackager.FluidRepackagerBlockEntity;
import com.yision.fluidlogistics.infrastructure.config.Config;
import com.yision.fluidlogistics.infrastructure.config.InfiniteTankFluidMode;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Exercises native order planning, packing, unpacking, remainders, and infinite-tank rules. */
public final class FluidLogisticsRegression {
  private FluidLogisticsRegression() {}

  public static void verify(ServerLevel level) {
    var spawn = level.getSharedSpawnPos();
    var origin =
        new BlockPos(
            spawn.getX() + 32,
            Math.min(spawn.getY() + 32, level.getMaxBuildHeight() - 4),
            spawn.getZ() + 32);
    var positions =
        List.of(
            origin,
            origin.east(),
            origin.above(),
            origin.south(4),
            origin.south(4).east(),
            origin.south(8),
            origin.south(8).east(),
            origin.south(12),
            origin.south(16));
    var previous = positions.stream().map(level::getBlockState).toList();
    PackagerLinkBlockEntity link = null;
    UUID network = UUID.fromString("ed00947c-62ed-4f4b-9740-30cd1ac24c8d");
    try {
      level.setBlockAndUpdate(origin.east(), block("create:fluid_tank"));
      var sourceTank = (FluidTankBlockEntity) level.getBlockEntity(origin.east());
      require(sourceTank != null, "Native source tank exists");
      sourceTank.initialize();
      level.setBlockAndUpdate(
          origin,
          block("fluidlogistics:fluid_packager").setValue(PackagerBlock.FACING, Direction.WEST));
      var packager = (FluidPackagerBlockEntity) level.getBlockEntity(origin);
      require(packager != null, "Native fluid packager exists");
      packager.initialize();
      packager.fluidTarget.findNewCapability();
      level.setBlockAndUpdate(
          origin.above(),
          block("create:stock_link")
              .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
      link = (PackagerLinkBlockEntity) level.getBlockEntity(origin.above());
      require(link != null, "Native stock link exists");
      link.initialize();
      LogisticallyLinkedBehaviour.remove(link.behaviour);
      link.behaviour.freqId = network;
      LogisticallyLinkedBehaviour.keepAlive(link.behaviour);
      require(link.getPackager() == packager, "Stock link is attached to fluid packager");

      level.setBlockAndUpdate(origin.south(4).east(), block("create:fluid_tank"));
      var destinationTank = (FluidTankBlockEntity) level.getBlockEntity(origin.south(4).east());
      require(destinationTank != null, "Native destination tank exists");
      destinationTank.initialize();
      level.setBlockAndUpdate(
          origin.south(4),
          block("fluidlogistics:fluid_packager").setValue(PackagerBlock.FACING, Direction.WEST));
      var receiver = (FluidPackagerBlockEntity) level.getBlockEntity(origin.south(4));
      require(receiver != null, "Native fluid receiver exists");
      receiver.initialize();
      receiver.fluidTarget.findNewCapability();

      for (var fluid : BuiltInRegistries.FLUID) {
        if (fluid == Fluids.EMPTY
            || fluid instanceof FlowingFluid flowing && flowing.getSource() != fluid) continue;
        var resource = new FluidStack(fluid, 1000);
        var key = PackageResourceTypes.createFluidKey(resource);
        require(
            !key.isEmpty() && PackageResourceTypes.getFluid(key).getFluid() == fluid,
            "Registered fluid has a native order key: " + BuiltInRegistries.FLUID.getKey(fluid));
        sourceTank.getTankInventory().setFluid(resource);
        destinationTank.getTankInventory().setFluid(FluidStack.EMPTY);
        packager.heldBox = ItemStack.EMPTY;
        packager.animationTicks = 0;
        packager.queuedExitingPackages.clear();
        receiver.animationTicks = 0;
        receiver.queuedExitingPackages.clear();
        ResourcePackagers.triggerStockCheck(packager);
        LogisticsManager.ACCURATE_SUMMARIES.invalidate(network);
        LogisticsManager.SUMMARIES.invalidate(network);
        var order = PackageOrderWithCrafts.simple(List.of(new BigItemStack(key, 137)));
        var planned = LogisticsManager.findPackagersForRequest(network, order, null, "AeroFluid");
        require(
            planned.size() == 1
                && planned.containsKey(packager)
                && planned.values().iterator().next().getCount() == 137,
            "Native Create order plans exactly 137 mB: " + BuiltInRegistries.FLUID.getKey(fluid));
        LogisticsManager.performPackageRequests(planned);
        var cargo = packager.heldBox.copy();
        require(
            FluidPackageContentHelper.getContainedFluid(cargo).getAmount() == 137
                && sourceTank.getTankInventory().getFluidAmount() == 863
                && PackageItem.getAddress(cargo).equals("AeroFluid")
                && PackageItem.hasOrderData(cargo),
            "Packing debits exact volume and carries order metadata");
        require(
            receiver.unwrapBox(cargo, true) && destinationTank.getTankInventory().isEmpty(),
            "Receiving simulation preserves tank");
        require(
            receiver.unwrapBox(cargo, false)
                && destinationTank.getTankInventory().getFluidAmount() == 137,
            "Native receiving packager deposits exact ordered fluid");
      }
      producedSteam(
          level, origin.south(16), sourceTank, destinationTank, packager, receiver, network);
      containers();
      partialReceiver(level, sourceTank, packager, receiver, destinationTank);
      engineDelivery(level, sourceTank, packager, receiver, origin.south(4).east());
      unpackager(level, origin.south(8));
      infiniteTank(level, origin.south(12));
    } finally {
      if (link != null) LogisticallyLinkedBehaviour.remove(link.behaviour);
      LogisticsManager.ACCURATE_SUMMARIES.invalidate(network);
      LogisticsManager.SUMMARIES.invalidate(network);
      for (int index = positions.size() - 1; index >= 0; index--)
        level.setBlockAndUpdate(positions.get(index), previous.get(index));
    }
  }

  private static void producedSteam(
      ServerLevel level,
      BlockPos pos,
      FluidTankBlockEntity source,
      FluidTankBlockEntity destination,
      FluidPackagerBlockEntity sender,
      FluidPackagerBlockEntity receiver,
      UUID network) {
    level.setBlockAndUpdate(pos, block("steampowered:bronze_boiler"));
    var boiler = (BoilerTileEntity) level.getBlockEntity(pos);
    require(boiler != null, "Native producer for logistics steam");
    var input = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
    require(
        input != null
            && input.fill(new FluidStack(Fluids.WATER, 1), IFluidHandler.FluidAction.EXECUTE) == 1,
        "One mB of production water");
    boiler.commitHeat(120);
    boiler.tick();
    var steam = boiler.output.drain(12, IFluidHandler.FluidAction.EXECUTE);
    require(
        steam.getAmount() == 12 && input.getFluidInTank(0).isEmpty(),
        "Native boiler converts one water into twelve steam");
    source.getTankInventory().setFluid(steam);
    destination.getTankInventory().setFluid(FluidStack.EMPTY);
    sender.heldBox = ItemStack.EMPTY;
    sender.animationTicks = 0;
    sender.queuedExitingPackages.clear();
    receiver.animationTicks = 0;
    ResourcePackagers.triggerStockCheck(sender);
    LogisticsManager.ACCURATE_SUMMARIES.invalidate(network);
    LogisticsManager.SUMMARIES.invalidate(network);
    var key = PackageResourceTypes.createFluidKey(steam);
    var order = PackageOrderWithCrafts.simple(List.of(new BigItemStack(key, 12)));
    var planned = LogisticsManager.findPackagersForRequest(network, order, null, "ProducedSteam");
    require(planned.size() == 1, "Actual boiler steam appears in native stock");
    LogisticsManager.performPackageRequests(planned);
    var cargo = sender.heldBox.copy();
    require(
        source.getTankInventory().isEmpty()
            && FluidPackageContentHelper.getContainedFluid(cargo).getAmount() == 12
            && receiver.unwrapBox(cargo, false)
            && destination.getTankInventory().getFluidAmount() == 12
            && destination.getTankInventory().getFluid().is(fluid("steampowered:steam")),
        "Actual boiler steam passes native order, packing and delivery without volume loss");
  }

  private static void containers() {
    var diesel = fluid("createdieselgenerators:diesel");
    var copper =
        new ItemStack(
            BuiltInRegistries.ITEM.get(ResourceLocation.parse("fluidlogistics:copper_bucket")));
    var bucket = copper.getCapability(Capabilities.FluidHandler.ITEM);
    require(bucket != null && bucket.getTankCapacity(0) == 5000, "Native copper bucket capacity");
    require(
        bucket.fill(new FluidStack(diesel, 137), IFluidHandler.FluidAction.SIMULATE) == 137
            && bucket.getFluidInTank(0).isEmpty(),
        "Copper bucket fill simulation");
    require(
        bucket.fill(new FluidStack(diesel, 137), IFluidHandler.FluidAction.EXECUTE) == 137
            && bucket.drain(50, IFluidHandler.FluidAction.EXECUTE).getAmount() == 50
            && bucket.getFluidInTank(0).getAmount() == 87,
        "Copper bucket retains exact partial volume");
    var cargo = FluidPackageContentHelper.createCanonicalPackage(new FluidStack(diesel, 1137));
    var packaged = cargo.getCapability(Capabilities.FluidHandler.ITEM);
    require(
        packaged != null && packaged.drain(999, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
        "Manual package capability requires a full bucket");
    require(
        packaged.drain(1000, IFluidHandler.FluidAction.SIMULATE).getAmount() == 1000
            && packaged.getFluidInTank(0).getAmount() == 1137,
        "Manual package drain simulation");
    require(
        packaged.drain(1000, IFluidHandler.FluidAction.EXECUTE).getAmount() == 1000
            && FluidPackageContentHelper.getContainedFluid(packaged.getContainer()).getAmount()
                == 137,
        "Manual package drain preserves non-bucket remainder for native unpacking");
  }

  private static void partialReceiver(
      ServerLevel level,
      FluidTankBlockEntity source,
      FluidPackagerBlockEntity sender,
      FluidPackagerBlockEntity receiver,
      FluidTankBlockEntity destination) {
    var diesel = fluid("createdieselgenerators:diesel");
    source.getTankInventory().setFluid(new FluidStack(diesel, 137));
    sender.animationTicks = 0;
    sender.heldBox = ItemStack.EMPTY;
    sender.queuedExitingPackages.clear();
    ResourcePackagers.triggerStockCheck(sender);
    sender.attemptToSend(null);
    var cargo = sender.heldBox.copy();
    receiver.animationTicks = 0;
    receiver.queuedExitingPackages.clear();
    destination.getTankInventory().setFluid(FluidStack.EMPTY);
    require(
        receiver.unwrapBox(cargo, true), "Whole package fits before another producer fills tank");
    int capacity = destination.getTankInventory().getCapacity();
    destination.getTankInventory().setFluid(new FluidStack(diesel, capacity - 50));
    require(
        receiver.unwrapBox(cargo, false), "Native execution handles changed remaining capacity");
    require(
        destination.getTankInventory().getFluidAmount() == capacity
            && receiver.queuedExitingPackages.size() == 1
            && FluidPackageContentHelper.getContainedFluid(
                        receiver.queuedExitingPackages.getFirst().stack)
                    .getAmount()
                == 87,
        "50 mB deposited plus 87 mB remainder equals original 137 mB");
    var saved = receiver.saveCustomOnly(level.registryAccess());
    receiver.queuedExitingPackages.clear();
    receiver.loadCustomOnly(saved, level.registryAccess());
    require(
        receiver.queuedExitingPackages.size() == 1
            && FluidPackageContentHelper.getContainedFluid(
                        receiver.queuedExitingPackages.getFirst().stack)
                    .getAmount()
                == 87,
        "Exact queued remainder survives native serialization");
  }

  private static void unpackager(ServerLevel level, BlockPos origin) {
    level.setBlockAndUpdate(origin.east(), block("create:basin"));
    level.setBlockAndUpdate(
        origin,
        block("fluidlogistics:fluid_repackager").setValue(PackagerBlock.FACING, Direction.WEST));
    var unpackager = (FluidRepackagerBlockEntity) level.getBlockEntity(origin);
    require(unpackager != null, "Native dedicated Unpackager exists");
    unpackager.initialize();
    var target =
        level.getCapability(Capabilities.FluidHandler.BLOCK, origin.east(), Direction.WEST);
    require(target != null, "Real processing basin exposes input fluid capability");
    var molten = new FluidStack(fluid("createmetallurgy:molten_steel"), 90);
    var cargo = FluidPackageContentHelper.createCanonicalPackage(molten);
    require(
        unpackager.unwrapBox(cargo, true) && amount(target, molten.getFluid()) == 0,
        "Dedicated Unpackager simulation preserves molten metal");
    require(
        unpackager.unwrapBox(cargo, false) && amount(target, molten.getFluid()) == 90,
        "Dedicated Unpackager feeds exactly one ingot's 90 mB into real basin");
    unpackager.animationTicks = 0;
    target.fill(molten.copyWithAmount(Integer.MAX_VALUE), IFluidHandler.FluidAction.EXECUTE);
    target.fill(new FluidStack(Fluids.WATER, Integer.MAX_VALUE), IFluidHandler.FluidAction.EXECUTE);
    require(
        target.fill(new FluidStack(Fluids.LAVA, 137), IFluidHandler.FluidAction.SIMULATE) == 0,
        "Both native basin input fluid slots are occupied");
    var incompatible =
        FluidPackageContentHelper.createCanonicalPackage(new FluidStack(Fluids.LAVA, 137));
    require(
        unpackager.unwrapBox(incompatible, false),
        "Dedicated Unpackager accepts blocked package for staging");
    unpackager.animationTicks = 0;
    require(unpackager.hasStalledPackageReady(), "Incompatible basin cargo remains recoverable");
    var saved = unpackager.saveCustomOnly(level.registryAccess());
    unpackager.clearContent();
    unpackager.loadCustomOnly(saved, level.registryAccess());
    unpackager.animationTicks = 0;
    var recovered = unpackager.extractStalledPackage(false);
    require(
        FluidPackageContentHelper.getContainedFluid(recovered).getAmount() == 137,
        "Dedicated Unpackager retains exact stalled cargo across serialization");
  }

  private static void engineDelivery(
      ServerLevel level,
      FluidTankBlockEntity source,
      FluidPackagerBlockEntity sender,
      FluidPackagerBlockEntity receiver,
      BlockPos targetPos) {
    level.setBlockAndUpdate(
        targetPos,
        block("createdieselgenerators:diesel_engine")
            .setValue(DieselEngineBlock.FACING, Direction.DOWN));
    receiver.fluidTarget.findNewCapability();
    var target = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, null);
    require(target != null, "Actual diesel engine exposes fuel capability");
    var diesel = fluid("createdieselgenerators:diesel");
    source.getTankInventory().setFluid(new FluidStack(diesel, 137));
    sender.animationTicks = 0;
    sender.heldBox = ItemStack.EMPTY;
    sender.queuedExitingPackages.clear();
    ResourcePackagers.triggerStockCheck(sender);
    sender.attemptToSend(null);
    var cargo = sender.heldBox.copy();
    receiver.animationTicks = 0;
    receiver.queuedExitingPackages.clear();
    require(
        receiver.unwrapBox(cargo, true)
            && receiver.unwrapBox(cargo, false)
            && amount(target, diesel) == 137
            && source.getTankInventory().isEmpty(),
        "Native packager-to-engine delivery conserves exact fuel quantity");
  }

  private static void infiniteTank(ServerLevel level, BlockPos pos) {
    require(
        Config.getInfiniteFluidTankAllowedFluids() == InfiniteTankFluidMode.ALLOW_BY_TAG,
        "Installed server config explicitly restricts Infinite Fluid Tank fluids");
    var originalMode = AllConfigs.server().fluids.bottomlessFluidMode.get();
    AllConfigs.server().fluids.bottomlessFluidMode.set(BottomlessFluidMode.ALLOW_ALL);
    try {
      verifyInfiniteTank(level, pos);
    } finally {
      AllConfigs.server().fluids.bottomlessFluidMode.set(originalMode);
    }
  }

  private static void verifyInfiniteTank(ServerLevel level, BlockPos pos) {
    level.setBlockAndUpdate(pos, block("fluidlogistics:infinite_fluid_tank"));
    var blockEntity = (InfiniteFluidTankBlockEntity) level.getBlockEntity(pos);
    require(blockEntity != null, "Native Infinite Fluid Tank exists");
    var tank = blockEntity.getTankInventory();
    int threshold = InfiniteFluidSupplyRules.getRequiredAmount();
    for (var fluid : BuiltInRegistries.FLUID) {
      if (fluid == Fluids.EMPTY) continue;
      boolean allowed = fluid == Fluids.WATER || fluid == Fluids.LAVA;
      require(
          InfiniteFluidSupplyRules.canSupplyInfinitely(fluid) == allowed,
          "Only water and lava may supply infinitely: " + BuiltInRegistries.FLUID.getKey(fluid));
      tank.setFluid(FluidStack.EMPTY);
      var input = new FluidStack(fluid, threshold);
      require(
          tank.fill(input, IFluidHandler.FluidAction.SIMULATE) == (allowed ? threshold : 0)
              && tank.isEmpty(),
          "Infinite tank simulation follows actual whitelist");
      require(
          tank.fill(input, IFluidHandler.FluidAction.EXECUTE) == (allowed ? threshold : 0),
          "Infinite tank execution follows actual whitelist");
      if (allowed) {
        require(
            tank.drain(1000, IFluidHandler.FluidAction.EXECUTE).getAmount() == 1000
                && tank.getFluidAmount() == threshold,
            "Whitelisted fluid remains natively infinite");
      } else {
        tank.setFluid(input);
        var saved = blockEntity.saveCustomOnly(level.registryAccess());
        tank.setFluid(FluidStack.EMPTY);
        blockEntity.loadCustomOnly(saved, level.registryAccess());
        require(
            !tank.isInfiniteMode() && tank.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
            "Legacy stored industrial fluid cannot bypass whitelist on reload");
        var item = new ItemStack(blockEntity.getBlockState().getBlock());
        item.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(saved));
        var stored = InfiniteFluidTankItem.getContainedFluid(item, level.registryAccess());
        require(
            !InfiniteFluidTankItem.isInfiniteSupply(stored),
            "Stored Infinite Tank item cannot supply industrial fluid infinitely");
      }
    }
  }

  private static BlockState block(String id) {
    var location = ResourceLocation.parse(id);
    require(BuiltInRegistries.BLOCK.containsKey(location), "Registered block " + id);
    return BuiltInRegistries.BLOCK.get(location).defaultBlockState();
  }

  private static Fluid fluid(String id) {
    var location = ResourceLocation.parse(id);
    require(BuiltInRegistries.FLUID.containsKey(location), "Registered fluid " + id);
    return BuiltInRegistries.FLUID.get(location);
  }

  private static int amount(IFluidHandler handler, Fluid fluid) {
    int amount = 0;
    for (int tank = 0; tank < handler.getTanks(); tank++) {
      var stack = handler.getFluidInTank(tank);
      if (stack.getFluid() == fluid) amount += stack.getAmount();
    }
    return amount;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
