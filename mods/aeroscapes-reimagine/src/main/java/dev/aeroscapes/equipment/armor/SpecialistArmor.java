package dev.aeroscapes.equipment.armor;

import dev.aeroscapes.races.ClassProgression;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SpecialistArmor {
  private static final String MOD_ID = "aeroscapes_reimagine";
  private static final DeferredRegister<ArmorMaterial> MATERIALS =
      DeferredRegister.create(Registries.ARMOR_MATERIAL, MOD_ID);
  private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

  private SpecialistArmor() {}

  public static void register(IEventBus bus) {
    for (var set : Set.values()) set.register();
    MATERIALS.register(bus);
    ITEMS.register(bus);
    bus.addListener(SpecialistArmor::creativeContents);
  }

  private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
    if (!event.getTabKey().equals(CreativeModeTabs.COMBAT)) return;
    for (var set : Set.values()) {
      for (var piece : set.pieces) event.accept(piece);
    }
  }

  public enum Set {
    WINDWARD("windward", "ranger_armor_training", 2, 5, 5, 2, 16, Items.COPPER_INGOT),
    DUSKSTRIDE("duskstride", "rogue_armor_training", 2, 5, 4, 2, 15, Items.PHANTOM_MEMBRANE);

    private final String id;
    private final String talent;
    private final Map<ArmorItem.Type, Integer> defense;
    private final int durability;
    private final Item repair;
    private final List<DeferredItem<Piece>> pieces = new ArrayList<>();

    Set(
        String id,
        String talent,
        int head,
        int chest,
        int legs,
        int feet,
        int durability,
        Item repair) {
      this.id = id;
      this.talent = talent;
      this.defense =
          Map.of(
              ArmorItem.Type.HELMET, head,
              ArmorItem.Type.CHESTPLATE, chest,
              ArmorItem.Type.LEGGINGS, legs,
              ArmorItem.Type.BOOTS, feet);
      this.durability = durability;
      this.repair = repair;
    }

    public String id() {
      return id;
    }

    public List<Item> items() {
      return pieces.stream().map(piece -> (Item) piece.get()).toList();
    }

    public Component trainingName() {
      return ClassProgression.talentTitle(talent);
    }

    public MutableComponent setEffect() {
      return Component.translatable(ArmorProficiency.TOOLTIP + id + ".set");
    }

    private void register() {
      var material =
          MATERIALS.register(
              id,
              () ->
                  new ArmorMaterial(
                      defense,
                      15,
                      SoundEvents.ARMOR_EQUIP_LEATHER,
                      () -> Ingredient.of(repair),
                      List.of(new ArmorMaterial.Layer(ArmorProficiency.id(id))),
                      0.0F,
                      0.0F));
      for (var slot :
          List.of(
              ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
              ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
        pieces.add(
            ITEMS.register(
                id + "_" + slot.getName(),
                () ->
                    new Piece(
                        this,
                        material,
                        slot,
                        new Item.Properties().durability(slot.getDurability(durability)))));
      }
    }
  }

  static final class Piece extends ArmorItem {
    private final Set set;

    Piece(Set set, Holder<ArmorMaterial> material, Type type, Properties properties) {
      super(material, type, properties);
      this.set = set;
    }

    Set set() {
      return set;
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot slot, LivingEntity entity) {
      return slot == getEquipmentSlot()
          && (!(entity instanceof Player player) || !ArmorProficiency.undertrained(player, stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
        Level level, Player player, InteractionHand hand) {
      var stack = player.getItemInHand(hand);
      if (canEquip(stack, getEquipmentSlot(), player)) return super.use(level, player, hand);
      if (!level.isClientSide()) {
        player.displayClientMessage(
            Component.translatable(ArmorProficiency.TOOLTIP + "equip_denied", set.trainingName()),
            true);
      }
      return InteractionResultHolder.fail(stack);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
      var modifiers = super.getDefaultAttributeModifiers();
      if (set == Set.WINDWARD && getType() == Type.BOOTS) {
        return modifiers.withModifierAdded(
            Attributes.KNOCKBACK_RESISTANCE,
            new AttributeModifier(
                ArmorProficiency.id("windward_footing"),
                0.15,
                AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.FEET);
      }
      if (set == Set.DUSKSTRIDE && getType() == Type.HELMET) {
        return modifiers.withModifierAdded(
            Attributes.SNEAKING_SPEED,
            new AttributeModifier(
                ArmorProficiency.id("duskstride_sneaking"),
                0.50,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            EquipmentSlotGroup.HEAD);
      }
      return modifiers;
    }

    @Override
    public void appendHoverText(
        ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      super.appendHoverText(stack, context, tooltip, flag);
      tooltip.add(
          Component.translatable(getDescriptionId() + ".effect").withStyle(ChatFormatting.GRAY));
      tooltip.add(set.setEffect().withStyle(ChatFormatting.GOLD));
    }
  }
}
