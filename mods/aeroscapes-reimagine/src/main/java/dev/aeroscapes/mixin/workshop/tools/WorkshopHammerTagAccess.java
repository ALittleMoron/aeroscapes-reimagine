package dev.aeroscapes.mixin.workshop.tools;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.block.custom.AbstractSmithingAnvil", remap = false)
public interface WorkshopHammerTagAccess {
  @Invoker("hammerTag")
  TagKey<Item> aeroscapes$hammerTag();
}
