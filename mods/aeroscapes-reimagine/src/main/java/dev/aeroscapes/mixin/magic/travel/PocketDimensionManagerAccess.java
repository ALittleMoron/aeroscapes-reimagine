package dev.aeroscapes.mixin.magic.travel;

import io.redspace.ironsspellbooks.capabilities.magic.PocketDimensionManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PocketDimensionManager.class, remap = false)
public interface PocketDimensionManagerAccess {
  @Accessor("ids")
  Object2IntMap<UUID> aeroscapes$pocketIds();
}
