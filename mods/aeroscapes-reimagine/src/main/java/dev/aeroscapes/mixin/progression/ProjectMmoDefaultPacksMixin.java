package dev.aeroscapes.mixin.progression;

import harmonised.pmmo.ProjectMMO;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ProjectMMO.class, remap = false)
abstract class ProjectMmoDefaultPacksMixin {
  // The pack owns its practice rules. PMMO's default presets add independent object/NBT awards.
  @Inject(method = "onPackFind", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$usePackPractice(AddPackFindersEvent event, CallbackInfo callback) {
    if (event.getPackType() == PackType.SERVER_DATA) callback.cancel();
  }
}
