package dev.aeroscapes.mixin.weathering;

import com.ordana.immersive_weathering.items.FlowerCrownItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FlowerCrownItem.class, remap = false)
public abstract class WeatheringCrownMixin implements IItemExtension {
  @Override
  public ResourceLocation getArmorTexture(
      ItemStack stack,
      Entity entity,
      EquipmentSlot slot,
      ArmorMaterial.Layer layer,
      boolean innerModel) {
    String texture = FlowerCrownItem.getModelTexture(stack);
    return texture == null
        ? ResourceLocation.withDefaultNamespace("textures/models/armor/flower_layer_1.png")
        : ResourceLocation.parse(texture);
  }
}
