package dev.aeroscapes.equipment.field;

import dev.aeroscapes.equipment.ReforgeProfiles;
import io.github.bonsaistudi0s.crittersandcompanions.common.item.PearlNecklaceItem;
import io.github.bonsaistudi0s.crittersandcompanions.common.platform.PlatformHooks;
import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class CompanionReforges {
  private static final String NECKLACE_READY = "aeroscapes_reimagine:necklace_ready";
  private static final ResourceLocation SWIM =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "necklace_swimming");
  private static final Map<Mob, PetGuard> PET_GUARDS = new WeakHashMap<>();

  private CompanionReforges() {}

  @SubscribeEvent
  public static void steadySwimmer(LivingKnockBackEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || !player.isInWater()) return;
    String profile = ReforgeProfiles.path(necklace(player));
    float scale =
        switch (profile) {
          case "swim_necklace_poor_drag" -> 1.15F;
          case "swim_necklace_fine_stable" -> 0.94F;
          case "swim_necklace_exceptional_stable", "swim_necklace_epic_current" -> 0.85F;
          case "swim_necklace_legendary_anchor" -> 0.9F;
          default -> 1.0F;
        };
    if ("swim_necklace_legendary_anchor".equals(profile)
        && event.getStrength() > 0
        && player.getPersistentData().getLong(NECKLACE_READY) <= player.level().getGameTime()) {
      player.getPersistentData().putLong(NECKLACE_READY, player.level().getGameTime() + 120);
      scale = 0.25F;
    }
    if (scale != 1.0F) event.setStrength(event.getStrength() * scale);
  }

  @SubscribeEvent
  public static void swimControl(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    var attribute = player.getAttribute(NeoForgeMod.SWIM_SPEED);
    if (attribute == null) return;
    double amount = 0.0;
    if (player.isInWater()) {
      amount =
          switch (ReforgeProfiles.path(necklace(player))) {
            case "swim_necklace_poor_drag" -> -0.06;
            case "swim_necklace_epic_current", "swim_necklace_legendary_anchor" -> 0.06;
            default -> 0.0;
          };
    }
    var installed = attribute.getModifier(SWIM);
    if (installed != null && installed.amount() == amount) return;
    if (installed != null) attribute.removeModifier(SWIM);
    if (amount != 0.0) {
      attribute.addTransientModifier(
          new AttributeModifier(SWIM, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
  }

  private static ItemStack necklace(ServerPlayer player) {
    if (!ModList.get().isLoaded("crittersandcompanions")) return ItemStack.EMPTY;
    return Stream.concat(
            player.getInventory().items.stream(), PlatformHooks.getAdditionalEquipment(player))
        .filter(stack -> stack.getItem() instanceof PearlNecklaceItem)
        .max(Comparator.comparingInt(stack -> ((PearlNecklaceItem) stack.getItem()).getLevel()))
        .orElse(ItemStack.EMPTY);
  }

  @SubscribeEvent
  public static void petProtection(LivingDamageEvent.Pre event) {
    if (!(event.getEntity() instanceof Mob mob)
        || mob.level().isClientSide()
        || event.getNewDamage() <= 0
        || event.getSource().getEntity() == null
        || mob instanceof Wolf && event.getSource().is(DamageTypeTags.BYPASSES_WOLF_ARMOR)) return;
    float result = adjustProtection(mob, mob.getBodyArmorItem(), event.getNewDamage());
    if (result != event.getNewDamage()) event.setNewDamage(result);
  }

  private static float adjustProtection(Mob mob, ItemStack armor, float damage) {
    float scale =
        switch (ReforgeProfiles.path(armor)) {
          case "pet_armor_poor_loose" -> 1.08F;
          case "pet_armor_fine_stable" -> 0.96F;
          case "pet_armor_exceptional_guard" -> 0.92F;
          case "pet_armor_epic_guard", "pet_armor_legendary_guard" -> 0.90F;
          default -> 1.0F;
        };
    if (scale == 1.0F) return damage;
    float result = damage * scale;
    var guard = PET_GUARDS.get(mob);
    if (guard != null
        && ReforgeProfiles.has(armor, "pet_armor_legendary_guard")
        && guard.armor().get() == armor
        && guard.expires() > mob.level().getGameTime()) {
      result -= Math.min(1.5F, result * 0.2F);
      PET_GUARDS.put(mob, new PetGuard(guard.armor(), 0, guard.readyAgain()));
    }
    return result;
  }

  @SubscribeEvent
  public static void readyPetGuard(LivingDamageEvent.Post event) {
    if (!(event.getEntity() instanceof Mob mob)
        || mob.level().isClientSide()
        || !mob.isAlive()
        || event.getNewDamage() <= 0
        || event.getSource().getEntity() == null
        || mob instanceof Wolf && event.getSource().is(DamageTypeTags.BYPASSES_WOLF_ARMOR)) return;
    readyGuard(mob, mob.getBodyArmorItem());
  }

  public static int wolfArmorWear(Wolf wolf, ItemStack armor, int wear) {
    float adjusted = adjustProtection(wolf, armor, wear);
    int whole = Mth.floor(adjusted);
    return whole == adjusted || wolf.getRandom().nextFloat() >= adjusted - whole
        ? whole
        : whole + 1;
  }

  public static void readyGuard(Mob mob, ItemStack armor) {
    if (mob.getBodyArmorItem() != armor || armor.isEmpty()) return;
    if (!ReforgeProfiles.has(armor, "pet_armor_legendary_guard")) return;
    long now = mob.level().getGameTime();
    var guard = PET_GUARDS.get(mob);
    if (guard == null || guard.readyAgain() <= now) {
      PET_GUARDS.put(mob, new PetGuard(new WeakReference<>(armor), now + 60, now + 120));
    }
  }

  private record PetGuard(WeakReference<ItemStack> armor, long expires, long readyAgain) {}
}
