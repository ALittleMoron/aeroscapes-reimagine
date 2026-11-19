package dev.aeroscapes.mixin.inventory;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.inventory.AccessoryUse;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class AccessoryItemUseMixin {
  @WrapOperation(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/Item;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by MixinExtras.
  private InteractionResultHolder<ItemStack> aeroscapes$equip(
      Item item,
      Level level,
      Player player,
      InteractionHand hand,
      Operation<InteractionResultHolder<ItemStack>> original) {
    var held = (ItemStack) (Object) this;
    boolean deliberate = player.isSecondaryUseActive();
    if (deliberate) {
      var equipped = AccessoryUse.equip(player, hand, held);
      if (equipped != null) return equipped;
    }
    var used = original.call(item, level, player, hand);
    if (!deliberate && used.getResult() == InteractionResult.PASS && used.getObject() == held) {
      var equipped = AccessoryUse.equip(player, hand, held);
      if (equipped != null) return equipped;
    }
    return used;
  }
}
