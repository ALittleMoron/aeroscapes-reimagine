package dev.aeroscapes.equipment;

import com.herobrot.tieredneo.api.TieredItemTags;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class ReforgeProfiles {
  private static final String NAMESPACE = "aeroscapes_reimagine";

  private ReforgeProfiles() {}

  public static boolean has(ItemStack stack, String profile) {
    return profile.equals(path(stack));
  }

  public static String path(ItemStack stack) {
    if (stack.isEmpty() || !ModList.get().isLoaded("tieredneo")) return "";
    var id = TieredNeoAPI.getTierId(stack);
    return id != null && NAMESPACE.equals(id.getNamespace()) ? id.getPath() : "";
  }

  public static boolean isEligible(Item item) {
    return ModList.get().isLoaded("tieredneo")
        && !item.getDefaultInstance().is(TieredItemTags.MODIFIER_RESTRICTED)
        && TieredNeoAPI.getTierAttributes().values().stream()
            .anyMatch(profile -> profile.getWeight() >= 0 && profile.isValid(item));
  }

  public static ResourceLocation roll(Item item, boolean paid) {
    var choices = new EnumMap<Quality, List<ResourceLocation>>(Quality.class);
    for (var entry : TieredNeoAPI.getTierAttributes().entrySet()) {
      var id = entry.getKey();
      var profile = entry.getValue();
      if (!NAMESPACE.equals(id.getNamespace()) || !profile.isValid(item)) continue;
      var quality = Quality.fromPath(id.getPath());
      if (quality == null || profile.getWeight() < (paid ? 0 : 1) || quality.weight(paid) == 0)
        continue;
      choices.computeIfAbsent(quality, key -> new ArrayList<>()).add(id);
    }
    if (choices.isEmpty()) return null;

    int total = choices.keySet().stream().mapToInt(quality -> quality.weight(paid)).sum();
    var random = ThreadLocalRandom.current();
    int ticket = random.nextInt(total);
    for (var entry : choices.entrySet()) {
      ticket -= entry.getKey().weight(paid);
      if (ticket < 0) {
        var profiles = entry.getValue();
        return profiles.get(random.nextInt(profiles.size()));
      }
    }
    throw new IllegalStateException("Invalid equipment quality weights");
  }

  private enum Quality {
    POOR(12, 15),
    ORDINARY(47, 52),
    FINE(22, 21),
    EXCEPTIONAL(11, 10),
    EPIC(5, 2),
    LEGENDARY(3, 0);

    private final int paidWeight;
    private final int naturalWeight;

    Quality(int paidWeight, int naturalWeight) {
      this.paidWeight = paidWeight;
      this.naturalWeight = naturalWeight;
    }

    int weight(boolean paid) {
      return paid ? paidWeight : naturalWeight;
    }

    static Quality fromPath(String path) {
      String parts = "_" + path + "_";
      if (parts.contains("_poor_")) return POOR;
      if (parts.contains("_ordinary_")) return ORDINARY;
      if (parts.contains("_fine_")) return FINE;
      if (parts.contains("_exceptional_")) return EXCEPTIONAL;
      if (parts.contains("_epic_")) return EPIC;
      if (parts.contains("_legendary_")) return LEGENDARY;
      return null;
    }
  }
}
