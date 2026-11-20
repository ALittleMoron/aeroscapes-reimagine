package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;
import top.theillusivec4.curios.api.event.CurioCanUnequipEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

final class AccessoryUseRegression {
  private AccessoryUseRegression() {}

  static void verify(ServerLevel level) {
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.randomUUID(),
            "AccessoryFixture",
            Vec3.atBottomCenterOf(level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 3)),
            null)) {
      var player = session.player;
      require(
          RaceIdentity.choose(
              player,
              Race.HUMAN,
              0,
              CharacterClass.WARRIOR,
              "quick_block",
              Profession.BLACKSMITH,
              "material_repair"),
          "Character ready for use");
      var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
      checkCatalogue(player, inventory);
      var activeStates = new HashMap<String, List<Boolean>>();
      inventory
          .getCurios()
          .forEach(
              (id, slot) -> {
                var active = slot.getActiveStates();
                activeStates.put(id, List.copyOf(active));
                active.replaceAll(ignored -> false);
              });
      try {
        checkTransfer(player, inventory);
      } finally {
        inventory
            .getCurios()
            .forEach(
                (id, slot) -> {
                  var saved = activeStates.get(id);
                  for (int i = 0; i < saved.size(); i++)
                    slot.getActiveStates().set(i, saved.get(i));
                });
      }
      checkActiveUse(player, inventory);
      checkBlockUse(player, inventory);
    }
  }

  private static void checkCatalogue(ServerPlayer player, ICuriosItemHandler inventory) {
    var items = new LinkedHashSet<Item>();
    BuiltInRegistries.ITEM
        .getTags()
        .filter(pair -> pair.getFirst().location().getNamespace().equals("curios"))
        .forEach(pair -> pair.getSecond().forEach(holder -> items.add(holder.value())));
    int wearable = 0, tagOnly = 0;
    var equipped = inventory.getEquippedCurios();
    for (int index = 0; index < equipped.getSlots(); index++)
      equipped.setStackInSlot(index, ItemStack.EMPTY);
    for (Item item : items) {
      var stack = new ItemStack(item);
      player.setItemInHand(InteractionHand.MAIN_HAND, stack);
      boolean allowed = false;
      for (var slot : inventory.getCurios().values())
        for (int i = 0; i < slot.getStacks().getSlots(); i++)
          if (i < slot.getActiveStates().size()
              && slot.getActiveStates().get(i)
              && slot.getStacks().isItemValid(i, stack)) allowed = true;
      if (!allowed) continue;
      wearable++;
      if (CuriosApi.getCurio(stack).isEmpty()) tagOnly++;
      use(player, InteractionHand.MAIN_HAND, true);
      var found = inventory.findCurios(wornStack -> wornStack.is(item));
      require(
          player.getMainHandItem().isEmpty() && found.size() == 1,
          "Wear one manually valid accessory by use: " + BuiltInRegistries.ITEM.getKey(item));
      var where = found.getFirst().slotContext();
      inventory.setEquippedCurio(where.identifier(), where.index(), ItemStack.EMPTY);
    }
    require(wearable > 0 && tagOnly > 0, "Catalogue includes tag-only accessories");
    LogUtils.getLogger()
        .info(
            "Accessory use verified {} manually wearable tagged items including {} without an item"
                + " capability",
            wearable,
            tagOnly);
  }

  private static void checkTransfer(ServerPlayer player, ICuriosItemHandler inventory) {
    var ring = BuiltInRegistries.ITEM.get(ResourceLocation.parse("irons_spellbooks:silver_ring"));
    var rings = inventory.getStacksHandler("ring").orElseThrow();
    var stacks = rings.getStacks();
    var source = new ItemStack(ring, 3);
    source.set(DataComponents.MAX_STACK_SIZE, 16);
    source.set(DataComponents.CUSTOM_NAME, Component.literal("Fixture set"));
    player.setItemInHand(InteractionHand.OFF_HAND, source);
    use(player, InteractionHand.OFF_HAND, false);
    require(
        player.getOffhandItem().getCount() == 3
            && inventory.findCurios(wornStack -> wornStack.is(ring)).isEmpty(),
        "Inactive slots unchanged: held="
            + player.getOffhandItem()
            + ", worn="
            + inventory.findCurios(wornStack -> wornStack.is(ring)));
    rings.getActiveStates().set(0, true);
    Consumer<CurioCanEquipEvent> deny =
        event -> {
          if (event.getEntity() == player) event.setEquipResult(TriState.FALSE);
        };
    NeoForge.EVENT_BUS.addListener(CurioCanEquipEvent.class, deny);
    try {
      use(player, InteractionHand.OFF_HAND, false);
      require(
          player.getOffhandItem().getCount() == 3 && stacks.getStackInSlot(0).isEmpty(),
          "Equip event veto");
    } finally {
      NeoForge.EVENT_BUS.unregister(deny);
    }
    use(player, InteractionHand.OFF_HAND, false);
    var worn = stacks.getStackInSlot(0);
    require(
        worn.getCount() == 1
            && ItemStack.isSameItemSameComponents(worn, source)
            && player.getOffhandItem().getCount() == 2,
        "One customized item transferred from offhand");
    use(player, InteractionHand.OFF_HAND, false);
    require(
        stacks.getStackInSlot(0) == worn && player.getOffhandItem().getCount() == 2,
        "Full slot leaves remainder and equipped copy intact");
    var replacement = new ItemStack(ring);
    player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
    Consumer<CurioCanUnequipEvent> locked =
        event -> {
          if (event.getEntity() == player) event.setUnequipResult(TriState.FALSE);
        };
    NeoForge.EVENT_BUS.addListener(CurioCanUnequipEvent.class, locked);
    try {
      use(player, InteractionHand.MAIN_HAND, false);
      require(
          stacks.getStackInSlot(0) == worn && player.getMainHandItem() == replacement,
          "Unequip event veto");
    } finally {
      NeoForge.EVENT_BUS.unregister(locked);
    }
    var binding = worn.copy();
    var registry = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    binding.enchant(registry.getHolderOrThrow(Enchantments.BINDING_CURSE), 1);
    stacks.setStackInSlot(0, binding);
    use(player, InteractionHand.MAIN_HAND, false);
    require(
        stacks.getStackInSlot(0) == binding && player.getMainHandItem() == replacement,
        "Binding prevents replacement");
    worn.enchant(registry.getHolderOrThrow(Enchantments.VANISHING_CURSE), 1);
    stacks.setStackInSlot(0, worn);
    use(player, InteractionHand.MAIN_HAND, false);
    require(
        ItemStack.isSameItemSameComponents(stacks.getStackInSlot(0), replacement)
            && ItemStack.isSameItemSameComponents(player.getMainHandItem(), worn)
            && player.getOffhandItem().getCount() == 2,
        "Swap returns original including vanishing to the correct hand");
    var same = replacement.copy();
    var installed = stacks.getStackInSlot(0);
    player.setItemInHand(InteractionHand.MAIN_HAND, same);
    use(player, InteractionHand.MAIN_HAND, true);
    require(
        stacks.getStackInSlot(0) == installed && player.getMainHandItem() == same,
        "Repeated deliberate use does not replace the same accessory");
    var secondSlot =
        new AttributeModifier(
            ResourceLocation.parse("aeroscapes_regression:second_ring"),
            1,
            AttributeModifier.Operation.ADD_VALUE);
    rings.addTransientModifier(secondSlot);
    try {
      rings.getActiveStates().set(1, true);
      var different = new ItemStack(ring);
      different.set(DataComponents.CUSTOM_NAME, Component.literal("Second ring"));
      player.setItemInHand(InteractionHand.MAIN_HAND, different);
      use(player, InteractionHand.MAIN_HAND, true);
      require(
          stacks.getStackInSlot(0) == installed
              && ItemStack.isSameItemSameComponents(stacks.getStackInSlot(1), different)
              && player.getMainHandItem().isEmpty(),
          "Free functional slot wins over a replaceable one");
      for (int i = 0; i < rings.getCosmeticStacks().getSlots(); i++)
        require(rings.getCosmeticStacks().getStackInSlot(i).isEmpty(), "Cosmetic slots untouched");
      stacks.setStackInSlot(1, ItemStack.EMPTY);
    } finally {
      rings.removeModifier(secondSlot.id());
    }
    stacks.setStackInSlot(0, ItemStack.EMPTY);
  }

  private static void checkActiveUse(ServerPlayer player, ICuriosItemHandler inventory) {
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
    use(player, InteractionHand.MAIN_HAND, false);
    require(
        player.isUsingItem()
            && player.getMainHandItem().is(Items.SPYGLASS)
            && inventory.findCurios(wornStack -> wornStack.is(Items.SPYGLASS)).isEmpty(),
        "Ordinary use looks through the spyglass");
    player.stopUsingItem();
    use(player, InteractionHand.MAIN_HAND, true);
    require(
        !player.isUsingItem()
            && player.getMainHandItem().isEmpty()
            && inventory.findCurios(wornStack -> wornStack.is(Items.SPYGLASS)).size() == 1,
        "Crouching wears spyglass in belt");
  }

  private static void use(ServerPlayer player, InteractionHand hand, boolean crouched) {
    player.setShiftKeyDown(crouched);
    player.gameMode.useItem(player, player.serverLevel(), player.getItemInHand(hand), hand);
  }

  private static void checkBlockUse(ServerPlayer player, ICuriosItemHandler inventory) {
    var level = player.serverLevel();
    var floor = player.blockPosition().east(2).below();
    var target = floor.above();
    var previousFloor = level.getBlockState(floor);
    require(level.isEmptyBlock(target), "Disposable placement space is empty");
    var backpackId = ResourceLocation.parse("sophisticatedbackpacks:backpack");
    var pack = BuiltInRegistries.ITEM.getOptional(backpackId).orElseThrow();
    var block = BuiltInRegistries.BLOCK.getOptional(backpackId).orElseThrow();
    level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
    try {
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(pack));
      player.setShiftKeyDown(true);
      var hit =
          new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
      var result =
          player.gameMode.useItemOn(
              player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
      require(
          result.consumesAction()
              && level.getBlockState(target).is(block)
              && player.getMainHandItem().isEmpty()
              && inventory.findCurios(wornStack -> wornStack.is(pack)).isEmpty(),
          "Expected a placed backpack at "
              + target
              + ", got "
              + level.getBlockState(target)
              + ", hand="
              + player.getMainHandItem()
              + ", result="
              + result);
    } finally {
      level.removeBlock(target, false);
      level.setBlockAndUpdate(floor, previousFloor);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
