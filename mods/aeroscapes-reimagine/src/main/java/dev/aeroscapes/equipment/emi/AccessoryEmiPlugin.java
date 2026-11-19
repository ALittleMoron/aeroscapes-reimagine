package dev.aeroscapes.equipment.emi;

import dev.aeroscapes.equipment.AccessoryGuides;
import dev.aeroscapes.equipment.AccessoryTooltips;
import dev.aeroscapes.equipment.magic.SpellAccessoryAttributes;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

@EmiEntrypoint
public final class AccessoryEmiPlugin implements EmiPlugin {
  @Override
  public void register(EmiRegistry registry) {
    if (!ModList.get().isLoaded("curios")) return;
    for (var entry : AccessoryGuides.ITEMS.entrySet()) {
      var id = entry.getKey();
      var item = BuiltInRegistries.ITEM.getOptional(id);
      if (item.isEmpty()) continue;
      var stack = item.get().getDefaultInstance();
      var guide = entry.getValue();
      var lines = new ArrayList<Component>();
      lines.add(AccessoryTooltips.slot(guide.slot()));
      guide.hints().forEach(key -> lines.add(AccessoryTooltips.line(key)));
      guide.details().forEach(key -> lines.add(AccessoryTooltips.line(key)));
      if (id.getNamespace().equals("irons_spellbooks"))
        lines.addAll(SpellAccessoryAttributes.baseLines(guide.slot(), id, stack));
      registry.addRecipe(
          new EmiInfoRecipe(
              List.of(EmiStack.of(stack)),
              lines,
              ResourceLocation.fromNamespaceAndPath(
                  "aeroscapes_reimagine",
                  "/info/accessory/" + id.getNamespace() + "/" + id.getPath())));
    }
  }
}
