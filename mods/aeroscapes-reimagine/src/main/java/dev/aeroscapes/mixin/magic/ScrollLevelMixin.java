package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.magic.SpellKnowledge;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Scroll.class, remap = false)
abstract class ScrollLevelMixin {
  @WrapOperation(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getLevelFor(ILnet/minecraft/world/entity/LivingEntity;)I"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$scrollRank(
      AbstractSpell spell, int rank, LivingEntity caster, Operation<Integer> original) {
    return SpellKnowledge.scrollLevel(() -> original.call(spell, rank, caster));
  }
}
