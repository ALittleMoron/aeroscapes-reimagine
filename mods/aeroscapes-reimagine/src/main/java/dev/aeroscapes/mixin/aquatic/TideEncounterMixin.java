package dev.aeroscapes.mixin.aquatic;

import com.li64.tide.data.rods.CustomRodManager;
import com.li64.tide.registries.entities.misc.fishing.HookAccessor;
import com.li64.tide.registries.entities.misc.fishing.TideFishingHook;
import dev.hybridlabs.aquatic.entity.HAEntityTypes;
import dev.hybridlabs.aquatic.item.HAItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = TideFishingHook.class, remap = false)
public abstract class TideEncounterMixin {
  @Shadow private int nibble;

  @Shadow
  protected abstract void startRetrieving();

  @Invoker("shouldKeepFishing")
  public abstract boolean aeroscapes$shouldKeepFishing(Player player);

  @Inject(
      method =
          "retrieve(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;)I",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Runs once before Tide's per-item reward loop.
  private void aeroscapes$summonOnce(
      ItemStack rod, ServerLevel level, Player player, CallbackInfoReturnable<Integer> callback) {
    var hook = (TideFishingHook) (Object) this;
    if (!(player instanceof ServerPlayer serverPlayer)
        || HookAccessor.getHook(player) != hook
        || !aeroscapes$shouldKeepFishing(player)
        || hook.getHookedIn() != null
        || nibble <= 0) return;
    ItemStack attachment = hook.getHook();
    EntityType<?> type;
    if (attachment.is(HAItems.INSTANCE.getOMINOUS_HOOK().get())) {
      type = HAEntityTypes.INSTANCE.getKARKINOS().get();
    } else if (attachment.is(HAItems.INSTANCE.getCREEPERMAGNET_HOOK().get())) {
      type = EntityType.CREEPER;
    } else return;
    var entity = type.spawn(level, hook.blockPosition(), MobSpawnType.MOB_SUMMONED);
    if (entity == null) return;
    var distance = player.position().subtract(hook.position());
    double lift = Math.sqrt(Math.sqrt(distance.lengthSqr())) * 0.08;
    entity.setDeltaMovement(distance.x * 0.15, distance.y * 0.15 + lift, distance.z * 0.15);
    attachment.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    CustomRodManager.setHook(rod, attachment.isEmpty() ? null : attachment);
    serverPlayer.inventoryMenu.broadcastChanges();
    int wear = hook.onGround() ? 2 : 1;
    startRetrieving();
    callback.setReturnValue(wear);
  }
}
