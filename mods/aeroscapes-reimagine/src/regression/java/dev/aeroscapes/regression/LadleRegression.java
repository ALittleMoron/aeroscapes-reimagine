package dev.aeroscapes.regression;

import com.herobrot.tieredneo.api.ModifierUtils;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * Native opening, item/entity capabilities and full-stack serialization of graded fluid carriers.
 */
public final class LadleRegression {
  private static final List<String> STYLES =
      List.of("ghast", "cute", "old", "strider", "restingphantom", "the_cooler");

  private LadleRegression() {}

  public static void verify(ServerLevel level) {
    var player = FakePlayerFactory.getMinecraft(level);
    var previous = player.getMainHandItem();
    int previousFire = player.getRemainingFireTicks();
    var steel =
        BuiltInRegistries.FLUID.get(ResourceLocation.parse("createmetallurgy:molten_steel"));
    try {
      for (String style : STYLES) {
        var item =
            BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("createmetallurgy:" + style + "_transfer_ladle"));
        require(
            ReforgeProfiles.paidGrades(item).size() == 6, "Every ladle has all six paid grades");
        require(
            TieredNeoAPI.getReforgeBaseItems(item).size() == 2,
            "Either provider's steel ingot can pay for ladle reforging");
        var ladle = ranked(new ItemStack(item), "legendary");
        var fluids = fluids(ladle);
        require(fluids.getTankCapacity(0) == 4000, "Graded ladle capacity remains 4000mB");
        require(
            fluids.fill(new FluidStack(steel, 4000), IFluidHandler.FluidAction.EXECUTE) == 4000,
            "Fill native ladle exactly once");
        var dropped = new ItemEntity(level, 0, 300, 0, ladle.copy());
        var entity = item.createEntity(level, dropped, ladle.copy());
        if (!(entity instanceof PackageEntity placed))
          throw new IllegalStateException("Native ladle entity must retain PackageEntity contract");
        require(
            ItemStack.isSameItemSameComponents(ladle, placed.getBox()),
            "Dropped ladle keeps quality and fluid");
        var restored = placed.getType().create(level);
        if (!(restored instanceof PackageEntity loaded))
          throw new IllegalStateException("Saved ladle entity type is registered");
        loaded.load(placed.saveWithoutId(new CompoundTag()));
        require(
            ItemStack.isSameItemSameComponents(ladle, loaded.getBox()),
            "Entity save/load keeps quality and fluid");
        require(
            fluids(loaded.getBox()).getFluidInTank(0).getAmount() == 4000,
            "Saved fluid is conserved");
        placed.discard();
        loaded.discard();
        dropped.discard();
        player.setItemInHand(InteractionHand.MAIN_HAND, ladle);
        player.setRemainingFireTicks(200);
        ((PackageItem) item).open(level, player, InteractionHand.MAIN_HAND);
        require(
            player.getRemainingFireTicks() == 200,
            "Safe handling must not extinguish unrelated fire");
        require(
            fluids(player.getMainHandItem()).getFluidInTank(0).getAmount() == 3000,
            "Native opening still drains 1000mB");
        require(
            ReforgeProfiles.has(player.getMainHandItem(), "ladle_legendary"),
            "Opening keeps quality");
      }
      var item =
          BuiltInRegistries.ITEM.get(ResourceLocation.parse("createmetallurgy:old_transfer_ladle"));
      long burnSeed = 0;
      while (true) {
        level.getRandom().setSeed(burnSeed);
        if (level.getRandom().nextInt(10) == 0) break;
        burnSeed++;
      }
      int previousBurns = 201;
      for (String grade : List.of("poor", "ordinary", "fine", "exceptional", "epic", "legendary")) {
        int burns = 0;
        for (int trial = 0; trial < 200; trial++) {
          var ladle = ranked(new ItemStack(item), grade);
          player.setItemInHand(InteractionHand.MAIN_HAND, ladle);
          player.setRemainingFireTicks(0);
          level.getRandom().setSeed(burnSeed);
          player.getRandom().setSeed(trial);
          ((PackageItem) item).open(level, player, InteractionHand.MAIN_HAND);
          if (player.getRemainingFireTicks() > 0) burns++;
          require(
              fluids(ladle).getTankCapacity(0) == 4000, "Every grade retains the same capacity");
        }
        if (grade.equals("poor") || grade.equals("ordinary"))
          require(burns == 200, "Unmodified native burn branch: " + grade);
        else require(burns < previousBurns, "Quality reduces the native burn branch: " + grade);
        if (grade.equals("legendary"))
          require(burns == 0, "Legendary manual handling blocks native burn");
        previousBurns = burns;
      }
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, previous);
      player.setRemainingFireTicks(previousFire);
    }
  }

  private static ItemStack ranked(ItemStack stack, String grade) {
    var id = ResourceLocation.parse("aeroscapes_reimagine:ladle_" + grade);
    var tier = TieredNeoAPI.getTierAttribute(id);
    require(tier != null && tier.isValid(stack.getItem()), "Ladle profile " + grade);
    ModifierUtils.applyTierToStack(stack, id, tier);
    return stack;
  }

  private static IFluidHandlerItem fluids(ItemStack stack) {
    var fluids = stack.getCapability(Capabilities.FluidHandler.ITEM);
    if (fluids == null) throw new IllegalStateException("Native ladle item fluid capability");
    return fluids;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
