package dev.aeroscapes.mixin.reforge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.ToolReforges;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

@Mixin(value = CuttingBoardBlockEntity.class, remap = false)
abstract class CuttingBoardReforgeMixin {
  @Unique
  private static final Set<ResourceLocation> aeroscapes$FILLET_RECIPES =
      Set.of(
          ResourceLocation.fromNamespaceAndPath("farmersdelight", "cutting/cod"),
          ResourceLocation.fromNamespaceAndPath("farmersdelight", "cutting/salmon"));

  @WrapOperation(
      method = "lambda$processStoredItemUsingTool$2",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lvectorwing/farmersdelight/common/crafting/CuttingBoardRecipe;rollResults(Lnet/minecraft/util/RandomSource;I)Ljava/util/List;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private List<ItemStack> aeroscapes$cutTrimmings(
      CuttingBoardRecipe recipe,
      RandomSource random,
      int fortune,
      Operation<List<ItemStack>> original,
      @Local(argsOnly = true) ItemStack knife,
      @Local(argsOnly = true) Player user,
      @Local(argsOnly = true) RecipeHolder<CuttingBoardRecipe> match) {
    List<ItemStack> outputs = original.call(recipe, random, fortune);
    var board = (CuttingBoardBlockEntity) (Object) this;
    if (!(user instanceof ServerPlayer player)
        || !ToolReforges.isSurvivalPlayer(player)
        || !ReforgeProfiles.has(knife, "knife_legendary_trimmings")
        || !aeroscapes$FILLET_RECIPES.contains(match.id())
        || !aeroscapes$isFilleting(board.getStoredItem())
        || outputs.stream().noneMatch(stack -> stack.is(Items.BONE_MEAL))
        || random.nextFloat() >= 0.15F) return outputs;
    List<ItemStack> result = new ArrayList<>(outputs);
    result.add(new ItemStack(Items.BONE_MEAL));
    return result;
  }

  @WrapOperation(
      method = "lambda$processStoredItemUsingTool$2",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$saveKnifeWear(
      ItemStack knife,
      int wear,
      ServerLevel level,
      LivingEntity user,
      Consumer<Item> onBreak,
      Operation<Void> original) {
    String profile = ReforgeProfiles.path(knife);
    if (profile.contains("_with_") || profile.equals("knife_legendary_trimmings")) {
      var board = (CuttingBoardBlockEntity) (Object) this;
      boolean fish = aeroscapes$isFilleting(board.getStoredItem());
      int chance =
          switch (profile) {
            case "knife_poor_with_dragging_edge" -> -20;
            case "knife_ordinary_with_board_edge" -> 5;
            case "knife_fine_with_board_edge" -> 10;
            case "knife_exceptional_speed_with_filleting" -> fish ? 18 : 0;
            case "knife_epic_with_filleting" -> fish ? 25 : 10;
            case "knife_legendary_trimmings" -> 10;
            default -> 0;
          };
      int changed = ToolReforges.modifyWear(user, wear, chance);
      if (changed > 0) original.call(knife, changed, level, user, onBreak);
      return;
    }
    float chance =
        switch (profile) {
          case "knife_exceptional_saving" -> 0.12F;
          case "knife_epic", "knife_legendary_batch" -> 0.10F;
          default -> 0;
        };
    if (user == null || chance == 0 || user.getRandom().nextFloat() >= chance)
      original.call(knife, wear, level, user, onBreak);
  }

  @Unique
  private static boolean aeroscapes$isFilleting(ItemStack input) {
    return input.is(Items.COD) || input.is(Items.SALMON);
  }
}
