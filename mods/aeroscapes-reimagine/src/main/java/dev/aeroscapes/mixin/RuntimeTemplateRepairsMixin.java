package dev.aeroscapes.mixin;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.RuntimeTemplateRepairs;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Repair only resource templates; generated/saved templates retain the native loading path. */
@Mixin(StructureTemplateManager.class)
public abstract class RuntimeTemplateRepairsMixin {
  @Shadow private ResourceManager resourceManager;

  @Shadow
  public abstract StructureTemplate readStructure(CompoundTag data);

  @Inject(method = "loadFromResource", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Called by Mixin before the native resource load.
  private void aeroscapesReimagine$repairResource(
      ResourceLocation id, CallbackInfoReturnable<Optional<StructureTemplate>> callback) {
    if (!RuntimeTemplateRepairs.applies(id)) return;
    ResourceLocation resource =
        ResourceLocation.fromNamespaceAndPath(
            id.getNamespace(), "structure/" + id.getPath() + ".nbt");
    try (var stream = resourceManager.open(resource)) {
      CompoundTag data = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
      RuntimeTemplateRepairs.apply(id, data);
      // Native readStructure performs the version-appropriate STRUCTURE DFU and block lookup.
      callback.setReturnValue(Optional.of(readStructure(data)));
    } catch (IOException | RuntimeException exception) {
      LogUtils.getLogger().error("Couldn't load repaired structure {}", id, exception);
      callback.setReturnValue(Optional.empty());
    }
  }
}
