package dev.aeroscapes.mixin.hex;

import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapDisallowedSpell;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.hex.HexSpellLimits;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets = "at.petrak.hexcasting.api.casting.castables.SpellAction$DefaultImpls",
    remap = false)
abstract class BoundedSpellMixin {
  @WrapOperation(
      method = "operate",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lat/petrak/hexcasting/api/casting/castables/SpellAction;executeWithUserdata(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;Lnet/minecraft/nbt/CompoundTag;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras invokes the wrapped call site.
  private static SpellAction.Result aeroscapes$boundSpell(
      SpellAction action,
      List<Iota> arguments,
      CastingEnvironment environment,
      CompoundTag data,
      Operation<SpellAction.Result> original)
      throws MishapDisallowedSpell {
    var result = original.call(action, arguments, environment, data);
    return HexSpellLimits.check(action, arguments, environment, result);
  }
}
