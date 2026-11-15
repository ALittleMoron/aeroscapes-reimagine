package dev.aeroscapes.husbandry;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Chicken;
import org.athisworkshop.animalhusbandry.config.ModConfig;
import org.athisworkshop.animalhusbandry.registry.ModAttachments;

public final class ChickenGeneticAppearance {
  private static final Set<String> COLORS = Set.of("white", "brown", "black", "golden");
  private static final Set<String> PATTERNS = Set.of("solid", "speckled");

  private ChickenGeneticAppearance() {}

  public static ResourceLocation texture(Chicken chicken) {
    if (!ModConfig.INSTANCE.enableCustomTextures) return null;
    var genetics = ModAttachments.getGenetics(chicken);
    if (!genetics.isInitialized
        || genetics.primaryColor == null
        || genetics.pattern == null
        || !COLORS.contains(genetics.primaryColor)
        || !PATTERNS.contains(genetics.pattern)) return null;
    return ResourceLocation.fromNamespaceAndPath(
        "aeroscapes_husbandry_translations",
        "textures/entity/chicken/"
            + (chicken.isBaby() ? "baby_" : "")
            + genetics.primaryColor
            + "_"
            + genetics.pattern
            + ".png");
  }
}
