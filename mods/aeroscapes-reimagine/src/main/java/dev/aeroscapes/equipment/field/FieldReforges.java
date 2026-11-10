package dev.aeroscapes.equipment.field;

import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.ToolReforges;
import java.util.Comparator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import sfiomn.legendarysurvivaloverhaul.api.thirst.HydrationEnum;
import sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class FieldReforges {
  private static final String FOOTING = "aeroscapes_reimagine:canteen_footing";

  private FieldReforges() {}

  public static int brushWear(ItemStack brush, LivingEntity user, int amount) {
    int chance =
        switch (ReforgeProfiles.path(brush)) {
          case "brush_poor_rough" -> -25;
          case "brush_fine_edge" -> 20;
          case "brush_epic_sand", "brush_epic_silt", "brush_legendary_memory" -> 30;
          default -> 0;
        };
    return ToolReforges.modifyWear(user, amount, chance);
  }

  public static int brushProgress(ItemStack brush, BlockState medium, int nativeStep) {
    String profile = ReforgeProfiles.path(brush);
    if (nativeStep < 3 || nativeStep >= 9 || nativeStep % 3 != 0) return nativeStep;
    var id = BuiltInRegistries.BLOCK.getKey(medium.getBlock());
    boolean sand =
        medium.is(Blocks.SUSPICIOUS_SAND)
            || id.equals(
                ResourceLocation.fromNamespaceAndPath("betterarcheology", "suspicious_red_sand"));
    boolean silt =
        medium.is(Blocks.SUSPICIOUS_GRAVEL)
            || id.equals(ResourceLocation.fromNamespaceAndPath("wetland_whimsy", "suspicious_mud"))
            || id.equals(
                ResourceLocation.fromNamespaceAndPath("betterarcheology", "suspicious_dirt"));
    boolean quick =
        switch (profile) {
          case "brush_exceptional_sand", "brush_epic_sand" -> sand;
          case "brush_exceptional_silt", "brush_epic_silt" -> silt;
          case "brush_legendary_memory" -> true;
          default -> false;
        };
    return quick ? nativeStep + 1 : nativeStep;
  }

  @SubscribeEvent
  public static void fieldUseDuration(LivingEntityUseItemEvent.Start event) {
    int duration = event.getDuration();
    if (duration <= 0) return;
    float factor =
        switch (ReforgeProfiles.path(event.getItem())) {
          case "canteen_poor_stiff", "medkit_poor_stiff" -> 1.2F;
          case "canteen_fine_ready", "medkit_fine_ready" -> 0.9F;
          case "canteen_exceptional_mobile" -> event.getEntity().isSprinting() ? 0.72F : 0.85F;
          case "medkit_exceptional_quick" -> 0.8F;
          case "canteen_epic_ready", "medkit_epic_ready" -> 0.72F;
          case "canteen_legendary_footing", "medkit_legendary_retreat" -> 0.75F;
          default -> 1.0F;
        };
    if (factor != 1.0F) event.setDuration(Math.max(1, Math.round(duration * factor)));
  }

  @SubscribeEvent
  public static void completedFieldUse(LivingEntityUseItemEvent.Finish event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    ItemStack before = event.getItem();
    ItemStack after = event.getResultStack();
    if (ReforgeProfiles.has(before, "canteen_legendary_footing")
        && ModList.get().isLoaded("legendarysurvivaloverhaul")
        && ThirstUtil.getHydrationEnumTag(before) == HydrationEnum.PURIFIED
        && ThirstUtil.getCapacityTag(before) - ThirstUtil.getCapacityTag(after) == 1) {
      player.getPersistentData().putLong(FOOTING, player.level().getGameTime() + 100);
    }
    if (ReforgeProfiles.has(before, "medkit_legendary_retreat")
        && before.getCount() - after.getCount() == 1
        && player.getTeam() != null) {
      player.serverLevel().players().stream()
          .filter(ally -> ally != player && ally.isAlive() && !ally.isSpectator())
          .filter(ally -> player.isAlliedTo(ally) && ally.getHealth() < ally.getMaxHealth())
          .filter(ally -> player.distanceToSqr(ally) <= 16 && player.hasLineOfSight(ally))
          .min(Comparator.comparingDouble(player::distanceToSqr))
          .ifPresent(ally -> ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60)));
    }
  }

  @SubscribeEvent
  public static void canteenFooting(LivingKnockBackEvent event) {
    if (event.getEntity() instanceof ServerPlayer player
        && player.getPersistentData().getLong(FOOTING) > player.level().getGameTime()) {
      event.setStrength(event.getStrength() * 0.75F);
    }
  }

  public static Object component(ItemStack stack, ResourceLocation id) {
    var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
    return type == null ? null : stack.get(type);
  }
}
