package dev.aeroscapes.mixin.magic.travel;

import io.redspace.ironsspellbooks.spells.ender.PortalSpell;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = PortalSpell.class, remap = false)
public interface PortalSpellAccess {
  @Invoker("getCastDistance")
  float aeroscapes$castDistance(int spellLevel, LivingEntity caster);
}
