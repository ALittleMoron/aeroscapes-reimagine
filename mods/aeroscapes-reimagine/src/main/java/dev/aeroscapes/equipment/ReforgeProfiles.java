package dev.aeroscapes.equipment;

import com.herobrot.tieredneo.api.PotentialAttribute;
import com.herobrot.tieredneo.api.TieredItemTags;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
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

  public static String basePath(ItemStack stack) {
    return basePath(path(stack));
  }

  public static String basePath(String path) {
    int separator = path.indexOf("_with_");
    return separator < 0 ? path : path.substring(0, separator);
  }

  public static List<PaidGrade> paidGrades(Item item) {
    if (item.getDefaultInstance().is(TieredItemTags.MODIFIER_RESTRICTED)) return List.of();
    return choices(item, true).entrySet().stream()
        .map(
            entry ->
                new PaidGrade(
                    entry.getKey().name().toLowerCase(Locale.ROOT),
                    entry.getKey().weight(true),
                    entry.getValue().stream().sorted(Comparator.comparing(Outcome::id)).toList()))
        .toList();
  }

  private static EnumMap<Quality, List<Outcome>> choices(Item item, boolean paid) {
    var choices = new EnumMap<Quality, List<Outcome>>(Quality.class);
    for (var entry : TieredNeoAPI.getTierAttributes().entrySet()) {
      var id = entry.getKey();
      var profile = entry.getValue();
      if (!NAMESPACE.equals(id.getNamespace()) || !profile.isValid(item)) continue;
      var quality = Quality.fromPath(basePath(id.getPath()));
      if (quality == null || profile.getWeight() < (paid ? 0 : 1) || quality.weight(paid) == 0)
        continue;
      choices.computeIfAbsent(quality, key -> new ArrayList<>()).add(new Outcome(id, profile));
    }
    return choices;
  }

  public static ResourceLocation roll(Item item, boolean paid) {
    var choices = choices(item, paid);
    if (choices.isEmpty()) return null;

    int total = choices.keySet().stream().mapToInt(quality -> quality.weight(paid)).sum();
    var random = ThreadLocalRandom.current();
    int ticket = random.nextInt(total);
    for (var entry : choices.entrySet()) {
      ticket -= entry.getKey().weight(paid);
      if (ticket < 0) {
        var profiles = entry.getValue();
        return profiles.get(random.nextInt(profiles.size())).id();
      }
    }
    throw new IllegalStateException("Invalid equipment quality weights");
  }

  public record PaidGrade(String key, int weight, List<Outcome> outcomes) {}

  public record Outcome(ResourceLocation id, PotentialAttribute profile) {}

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
