package dev.aeroscapes.mixin.reforge.tools;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface MiningDropPickupDelayAccess {
  @Accessor("pickupDelay")
  int aeroscapes$pickupDelay();
}
