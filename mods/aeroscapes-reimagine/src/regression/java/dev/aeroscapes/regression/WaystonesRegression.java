package dev.aeroscapes.regression;

import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import dev.aeroscapes.travel.TravelIntegration;
import dev.aeroscapes.travel.WaystoneFees;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.waystones.api.TeleportFlags;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.core.WaystoneTeleportContextImpl;
import net.blay09.mods.waystones.core.WaystoneTeleportManager;
import net.blay09.mods.waystones.item.ModItems;
import net.blay09.mods.waystones.menu.ModMenus;
import net.blay09.mods.waystones.menu.WaystoneSelectionListBuilder;
import net.blay09.mods.waystones.menu.WaystoneSelectionMenu;
import net.blay09.mods.waystones.requirement.ExperiencePointsRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Real provider activation, authoritative payment and late-cancellation refund. */
public final class WaystonesRegression {
  private WaystonesRegression() {}

  public static void verify(ServerLevel level) {
    verifyCrafting(level);
    require(WaystoneFees.points(false, 0) == 100, "Zero-distance fee changed");
    require(WaystoneFees.points(false, 1000) == 120, "Exact distance boundary changed");
    require(WaystoneFees.points(false, 1000.01) == 140, "Started distance step was rounded down");
    require(WaystoneFees.points(false, 100000) == 360, "Same-dimension cap changed");
    require(WaystoneFees.points(true, 0) == 360, "Dimension fee changed");
    BlockPos origin = level.getSharedSpawnPos().offset(96, 40, 96);
    BlockPos destination = origin.offset(8, 0, 0);
    Map<BlockPos, BlockState> originals = new HashMap<>();
    for (BlockPos position : new BlockPos[] {origin, destination}) {
      level.getChunkAt(position);
      originals.put(position, level.getBlockState(position));
      originals.put(position.above(), level.getBlockState(position.above()));
    }
    AtomicBoolean cancelArrival = new AtomicBoolean();
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("e24f5b63-1c10-4cb0-a551-fb3b90959e8a"),
            "WaystoneRegression",
            Vec3.atCenterOf(origin).add(0, 0, 1),
            null)) {
      ServerPlayer player = session.player;
      player.getAbilities().instabuild = false;
      Waystone source =
          WaystonesAPI.placeWaystone(level, origin, WaystoneStyles.DEFAULT).orElseThrow();
      Waystone target =
          WaystonesAPI.placeWaystone(level, destination, WaystoneStyles.MOSSY).orElseThrow();
      try {
        verifyReach(player, source);
        var state = level.getBlockState(origin);
        var blockEntity = level.getBlockEntity(origin);
        require(
            Block.getDrops(
                    state, level, origin, blockEntity, player, new ItemStack(Items.DIAMOND_PICKAXE))
                .stream()
                .anyMatch(stack -> stack.is(state.getBlock().asItem())),
            "Crafted stone loot disappeared");
        require(
            Block.getDrops(
                    state.setValue(WaystoneBlockBase.ORIGIN, WaystoneOrigin.WILDERNESS),
                    level,
                    origin,
                    blockEntity,
                    player,
                    new ItemStack(Items.DIAMOND_PICKAXE))
                .isEmpty(),
            "Natural stone loot bypassed direct loot collection");
        PlayerWaystoneManager.activateWaystone(player, source);
        require(!WaystonesAPI.isWaystoneActivated(player, source), "Activation was free");
        player.getInventory().add(new ItemStack(TravelIntegration.WAYSTONE_SIGIL.get(), 2));
        PlayerWaystoneManager.activateWaystone(player, source);
        require(WaystonesAPI.isWaystoneActivated(player, source), "Paid activation failed");
        require(
            player.getInventory().countItem(TravelIntegration.WAYSTONE_SIGIL.get()) == 1,
            "Activation did not consume exactly one sigil");
        PlayerWaystoneManager.activateWaystone(player, source);
        require(
            player.getInventory().countItem(TravelIntegration.WAYSTONE_SIGIL.get()) == 1,
            "Known UUID charged another sigil");
        PlayerWaystoneManager.activateWaystone(player, target);
        require(!WaystonesAPI.isWaystoneActivated(player, target), "Remote activation was allowed");
        require(
            player.getInventory().countItem(TravelIntegration.WAYSTONE_SIGIL.get()) == 1,
            "Failed remote activation consumed the sigil");
        player.absMoveTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 1.5);
        PlayerWaystoneManager.activateWaystone(player, target);
        player.absMoveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 1.5);
        player.experienceLevel = 0;
        player.experienceProgress = 0;
        player.totalExperience = 0;
        player.giveExperiencePoints(120);
        require(
            TravelIntegration.experiencePoints(player) == 120, "XP fixture did not start exact");
        WaystoneTeleportContext portable = context(player, source, target);
        portable.setWarpItem(new ItemStack(Items.PAPER));
        require(
            !WaystoneTeleportManager.tryTeleport(portable).isSuccessful(),
            "Portable route survived");
        WaystoneTeleportContext inventory = context(player, source, target);
        inventory.addFlag(TeleportFlags.INVENTORY_BUTTON);
        require(
            !WaystoneTeleportManager.tryTeleport(inventory).isSuccessful(),
            "Inventory route survived");
        require(TravelIntegration.experiencePoints(player) == 120, "Denied route charged XP");
        player.giveExperiencePoints(-1);
        require(
            !WaystoneTeleportManager.tryTeleport(context(player, source, target)).isSuccessful(),
            "Insufficient experience did not reject travel");
        require(
            TravelIntegration.experiencePoints(player) == 119,
            "Insufficient experience changed the point budget");
        player.giveExperiencePoints(1);
        require(
            WaystoneTeleportManager.tryTeleport(context(player, source, target)).isSuccessful(),
            "Insufficient experience left a pending payment blocking immediate retry");
        require(
            TravelIntegration.experiencePoints(player) == 0,
            "Immediate retry did not charge exactly once");
        player.absMoveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 1.5);
        player.giveExperiencePoints(120);
        Balm.getEvents()
            .onEvent(
                WaystoneTeleportEntityEvent.Pre.class,
                event -> {
                  if (cancelArrival.get() && event.getEntity() == player) event.setCanceled(true);
                },
                EventPriority.Lowest);
        cancelArrival.set(true);
        require(
            !WaystoneTeleportManager.tryTeleport(context(player, source, target)).isSuccessful(),
            "Late cancellation did not cancel arrival");
        require(TravelIntegration.experiencePoints(player) == 120, "Failed arrival lost XP");
        cancelArrival.set(false);
        require(
            WaystoneTeleportManager.tryTeleport(context(player, source, target)).isSuccessful(),
            "Paid physical travel failed");
        require(
            TravelIntegration.experiencePoints(player) == 0, "Successful travel charged wrong XP");
        player.absMoveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 1.5);
        player.setGameMode(GameType.CREATIVE);
        require(
            WaystoneTeleportManager.tryTeleport(context(player, source, target)).isSuccessful(),
            "Native creative bypass refused a physical trip with zero XP");
        require(TravelIntegration.experiencePoints(player) == 0, "Creative travel charged XP");
        player.setGameMode(GameType.SURVIVAL);
        verifyShip(level, player, source, target);
        verifyDimensional(level, player, source);
      } finally {
        cancelArrival.set(false);
        WaystonesAPI.deactivateWaystone(player, source);
        WaystonesAPI.deactivateWaystone(player, target);
      }
    } finally {
      for (var entry : originals.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
    }
  }

  private static void verifyDimensional(ServerLevel level, ServerPlayer player, Waystone source) {
    ServerLevel nether = level.getServer().getLevel(Level.NETHER);
    require(nether != null, "Dimensional travel fixture requires the normal Nether level");
    BlockPos destination = new BlockPos(source.getPos().getX(), 200, source.getPos().getZ());
    Map<BlockPos, BlockState> originals = new HashMap<>();
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        for (int y = -1; y <= 2; y++) {
          BlockPos position = destination.offset(x, y, z);
          nether.getChunkAt(position);
          require(
              nether.getBlockEntity(position) == null,
              "Nether travel fixture would replace an existing block entity");
          originals.put(position, nether.getBlockState(position));
        }
      }
    }
    Waystone target = null;
    Vec3 home = Vec3.atCenterOf(source.getPos()).add(0, 0, 1);
    try {
      for (BlockPos position : originals.keySet()) {
        nether.setBlock(
            position,
            position.getY() == destination.getY() - 1
                ? Blocks.OBSIDIAN.defaultBlockState()
                : Blocks.AIR.defaultBlockState(),
            3);
      }
      target =
          WaystonesAPI.placeWaystone(nether, destination, WaystoneStyles.BLACKSTONE).orElseThrow();
      int initialSigils = player.getInventory().countItem(TravelIntegration.WAYSTONE_SIGIL.get());
      require(
          initialSigils == 0, "Dimensional fixture must start without unused activation sigils");
      player.getInventory().add(new ItemStack(TravelIntegration.WAYSTONE_SIGIL.get()));
      PlayerWaystoneManager.activateWaystone(player, target);
      require(
          !WaystonesAPI.isWaystoneActivated(player, target)
              && player.getInventory().countItem(TravelIntegration.WAYSTONE_SIGIL.get()) == 1,
          "A Nether destination was activated or charged without a physical visit");
      require(
          player.teleportTo(
              nether,
              destination.getX() + 0.5,
              destination.getY(),
              destination.getZ() + 1.5,
              Set.of(),
              0,
              0),
          "Cannot stage the physical Nether activation visit");
      PlayerWaystoneManager.activateWaystone(player, target);
      require(
          WaystonesAPI.isWaystoneActivated(player, target)
              && player.getInventory().countItem(TravelIntegration.WAYSTONE_SIGIL.get()) == 0,
          "Physical Nether activation did not consume exactly one sigil");
      require(
          player.teleportTo(level, home.x, home.y, home.z, Set.of(), 0, 0),
          "Cannot stage the paid dimensional departure");
      player.experienceLevel = 0;
      player.experienceProgress = 0;
      player.totalExperience = 0;
      player.giveExperiencePoints(359);
      MenuChoice outward = menuChoice(level, player, source, target);
      require(outward.points() == 360, "Native Nether menu quote was not 360 points");
      require(
          !WaystoneTeleportManager.tryTeleport(context(player, outward.source(), outward.target()))
              .isSuccessful(),
          "359 points purchased a dimensional journey");
      require(
          player.serverLevel() == level && TravelIntegration.experiencePoints(player) == 359,
          "Rejected dimensional journey moved the player or charged points");
      player.giveExperiencePoints(1);
      require(
          WaystoneTeleportManager.tryTeleport(context(player, outward.source(), outward.target()))
              .isSuccessful(),
          "360 points did not purchase the physical Nether journey");
      require(
          player.serverLevel() == nether
              && nether.getPlayerByUUID(player.getUUID()) == player
              && TravelIntegration.experiencePoints(player) == 0,
          "Nether journey did not register the player and consume exactly 360 points");
      require(
          player.position().distanceToSqr(Vec3.atCenterOf(destination)) < 16,
          "Dimensional destination ignored the physical Nether stone");
      player.giveExperiencePoints(360);
      MenuChoice returning = menuChoice(nether, player, target, source);
      require(returning.points() == 360, "Native return menu quote was not 360 points");
      require(
          WaystoneTeleportManager.tryTeleport(
                  context(player, returning.source(), returning.target()))
              .isSuccessful(),
          "Paid Nether-to-Overworld return failed");
      require(
          player.serverLevel() == level
              && level.getPlayerByUUID(player.getUUID()) == player
              && TravelIntegration.experiencePoints(player) == 0,
          "Return journey did not restore the player's dimension and exact point budget");
    } finally {
      try {
        if (player.serverLevel() != level) {
          require(
              player.teleportTo(level, home.x, home.y, home.z, Set.of(), 0, 0),
              "Could not restore the dimensional fixture player");
        }
      } finally {
        try {
          if (target != null) {
            UUID targetId = target.getWaystoneUid();
            WaystonesAPI.deactivateWaystone(player, target);
            nether.removeBlock(destination.above(), false);
            nether.removeBlock(destination, false);
            WaystonesAPI.getWaystone(level.getServer(), targetId)
                .ifPresent(
                    stone -> WaystonesAPI.removeWaystoneFromDatabase(level.getServer(), stone));
            require(
                WaystonesAPI.getWaystone(level.getServer(), targetId).isEmpty(),
                "Temporary Nether stone remains in the provider registry");
          }
        } finally {
          for (var entry : originals.entrySet()) {
            nether.setBlock(entry.getKey(), entry.getValue(), 3);
          }
        }
      }
    }
  }

  private static void verifyReach(ServerPlayer player, Waystone source) {
    Vec3 previous = player.position();
    BlockPos lower = source.getPos();
    double reach = player.blockInteractionRange();
    double eyeHeight = player.getEyeHeight();
    try {
      player.absMoveTo(
          lower.getX() + 0.5, lower.getY() - reach + 0.02 - eyeHeight, lower.getZ() + 0.5);
      require(player.canInteractWithBlock(lower, 0), "Lower-edge reach fixture is not legal");
      require(
          TravelIntegration.atStone(player, source),
          "Physical source gate rejected native legal eye-to-block reach below the stone");
      player.absMoveTo(player.getX(), player.getY() - 0.04, player.getZ());
      require(
          !player.canInteractWithBlock(lower, 0) && !player.canInteractWithBlock(lower.above(), 0),
          "Outside-reach fixture still intersects a stone half");
      require(
          !TravelIntegration.atStone(player, source),
          "Physical source gate allowed travel beyond native reach");
      player.absMoveTo(
          lower.getX() + 0.5, lower.getY() + 2 + reach - 0.02 - eyeHeight, lower.getZ() + 0.5);
      require(
          player.canInteractWithBlock(lower.above(), 0), "Upper-edge reach fixture is not legal");
      require(
          TravelIntegration.atStone(player, source),
          "Physical source gate rejected native legal reach to the upper stone half");
    } finally {
      player.absMoveTo(previous.x, previous.y, previous.z);
    }
  }

  private static void verifyCrafting(ServerLevel level) {
    for (String disabled :
        List.of(
            "warp_scroll",
            "return_scroll",
            "portal_scroll",
            "blank_scroll",
            "warp_plate",
            "red_sharestone",
            "red_portstone",
            "twinbound_feather")) {
      require(
          level.getRecipeManager().byKey(ResourceLocation.parse("waystones:" + disabled)).isEmpty(),
          "Disabled teleport route remains craftable: " + disabled);
    }
    require(
        level.getRecipeManager().byKey(ResourceLocation.parse("waystones:warp_stone")).isPresent(),
        "The ordinary waystone's crafting intermediate is missing");
    CraftingInput input =
        CraftingInput.of(
            3,
            3,
            List.of(
                ItemStack.EMPTY,
                new ItemStack(Items.STONE_BRICKS),
                ItemStack.EMPTY,
                new ItemStack(Items.STONE_BRICKS),
                new ItemStack(ModItems.warpStone),
                new ItemStack(Items.STONE_BRICKS),
                new ItemStack(Items.OBSIDIAN),
                new ItemStack(Items.OBSIDIAN),
                new ItemStack(Items.OBSIDIAN)));
    var recipe =
        level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
    require(
        recipe.id().equals(ResourceLocation.parse("waystones:waystone")),
        "Ordinary waystone ingredients match an unexpected recipe");
    ItemStack result = recipe.value().assemble(input, level.registryAccess());
    require(
        result.getCount() == 1
            && result.is(net.blay09.mods.waystones.block.ModBlocks.waystone.asItem()),
        "Ordinary waystone output changed");
    require(
        recipe.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
        "Waystone crafting returned a consumed ingredient");
  }

  private static void verifyShip(
      ServerLevel level, ServerPlayer player, Waystone land, Waystone target) {
    UUID targetId = target.getWaystoneUid();
    var originalActivation = player.saveWithoutId(new CompoundTag());
    ServerSubLevel ship =
        SableWaystoneCompat.assembleWaystone(level, target.getPos()).orElseThrow();
    var container = SubLevelContainer.getContainer(level);
    try {
      Waystone moved = WaystonesAPI.getWaystone(level.getServer(), targetId).orElseThrow();
      require(moved.getWaystoneUid().equals(targetId), "Assembly changed the paid stone UUID");
      require(WaystonesAPI.isWaystoneActivated(player, moved), "Assembly lost paid discovery");
      player.load(originalActivation);
      require(
          WaystonesAPI.isWaystoneActivated(player, moved), "Player serialization lost discovery");
      var blockEntity = level.getBlockEntity(moved.getPos());
      require(blockEntity != null, "Assembled stone has no block entity");
      var savedBlockEntity = blockEntity.saveWithFullMetadata(level.registryAccess());
      blockEntity.loadWithComponents(savedBlockEntity, level.registryAccess());
      require(
          WaystonesAPI.getWaystoneAt(level, moved.getPos())
              .orElseThrow()
              .getWaystoneUid()
              .equals(targetId),
          "Block entity serialization changed the paid stone UUID");
      ship.logicalPose().orientation().set(new Quaterniond().rotateY(Math.PI / 4));
      Vec3 landCenter = visibleStoneCenter(level, land);
      Vec3 desired = landCenter.add(1000, 0, 0);
      Vec3 before = SableWaystoneCompat.getVisibleWaystonePos(level, moved);
      ship.logicalPose()
          .position()
          .add(desired.x - before.x, desired.y - before.y, desired.z - before.z);
      container
          .physicsSystem()
          .getPipeline()
          .teleport(
              ship, new Vector3d(ship.logicalPose().position()), ship.logicalPose().orientation());
      ship.updateLastPose();
      ship.updateBoundingBox();
      ship.forceUpdateGlobalBounds();
      Vec3 visible = SableWaystoneCompat.getVisibleWaystonePos(level, moved);
      Vec3 shipCenter = visibleStoneCenter(level, moved);
      require(
          visible.distanceToSqr(desired) < 0.01,
          "Moved pose did not project the stone to world coordinates");
      player.absMoveTo(landCenter.x - 2, land.getPos().getY(), landCenter.z);
      player.experienceLevel = 0;
      player.experienceProgress = 0;
      player.totalExperience = 0;
      player.giveExperiencePoints(1000);
      int outward =
          WaystoneFees.points(
              false, Math.hypot(landCenter.x - shipCenter.x, landCenter.z - shipCenter.z));
      require(outward == 120, "Normalized stone-to-stone distance crossed the exact 1000 boundary");
      require(
          WaystoneFees.points(
                  false, Math.hypot(player.getX() - visible.x, player.getZ() - visible.z))
              == 140,
          "Boundary fixture did not distinguish player position from source stone position");
      MenuChoice outgoingMenu = menuChoice(level, player, land, moved);
      Vec3 menuTarget = Vec3.atCenterOf(outgoingMenu.target().getPos());
      require(
          Math.hypot(menuTarget.x - visible.x, menuTarget.z - visible.z) <= 1,
          "Native menu payload did not project the ship target to visible world coordinates");
      int displayed =
          WaystoneFees.points(
              false,
              Math.hypot(
                  outgoingMenu.source().getPos().getCenter().x - menuTarget.x,
                  outgoingMenu.source().getPos().getCenter().z - menuTarget.z));
      require(
          outgoingMenu.points() == outward && displayed == outward,
          "Native menu quote disagrees with the visible ship distance");
      player.absMoveTo(landCenter.x + 2, land.getPos().getY(), landCenter.z);
      require(
          menuChoice(level, player, land, moved).points() == outward,
          "Walking within source reach changed the fixed stone-to-stone quote");
      player.absMoveTo(landCenter.x - 2, land.getPos().getY(), landCenter.z);
      var toShip = context(player, outgoingMenu.source(), outgoingMenu.target());
      require(
          WaystoneTeleportManager.tryTeleport(toShip).isSuccessful(), "Land-to-ship travel failed");
      require(
          player.position().distanceToSqr(visible) < 16,
          "Land-to-ship arrival used plot coordinates");
      require(
          TravelIntegration.experiencePoints(player) == 1000 - outward,
          "Land-to-ship payment mismatch");
      long beforeReturn = TravelIntegration.experiencePoints(player);
      int returning =
          WaystoneFees.points(
              false, Math.hypot(shipCenter.x - landCenter.x, shipCenter.z - landCenter.z));
      MenuChoice returnMenu = menuChoice(level, player, moved, land);
      require(
          WaystoneFees.points(
                  false,
                  Math.hypot(
                      returnMenu.source().getPos().getCenter().x - landCenter.x,
                      returnMenu.source().getPos().getCenter().z - landCenter.z))
              == 360,
          "Ship-source wire fixture did not distinguish storage coordinates from world coordinates");
      require(
          returnMenu.points() == returning,
          "Native quote disagrees with normalized source and destination stone positions");
      require(
          WaystoneTeleportManager.tryTeleport(
                  context(player, returnMenu.source(), returnMenu.target()))
              .isSuccessful(),
          "Ship-to-land physical source validation failed");
      require(
          TravelIntegration.experiencePoints(player) == beforeReturn - returning,
          "Ship-to-land payment mismatch");
      require(
          player.position().distanceToSqr(Vec3.atCenterOf(land.getPos())) < 16,
          "Ship-to-land arrival incorrect");
    } finally {
      var moved = WaystonesAPI.getWaystone(level.getServer(), targetId).orElse(null);
      if (moved != null) {
        level.removeBlock(moved.getPos().above(), false);
        level.removeBlock(moved.getPos(), false);
      }
      container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
    }
  }

  private static Vec3 visibleStoneCenter(ServerLevel level, Waystone stone) {
    return Vec3.atCenterOf(
        BlockPos.containing(SableWaystoneCompat.getVisibleWaystonePos(level, stone)));
  }

  private static MenuChoice menuChoice(
      ServerLevel level, ServerPlayer player, Waystone source, Waystone target) {
    var provider =
        new WaystoneSelectionListBuilder(player)
            .withTargetsForWaystone(source)
            .buildMenuProvider(
                ModMenus.waystoneSelection.get(),
                Component.translatable("container.waystones.waystone_selection"));
    Object openingData = provider.getScreenOpeningData(player);
    require(
        openingData instanceof WaystoneSelectionMenu.Data,
        "Native menu provider returned unexpected screen data");
    var buffer =
        new RegistryFriendlyByteBuf(
            Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
    try {
      WaystoneSelectionMenu.STREAM_CODEC.encode(buffer, (WaystoneSelectionMenu.Data) openingData);
      var received = WaystoneSelectionMenu.STREAM_CODEC.decode(buffer);
      Waystone receivedSource = received.fromWaystone().orElseThrow();
      require(
          receivedSource.getWaystoneUid().equals(source.getWaystoneUid()),
          "Menu wire format changed the physical source UUID");
      Waystone receivedTarget =
          received.waystones().stream()
              .filter(candidate -> candidate.getWaystoneUid().equals(target.getWaystoneUid()))
              .findFirst()
              .orElseThrow();
      int serverQuote = quotedPoints(context(player, receivedSource, receivedTarget));
      var serverless = new ServerlessArmorStand(level, player.position());
      var projectedContext =
          new WaystoneTeleportContextImpl(serverless, receivedTarget)
              .setFromWaystone(receivedSource);
      require(
          quotedPoints(projectedContext) == serverQuote,
          "Serverless menu quote did not project native source storage coordinates");
      return new MenuChoice(receivedSource, receivedTarget, serverQuote);
    } finally {
      buffer.release();
    }
  }

  private static int quotedPoints(WaystoneTeleportContext context) {
    var requirement = WaystonesAPI.resolveRequirements(context);
    require(
        requirement instanceof ExperiencePointsRequirement,
        "Native menu requirements did not produce a point quote");
    return ((ExperiencePointsRequirement) requirement).getPoints();
  }

  private static final class ServerlessArmorStand extends ArmorStand {
    private ServerlessArmorStand(ServerLevel level, Vec3 position) {
      super(level, position.x, position.y, position.z);
    }

    @Override
    @javax.annotation.Nullable
    public MinecraftServer getServer() {
      return null;
    }
  }

  private record MenuChoice(Waystone source, Waystone target, int points) {}

  private static WaystoneTeleportContext context(
      ServerPlayer player, Waystone source, Waystone target) {
    return WaystonesAPI.createUncheckedDefaultTeleportContext(
            player, target, context -> context.setFromWaystone(source))
        .left()
        .orElseThrow();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
