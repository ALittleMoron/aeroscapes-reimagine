package dev.aeroscapes.equipment.engineering;

import com.herobrot.tieredneo.api.ModifierUtils;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class SwitchLinkPearlReforge {
  public static final String GRADE_TAG = "aeroscapes_reimagine:pearl_grade";
  private static final ResourceLocation PEARL =
      ResourceLocation.fromNamespaceAndPath("rsgauges", "switchlink_pearl");
  private static final int MAX_LINK_DISTANCE = 2048;
  private static final int FACTOR_DENOMINATOR = 24;
  private static final Map<String, Integer> RANGE_FACTORS =
      Map.of(
          "switchlink_pearl_poor", 20,
          "switchlink_pearl_ordinary", 24,
          "switchlink_pearl_fine", 27,
          "switchlink_pearl_exceptional", 30,
          "switchlink_pearl_epic", 33,
          "switchlink_pearl_legendary", 36);

  private SwitchLinkPearlReforge() {}

  public static String knownGrade(String grade) {
    return RANGE_FACTORS.containsKey(grade) ? grade : "";
  }

  public static String grade(ItemStack pearl) {
    return PEARL.equals(BuiltInRegistries.ITEM.getKey(pearl.getItem()))
        ? knownGrade(ReforgeProfiles.path(pearl))
        : "";
  }

  public static int manualDistanceLimit(ItemStack pearl, int nativeLimit) {
    if (nativeLimit <= 0) return nativeLimit;
    int factor = RANGE_FACTORS.getOrDefault(grade(pearl), FACTOR_DENOMINATOR);
    return Mth.clamp(nativeLimit * factor / FACTOR_DENOMINATOR, 1, MAX_LINK_DISTANCE);
  }

  public static void restoreGrade(ItemStack pearl, String grade) {
    if (grade.isEmpty()
        || !PEARL.equals(BuiltInRegistries.ITEM.getKey(pearl.getItem()))
        || !ModList.get().isLoaded("tieredneo")) return;
    var id = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", grade);
    var profile = TieredNeoAPI.getTierAttribute(id);
    if (profile != null && profile.isValid(pearl.getItem())) {
      ModifierUtils.applyTierToStack(pearl, id, profile);
    }
  }

  public interface StoredGrade {
    String aeroscapes$getGrade();

    void aeroscapes$setGrade(String grade);
  }
}
