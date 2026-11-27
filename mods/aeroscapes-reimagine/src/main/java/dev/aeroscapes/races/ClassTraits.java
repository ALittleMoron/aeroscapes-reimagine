package dev.aeroscapes.races;

import static dev.aeroscapes.races.ClassProgression.hasCombatTalent;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ClassTraits {
  public static final int WARRIOR_ARMOR = 2;
  public static final int MAGE_MANA = 20;
  private static final DeferredRegister<Attribute> ATTRIBUTES =
      DeferredRegister.create(Registries.ATTRIBUTE, "aeroscapes_reimagine");
  private static final DeferredHolder<Attribute, Attribute> SHIELD_RAISE_REDUCTION =
      ATTRIBUTES.register(
          "shield_raise_reduction",
          () ->
              new RangedAttribute("attribute.aeroscapes_reimagine.shield_raise_reduction", 0, 0, 2)
                  .setSyncable(true));
  private static final TagKey<Item> LIGHT_WEAPONS =
      TagKey.create(Registries.ITEM, ResourceLocation.parse("aeroscapes_reimagine:light_weapons"));
  private static final AttributeModifier ARMOR =
      modifier("warrior_armor", WARRIOR_ARMOR, AttributeModifier.Operation.ADD_VALUE);
  private static final AttributeModifier MANA =
      modifier("mage_mana", MAGE_MANA, AttributeModifier.Operation.ADD_VALUE);
  private static final AttributeModifier RECOVERY =
      modifier("quick_recovery", 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
  private static final AttributeModifier STRIDE =
      modifier("martial_stride", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

  private ClassTraits() {}

  private static AttributeModifier modifier(
      String name, double amount, AttributeModifier.Operation operation) {
    return new AttributeModifier(
        ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", name), amount, operation);
  }

  public static void register(IEventBus modBus) {
    ATTRIBUTES.register(modBus);
    modBus.addListener(ClassTraits::addPlayerAttributes);
    NeoForge.EVENT_BUS.addListener(ClassTraits::tick);
  }

  private static void addPlayerAttributes(EntityAttributeModificationEvent event) {
    event.add(EntityType.PLAYER, SHIELD_RAISE_REDUCTION);
  }

  private static void tick(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player) refresh(player);
  }

  public static void refresh(ServerPlayer player) {
    CharacterClass role = RaceIdentity.characterClass(player);
    boolean light = player.getMainHandItem().is(LIGHT_WEAPONS);
    apply(player, Attributes.ARMOR, ARMOR, role == CharacterClass.WARRIOR);
    if (ModList.get().isLoaded("irons_spellbooks"))
      apply(player, AttributeRegistry.MAX_MANA, MANA, role == CharacterClass.MAGE);
    apply(
        player,
        Attributes.ATTACK_SPEED,
        RECOVERY,
        light && hasCombatTalent(player, "quick_recovery"));
    ItemStack used = player.getUseItem();
    boolean stride =
        used.canPerformAction(ItemAbilities.SHIELD_BLOCK) && hasCombatTalent(player, "shield_march")
            || used.getItem() instanceof BowItem && hasCombatTalent(player, "bow_stride");
    apply(player, Attributes.MOVEMENT_SPEED, STRIDE, stride && !player.isPassenger());
  }

  private static void apply(
      ServerPlayer player,
      Holder<Attribute> attribute,
      AttributeModifier modifier,
      boolean active) {
    var instance = player.getAttribute(attribute);
    if (instance == null || instance.hasModifier(modifier.id()) == active) return;
    if (active) instance.addTransientModifier(modifier);
    else instance.removeModifier(modifier.id());
  }

  public static int shieldDelay(LivingEntity entity, int delay) {
    if (!(entity instanceof Player)
        || !entity.getUseItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)
        || !SHIELD_RAISE_REDUCTION.isBound()) return delay;
    return Math.max(1, delay - (int) entity.getAttributeValue(SHIELD_RAISE_REDUCTION));
  }

  public static float bowVelocity(ServerPlayer player, boolean full, float velocity) {
    return full && RaceIdentity.characterClass(player) == CharacterClass.RANGER
        ? velocity * 1.10F
        : velocity;
  }

  public static float bowSpread(ServerPlayer player, float spread) {
    return player.onGround()
            && !player.isPassenger()
            && player.getKnownMovement().horizontalDistanceSqr() < 1.0E-5
            && hasCombatTalent(player, "steady_aim")
        ? spread * 0.5F
        : spread;
  }

  public static float openingDamage(
      ServerPlayer player, LivingEntity target, ItemStack weapon, float charge, float damage) {
    return charge >= 1.0F
            && target.getHealth() >= target.getMaxHealth()
            && weapon.is(LIGHT_WEAPONS)
            && hasCombatTalent(player, "opening_strike")
        ? damage * 1.15F
        : damage;
  }
}
