package dev.aeroscapes.equipment.workshop;

import com.herobrot.tieredneo.api.ModifierUtils;
import com.herobrot.tieredneo.api.TieredItemTags;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.progression.PracticeActions;
import harmonised.pmmo.api.APIUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WorkshopQuality {
  private static final ResourceLocation NATIVE_QUALITY =
      ResourceLocation.fromNamespaceAndPath("overgeared", "forging_quality");

  private WorkshopQuality() {}

  public static void finish(Player player, ItemStack result) {
    finish(player, result, false);
  }

  public static void finishQuickMove(Player player, ItemStack result) {
    finish(player, result, true);
  }

  private static void finish(Player player, ItemStack result, boolean replaceProvisionalTier) {
    if (!WorkshopManufacture.finishedEquipment(result)) return;
    Object workmanship = clearNativeQuality(result);
    if (!PracticeActions.personal(player) || player.level().isClientSide()) return;
    if (result.is(TieredItemTags.MODIFIER_RESTRICTED)) return;
    // Hero's Tiered assigns a provisional tier before the shift-click transfer.
    if (replaceProvisionalTier) clearProvisionalTier(result);
    if (result.has(TieredNeoAPI.tierComponentType())) return;
    long level = APIUtils.getAllLevels((ServerPlayer) player).getOrDefault("smithing", 0L);
    var id =
        ReforgeProfiles.rollCrafted(
            result.getItem(), Math.clamp(level, 0, 50) / 10 + score(workmanship));
    if (id != null) {
      var attribute = TieredNeoAPI.getTierAttribute(id);
      if (attribute != null) ModifierUtils.applyTierToStack(result, id, attribute);
    }
  }

  public static ItemStack finishAutomatic(ItemStack result) {
    if (WorkshopManufacture.finishedEquipment(result)) clearNativeQuality(result);
    return result;
  }

  private static Object clearNativeQuality(ItemStack result) {
    var component = BuiltInRegistries.DATA_COMPONENT_TYPE.get(NATIVE_QUALITY);
    return component == null ? null : result.remove(component);
  }

  private static void clearProvisionalTier(ItemStack result) {
    var tier = result.get(TieredNeoAPI.tierComponentType());
    if (tier == null) return;
    if (tier.durable() > 0) {
      var baseDamage = result.getItem().getDefaultInstance().get(DataComponents.MAX_DAMAGE);
      if (baseDamage != null) result.set(DataComponents.MAX_DAMAGE, baseDamage);
    }
    TieredNeoAPI.removeTier(result);
  }

  private static int score(Object workmanship) {
    if (!(workmanship instanceof StringRepresentable quality)) return 0;
    return switch (quality.getSerializedName()) {
      case "well" -> 1;
      case "expert" -> 2;
      case "perfect" -> 3;
      case "master" -> 4;
      default -> 0;
    };
  }
}
