package dev.aeroscapes.engineering;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class LadleAssemblyOutputs {
  private static final ResourceLocation RECIPE =
      ResourceLocation.fromNamespaceAndPath("createmetallurgy", "sequenced_assembly/ladle");

  private LadleAssemblyOutputs() {}

  public static boolean addAlternates(Object recipe, List<EmiStack> outputs) {
    if (!(recipe instanceof RecipeHolder<?> holder)
        || !RECIPE.equals(holder.id())
        || !(holder.value() instanceof SequencedAssemblyRecipe assembly)) return false;
    for (var result : assembly.resultPool) {
      var stack = EmiStack.of(result.getStack().copy());
      if (outputs.stream().noneMatch(stack::isEqual)) outputs.add(stack);
    }
    return true;
  }
}
