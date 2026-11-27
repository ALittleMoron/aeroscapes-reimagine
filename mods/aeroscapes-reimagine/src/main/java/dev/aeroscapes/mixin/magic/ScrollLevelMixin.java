package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.magic.SpellProgression;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
    return rank;
  }

  @WrapOperation(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/capabilities/magic/SyncedSpellData;isSpellLearned(Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;)Z"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$previewScroll(
      SyncedSpellData data, AbstractSpell spell, Operation<Boolean> original) {
    return true;
  }

  @WrapMethod(method = "removeScrollAfterCast")
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$recordPhysicalScroll(
      ServerPlayer player, ItemStack stack, Operation<Void> original) {
    int before = stack.getCount();
    original.call(player, stack);
    SpellProgression.scrollConsumed(player, stack, before);
  }
}
