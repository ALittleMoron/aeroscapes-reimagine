package dev.aeroscapes.mixin.workshop;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TagLoader.class)
abstract class WorkshopBlockTagMixin {
  @ModifyExpressionValue(
      method = "load",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/TagFile;entries()Ljava/util/List;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private List<TagEntry> aeroscapes$omitAbsentAnvil(
      List<TagEntry> entries, @Local Resource source) {
    if (!"mod/overgeared".equals(source.sourcePackId())) return entries;
    // The shipped tags still name the removed anvil. Keep other packs' mining entries.
    return entries.stream()
        .filter(entry -> !"overgeared:copper_smithing_anvil".equals(entry.toString()))
        .toList();
  }
}
