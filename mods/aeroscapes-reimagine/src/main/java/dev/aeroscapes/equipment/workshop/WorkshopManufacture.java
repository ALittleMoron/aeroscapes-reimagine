package dev.aeroscapes.equipment.workshop;

import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.races.ProfessionProgression;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WorkshopManufacture {
  private static final String WORKSHOP = "overgeared";
  private static final String TEMPLATE = "diamond_upgrade_smithing_template";
  private static final Set<String> TOOLS = Set.of("axe", "pickaxe", "shovel", "hoe");
  private static final Set<String> ARMS =
      Set.of("sword", "sword_blade", "helmet", "chestplate", "leggings", "boots");
  private static final Set<String> STARTER_TOOLS = Set.of("stone", "copper");

  private WorkshopManufacture() {}

  public static boolean allowed(Player player, ItemStack result, boolean forge) {
    String talent = requiredTalent(BuiltInRegistries.ITEM.getKey(result.getItem()), forge);
    return talent == null
        || player != null
            && (player.isCreative()
                || ProfessionProgression.hasTalent(player, ProfessionDiscipline.SMITHING, talent));
  }

  public static void explain(Player player) {
    if (player != null)
      player.displayClientMessage(
          Component.translatable("aeroscapes_reimagine.workshop.smithing_talent_required"), true);
  }

  public static boolean finishedEquipment(ItemStack stack) {
    var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
    if (requiredTalent(id, false) != null) return !id.getPath().endsWith("_blade");
    String path = id.getPath();
    int separator = path.indexOf('_');
    if (separator < 0 || !STARTER_TOOLS.contains(path.substring(0, separator))) return false;
    if (!WORKSHOP.equals(id.getNamespace()) && !"minecraft".equals(id.getNamespace())) return false;
    String kind = path.substring(separator + 1);
    return TOOLS.contains(kind) || (ARMS.contains(kind) && !kind.endsWith("_blade"));
  }

  static String requiredTalent(ResourceLocation item, boolean forge) {
    String namespace = item.getNamespace();
    String path = item.getPath();
    if (forge && WORKSHOP.equals(namespace) && TEMPLATE.equals(path))
      return "manufacture_diamond_template";

    int materialEnd = path.indexOf('_');
    if (materialEnd < 0 || (!WORKSHOP.equals(namespace) && !"minecraft".equals(namespace)))
      return null;
    String material = path.substring(0, materialEnd);
    String tier =
        switch (material) {
          case "iron", "golden" -> "iron";
          case "steel", "diamond", "netherite" -> material;
          default -> null;
        };
    if (tier == null) return null;
    String kind = path.substring(materialEnd + 1);
    if (TOOLS.contains(kind)
        || (forge
            && kind.endsWith("_head")
            && TOOLS.contains(kind.substring(0, kind.length() - 5))))
      return "manufacture_" + tier + "_tools";
    if (ARMS.contains(kind)) return "manufacture_" + tier + "_arms";
    return null;
  }
}
