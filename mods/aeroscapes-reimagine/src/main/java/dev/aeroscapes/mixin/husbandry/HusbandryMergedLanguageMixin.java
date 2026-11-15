package dev.aeroscapes.mixin.husbandry;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(
    value = {LivingEntity.class, Cow.class},
    priority = 900)
abstract class HusbandryMergedLanguageMixin {}
