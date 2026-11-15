package dev.aeroscapes.atmosphere;

import com.crispytwig.naturalist.world.item.CaughtEntityItem;
import com.ninni.teallib.api.common.item.CatchableMobBucketItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.component.CustomData;

/** Physical vessels, including providers which do not inherit Minecraft's bucket class. */
public final class BucketPresentation {
  private BucketPresentation() {}

  public static boolean physical(Item item) {
    if (item instanceof CaughtEntityItem) return false;
    if (item instanceof BucketItem
        || item instanceof MilkBucketItem
        || item instanceof SolidBucketItem) return true;
    if (item instanceof CatchableMobBucketItem
        && new ItemStack(item).getCraftingRemainingItem().is(Items.BUCKET)) return true;
    String id = BuiltInRegistries.ITEM.getKey(item).toString();
    return id.equals("quark:slime_in_a_bucket") || id.equals("fluidlogistics:copper_bucket");
  }

  public static boolean physical(ItemStack stack, RegistryAccess registries) {
    if (physical(stack.getItem())) return true;
    if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("spawn:captured_octopus"))
      return false;
    var original = captureContainer(stack, registries);
    return original.is(Items.BUCKET) || original.is(Items.WATER_BUCKET);
  }

  public static ItemStack captureContainer(ItemStack stack, RegistryAccess registries) {
    var data = stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).copyTag();
    return ItemStack.parseOptional(registries, data.getCompound("Item"));
  }
}
