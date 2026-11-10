package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "org.violetmoon.quark.content.tools.item.SeedPouchItem", remap = false)
abstract class SeedPouchReforgeMixin {
  @WrapOperation(
      method = "lambda$useOn$4",
      at =
          @At(
              value = "INVOKE",
              ordinal = 0,
              target =
                  "Lorg/violetmoon/quark/content/tools/item/SeedPouchItem;placeSeed(Lorg/violetmoon/quark/content/tools/item/SeedPouchItem$PouchContents;Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/InteractionResult;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private InteractionResult aeroscapes$plantRow(
      @Coerce Object pouch,
      @Coerce Object contents,
      UseOnContext context,
      ItemStack seed,
      BlockPos origin,
      Operation<InteractionResult> original) {
    int available = seed.getCount();
    ItemStack tool = context.getItemInHand();
    InteractionResult result = original.call(pouch, contents, context, seed, origin);
    if (result != InteractionResult.CONSUME
        || !(context.getPlayer() instanceof ServerPlayer player)) return result;
    String profile = ReforgeProfiles.path(tool);
    if ("seed_pouch_poor_stiff".equals(profile)) {
      player.getCooldowns().addCooldown(tool.getItem(), 5);
      return result;
    }
    int length =
        switch (profile) {
          case "seed_pouch_fine_row" -> 2;
          case "seed_pouch_exceptional_row" -> 3;
          case "seed_pouch_epic_row" -> 4;
          case "seed_pouch_legendary_row" -> 5;
          default -> 1;
        };
    if (!(seed.getItem() instanceof ItemNameBlockItem)
        || context.getClickedFace() != Direction.UP
        || !context.getLevel().getBlockState(origin).is(Blocks.FARMLAND)) return result;
    Direction direction = player.getDirection();
    for (int offset = 1; offset < Math.min(length, available) && !seed.isEmpty(); offset++) {
      BlockPos next = origin.relative(direction, offset);
      var level = player.serverLevel();
      if (!level
              .getChunkSource()
              .hasChunk(
                  SectionPos.blockToSectionCoord(next.getX()),
                  SectionPos.blockToSectionCoord(next.getZ()))
          || !player.canInteractWithBlock(next, 0)
          || !level.mayInteract(player, next)
          || !player.mayUseItemAt(next.above(), Direction.UP, seed)
          || !level.getBlockState(next).is(Blocks.FARMLAND)
          || !level.isEmptyBlock(next.above())) break;
      if (original.call(pouch, contents, context, seed, next) != InteractionResult.CONSUME) break;
    }
    return result;
  }
}
