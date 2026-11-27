package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.races.ProfessionTools;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
abstract class BuildingDurabilityMixin {
  @Unique private static final ThreadLocal<ItemStack> aeroscapes$dismantling = new ThreadLocal<>();

  @WrapMethod(method = "mineBlock")
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$trackDismantling(
      Level level, BlockState state, BlockPos pos, Player actor, Operation<Void> original) {
    var tool = (ItemStack) (Object) this;
    var previous = aeroscapes$dismantling.get();
    if (actor instanceof ServerPlayer player
        && ProfessionTools.carefulDismantling(player, tool, state))
      aeroscapes$dismantling.set(tool);
    else aeroscapes$dismantling.remove();
    try {
      original.call(level, state, pos, actor);
    } finally {
      if (previous == null) aeroscapes$dismantling.remove();
      else aeroscapes$dismantling.set(previous);
    }
  }

  @WrapOperation(
      method =
          "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processDurabilityChange(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$saveWear(
      ServerLevel level, ItemStack tool, int damage, Operation<Integer> original) {
    int wear = original.call(level, tool, damage);
    if (wear <= 0 || aeroscapes$dismantling.get() != tool) return wear;
    return wear / 2 + (wear % 2 != 0 && level.getRandom().nextBoolean() ? 1 : 0);
  }
}
