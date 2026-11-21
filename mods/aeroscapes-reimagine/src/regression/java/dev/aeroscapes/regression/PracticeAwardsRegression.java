package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import com.li64.tide.data.fishing.mediums.FishingMedium;
import com.li64.tide.registries.TideEntityTypes;
import com.li64.tide.registries.entities.misc.fishing.TideFishingHook;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.mixin.aquatic.TideHookAccess;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.damage.ISSDamageTypes;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

final class PracticeAwardsRegression {
  private PracticeAwardsRegression() {}

  static void verify(ServerLevel level) {
    var position = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 16);
    var area = new AABB(position).inflate(10);
    var existing =
        level.getEntitiesOfClass(Entity.class, area).stream()
            .map(Entity::getUUID)
            .collect(Collectors.toSet());
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "PracticeAwards", Vec3.atBottomCenterOf(position), null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setNoGravity(true);
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        require(
            RaceIdentity.choose(
                    player,
                    Race.HUMAN,
                    0,
                    CharacterClass.RANGER,
                    "bow_stride",
                    Profession.BUILDER,
                    "long_reach")
                && !RaceIdentity.awaitingChoice(player),
            "Finish character creation so item-use ticks run");
        potions(level, player);
        crafting(level, player, position);
        smelting(level, player, position);
        placement(level, player, position);
        harvesting(level, player, position);
        fishing(level, player, position);
        damage(level, player);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
        level.getEntitiesOfClass(Entity.class, area).stream()
            .filter(entity -> entity != player && !existing.contains(entity.getUUID()))
            .forEach(Entity::discard);
      }
    }
  }

  private static void potions(ServerLevel level, ServerPlayer player) {
    long magic = MagicPractice.credit(player);
    long nativeMagic = APIUtils.getXp("magic", player);
    var stand = new SimpleContainer(5);
    var menu = new BrewingStandMenu(1, player.getInventory(), stand, new SimpleContainerData(2));
    for (Item bottle : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
      for (var potion :
          List.of(
              Potions.SWIFTNESS,
              Potions.LONG_SWIFTNESS,
              Potions.STRONG_SWIFTNESS,
              Potions.LONG_NIGHT_VISION)) {
        var stack = PotionContents.createItemStack(bottle, potion);
        String description = stack.getDescriptionId();
        stand.setItem(0, stack.copy());
        require(take(menu.getSlot(0), player, 1).is(bottle), "Brewing result can be taken");
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        require(
            player
                .gameMode
                .useItem(player, level, stack, InteractionHand.MAIN_HAND)
                .consumesAction(),
            "Ordinary potion use succeeds for " + description);
        if (bottle == Items.POTION) {
          require(player.isUsingItem(), "Drinkable potion starts use");
          int useTicks = player.getUseItemRemainingTicks();
          for (int tick = 0; tick < useTicks && player.isUsingItem(); tick++) player.doTick();
          require(player.getMainHandItem().is(Items.GLASS_BOTTLE), "Potion finishes drinking");
        } else {
          require(player.getMainHandItem().isEmpty(), "Throwable potion leaves the hand");
        }
        require(
            APIUtils.getXp("magic", player) == nativeMagic && MagicPractice.credit(player) == magic,
            "Potion brewing, drinking and throwing award no magic practice: " + description);
      }
    }
  }

  private static void crafting(ServerLevel level, ServerPlayer player, BlockPos position) {
    long crafting = APIUtils.getXp("crafting", player);
    long cooking = APIUtils.getXp("cooking", player);
    var menu =
        new CraftingMenu(2, player.getInventory(), ContainerLevelAccess.create(level, position));
    menu.getSlot(1).set(new ItemStack(Items.OAK_LOG, 2));
    require(take(menu.getSlot(0), player, 4).is(Items.OAK_PLANKS), "Craft four planks");
    require(
        menu.getSlot(1).getItem().getCount() == 1
            && APIUtils.getXp("crafting", player) == crafting + 1,
        "One occupied plank ingredient slot yields one XP regardless of stack and output size");
    menu.clearCraftingContent();

    menu.getSlot(1).set(new ItemStack(Items.BOWL));
    menu.getSlot(2).set(new ItemStack(Items.RED_MUSHROOM));
    menu.getSlot(4).set(new ItemStack(Items.BROWN_MUSHROOM));
    require(take(menu.getSlot(0), player, 1).is(Items.MUSHROOM_STEW), "Craft a three-slot meal");
    require(
        APIUtils.getXp("cooking", player) == cooking + 3
            && APIUtils.getXp("crafting", player) == crafting + 1,
        "Food uses occupied ingredient slots and awards cooking");

    for (var pair :
        List.of(
            List.of(Items.IRON_INGOT, Items.IRON_BLOCK),
            List.of(Items.RAW_IRON, Items.RAW_IRON_BLOCK),
            List.of(Items.BONE_MEAL, Items.BONE_BLOCK))) {
      var material = pair.getFirst();
      var block = pair.getLast();
      for (int slot = 1; slot <= 9; slot++) menu.getSlot(slot).set(new ItemStack(material));
      require(take(menu.getSlot(0), player, 1).is(block), "Compress nine " + material);
      menu.getSlot(1).set(new ItemStack(block));
      require(take(menu.getSlot(0), player, 9).is(material), "Unpack " + block);
    }
    require(
        APIUtils.getXp("crafting", player) == crafting + 1
            && APIUtils.getXp("cooking", player) == cooking + 3,
        "The reversible metal-storage cycle awards no XP");
  }

  private static void smelting(ServerLevel level, ServerPlayer player, BlockPos position) {
    long smithing = APIUtils.getXp("smithing", player);
    long cooking = APIUtils.getXp("cooking", player);
    var old = level.getBlockState(position);
    try {
      level.setBlockAndUpdate(position, Blocks.FURNACE.defaultBlockState());
      require(
          level.getBlockEntity(position) instanceof net.minecraft.world.Container,
          "Native furnace inventory exists");
      var furnace = (net.minecraft.world.Container) level.getBlockEntity(position);
      var output = new FurnaceResultSlot(player, furnace, 2, 0, 0);
      output.set(new ItemStack(Items.IRON_INGOT, 5));
      require(take(output, player, 2).getCount() == 2, "Take two of five smelted ingots");
      require(
          output.getItem().getCount() == 3 && APIUtils.getXp("smithing", player) == smithing + 4,
          "Partial output take trains only the two items removed");
      require(take(output, player, 3).getCount() == 3, "Take remaining ingots");
      require(APIUtils.getXp("smithing", player) == smithing + 10, "Full take trains smithing");
      output.set(new ItemStack(Items.COOKED_BEEF, 2));
      require(take(output, player, 2).is(Items.COOKED_BEEF), "Take cooked food");
      require(
          APIUtils.getXp("cooking", player) == cooking + 4
              && APIUtils.getXp("smithing", player) == smithing + 10,
          "A furnace food result trains only cooking");
    } finally {
      level.setBlockAndUpdate(position, old);
    }
  }

  private static void placement(ServerLevel level, ServerPlayer player, BlockPos position) {
    var placed = position.west(2);
    var support = placed.below();
    var oldPlaced = level.getBlockState(placed);
    var oldSupport = level.getBlockState(support);
    long building = APIUtils.getXp("building", player);
    long mining = APIUtils.getXp("mining", player);
    try {
      level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(placed, Blocks.AIR.defaultBlockState());
      var stack = new ItemStack(Items.COBBLESTONE);
      player.setItemInHand(InteractionHand.MAIN_HAND, stack);
      var hit = new BlockHitResult(Vec3.atBottomCenterOf(placed), Direction.UP, support, false);
      require(
          player
                  .gameMode
                  .useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit)
                  .consumesAction()
              && level.getBlockState(placed).is(Blocks.COBBLESTONE)
              && APIUtils.getXp("building", player) == building + 1,
          "Personal placement completes and trains Building");
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
      require(player.gameMode.destroyBlock(placed), "Player can remove the placed block");
      require(APIUtils.getXp("mining", player) == mining, "Placed blocks cannot train Gathering");
    } finally {
      level.setBlockAndUpdate(placed, oldPlaced);
      level.setBlockAndUpdate(support, oldSupport);
    }
  }

  private static void harvesting(ServerLevel level, ServerPlayer player, BlockPos position) {
    var crop = position.east(2);
    var below = crop.below();
    var oldCrop = level.getBlockState(crop);
    var oldSoil = level.getBlockState(below);
    long farming = APIUtils.getXp("farming", player);
    try {
      level.setBlockAndUpdate(below, Blocks.FARMLAND.defaultBlockState());
      level.setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState());
      require(player.gameMode.destroyBlock(crop), "Player can remove immature wheat");
      require(APIUtils.getXp("farming", player) == farming, "Immature wheat awards no XP");
      level.setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
      require(player.gameMode.destroyBlock(crop), "Player can harvest ripe wheat");
      require(
          APIUtils.getXp("farming", player) == farming + 3, "Only mature harvest trains farming");
    } finally {
      level.setBlockAndUpdate(crop, oldCrop);
      level.setBlockAndUpdate(below, oldSoil);
    }
  }

  private static void fishing(ServerLevel level, ServerPlayer player, BlockPos position) {
    long farming = APIUtils.getXp("farming", player);
    var rod = new ItemStack(Items.FISHING_ROD);
    player.setItemInHand(InteractionHand.MAIN_HAND, rod);
    var empty = new TideFishingHook(TideEntityTypes.FISHING_BOBBER, player, level, 0, 0, 1, rod);
    try {
      require(empty.retrieve(rod, level, player) == 0, "Empty cast winds in without a catch");
      require(APIUtils.getXp("farming", player) == farming, "An empty cast awards no XP");
    } finally {
      empty.discard();
    }
    var hook = new TideFishingHook(TideEntityTypes.FISHING_BOBBER, player, level, 0, 0, 1, rod);
    try {
      hook.setPos(Vec3.atCenterOf(position.south(2)));
      var access = (TideHookAccess) hook;
      access.aeroscapes$catchType(TideFishingHook.CatchType.FISH);
      access.aeroscapes$hookedItems(List.of(new ItemStack(Items.COD)));
      access.aeroscapes$medium(FishingMedium.WATER);
      access.aeroscapes$nibble(20);
      require(hook.retrieve(rod, level, player) == 1, "Native Tide retrieval completes a catch");
      require(
          !level
                  .getEntitiesOfClass(
                      ItemEntity.class,
                      new AABB(position).inflate(5),
                      item -> item.getItem().is(Items.COD))
                  .isEmpty()
              && APIUtils.getXp("farming", player) == farming + 6,
          "The landed catch trains farming once");
    } finally {
      hook.discard();
    }
  }

  private static void damage(ServerLevel level, ServerPlayer player) {
    long combat = APIUtils.getXp("combat", player);
    long archery = APIUtils.getXp("archery", player);
    var meleeTarget = EntityType.RAVAGER.create(level);
    var magicTarget = EntityType.RAVAGER.create(level);
    require(meleeTarget != null && magicTarget != null, "Damage targets exist");
    require(
        meleeTarget.hurt(level.damageSources().playerAttack(player), 40)
            && meleeTarget.getHealth() <= meleeTarget.getMaxHealth() - 12,
        "Physical hit delivers at least twelve damage");
    require(
        APIUtils.getXp("combat", player) == combat + 12,
        "Large melee damage awards at most twelve combat XP");
    var spell =
        new DamageSource(level.registryAccess().holderOrThrow(ISSDamageTypes.FIRE_MAGIC), player);
    require(magicTarget.hurt(spell, 20), "Player-attributed spell hits its target");
    require(
        APIUtils.getXp("combat", player) == combat + 12
            && APIUtils.getXp("archery", player) == archery,
        "Magic damage awards neither physical practice");
  }

  private static ItemStack take(Slot slot, ServerPlayer player, int count) {
    var result = slot.remove(count);
    require(!result.isEmpty(), "Native result slot has an output");
    slot.onTake(player, result);
    return result;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
