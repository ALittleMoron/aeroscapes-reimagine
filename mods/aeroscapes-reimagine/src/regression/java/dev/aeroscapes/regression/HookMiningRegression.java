package dev.aeroscapes.regression;

import com.yori3o.yo_hooks.common.entity.HookEntity;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForgeMod;

/** The native mining calculation exempts only the player's valid, attached rope. */
public final class HookMiningRegression {
  private HookMiningRegression() {}

  public static void verify(ServerLevel level, ServerPlayer player) {
    ItemStack mainHand = player.getMainHandItem().copy();
    ItemStack offHand = player.getOffhandItem().copy();
    boolean grounded = player.onGround();
    BlockPos eye = BlockPos.containing(player.getEyePosition());
    var originalEyeBlock = level.getBlockState(eye);
    var data = (PlayerWithHookData) player;
    require(data.yo_hooks$getHook() == null, "Mining fixture requires no existing hook");
    try {
      level.setBlock(eye, Blocks.AIR.defaultBlockState(), 3);
      player.baseTick();
      verifyOffhand(level, player);
      verifyMainHand(level, player);
    } finally {
      HookEntity hook = data.yo_hooks$getHook();
      if (hook != null) hook.discard();
      data.yo_hooks$setHook(null);
      player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
      player.setItemInHand(InteractionHand.OFF_HAND, offHand);
      level.setBlock(eye, originalEyeBlock, 3);
      player.baseTick();
      player.setOnGround(grounded);
    }
  }

  private static void verifyOffhand(ServerLevel level, ServerPlayer player) {
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    float grounded = speed(player, true);
    require(grounded > 0, "Grounded mining control must have positive speed");
    close(speed(player, false), grounded / 5, "Free airborne mining lost its native penalty");
    HookEntity hook = cast(level, player, InteractionHand.OFF_HAND);
    var data = (PlayerWithHookData) player;
    try {
      require(!hook.isInBlock(), "Freshly cast hook must be unembedded");
      close(speed(player, false), grounded / 5, "Unembedded hook removed the airborne penalty");
      embed(hook, player);
      close(speed(player, true), grounded, "Attached hook increased grounded mining speed");
      close(speed(player, false), grounded, "Attached offhand rope retained the airborne penalty");
      require(!player.onGround(), "Mining exemption changed the player's real ground state");
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
      grounded = speed(player, true);
      close(speed(player, false), grounded, "Changing mining tool lost the offhand exemption");
      ItemStack activeStack = player.getOffhandItem();
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      close(speed(player, false), grounded / 5, "Stowing the active hook retained its exemption");
      player.setItemInHand(InteractionHand.OFF_HAND, activeStack);
      data.yo_hooks$setHook(null);
      close(speed(player, false), grounded / 5, "An unowned anchored hook exempted the player");
      data.yo_hooks$setHook(hook);
      verifyWater(level, player, grounded);
      activeStack.getItem().use(level, player, InteractionHand.OFF_HAND);
      require(hook.isRemoved() && data.yo_hooks$getHook() == null, "Native hook recall failed");
      close(speed(player, false), grounded / 5, "Recalling the hook retained its exemption");
      data.yo_hooks$setHook(hook);
      close(speed(player, false), grounded / 5, "A stale removed hook exempted the player");
    } finally {
      hook.discard();
      data.yo_hooks$setHook(null);
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }
  }

  private static void verifyMainHand(ServerLevel level, ServerPlayer player) {
    HookEntity hook = cast(level, player, InteractionHand.MAIN_HAND);
    try {
      float grounded = speed(player, true);
      close(speed(player, false), grounded / 5, "Unembedded main-hand hook exempted the player");
      embed(hook, player);
      close(
          speed(player, false), grounded, "Attached main-hand rope retained the airborne penalty");
    } finally {
      hook.discard();
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
  }

  private static void verifyWater(ServerLevel level, ServerPlayer player, float drySpeed) {
    BlockPos eye = BlockPos.containing(player.getEyePosition());
    var original = level.getBlockState(eye);
    try {
      level.setBlock(eye, Blocks.WATER.defaultBlockState(), 3);
      player.baseTick();
      require(
          player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value()),
          "Water control did not submerge the player's eyes");
      float submerged = speed(player, true);
      require(submerged > 0 && submerged < drySpeed, "Water control must retain a mining penalty");
      close(speed(player, false), submerged, "Attached rope changed the separate water penalty");
    } finally {
      level.setBlock(eye, original, 3);
      player.baseTick();
    }
  }

  private static HookEntity cast(ServerLevel level, ServerPlayer player, InteractionHand hand) {
    var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("yo_hooks:iron_grappling_hook"));
    player.setItemInHand(hand, new ItemStack(item));
    item.use(level, player, hand);
    HookEntity hook = ((PlayerWithHookData) player).yo_hooks$getHook();
    require(
        hook != null && hook.getHoldingHand(player) == hand && hook.isControlledBy(player),
        "Native hook failed to bind its active held stack");
    return hook;
  }

  private static void embed(HookEntity hook, ServerPlayer player) {
    hook.setPos(player.getEyePosition().add(0, 3, 0));
    CompoundTag saved = hook.saveWithoutId(new CompoundTag());
    // Exercise the provider's native saved attachment state without client aiming or physics ticks.
    saved.putBoolean("in_block", true);
    saved.putFloat("length", 3);
    hook.load(saved);
    require(hook.isInBlock() && hook.isControlledBy(player), "Native anchored hook fixture failed");
  }

  private static float speed(ServerPlayer player, boolean grounded) {
    player.setOnGround(grounded);
    return player.getDigSpeed(Blocks.STONE.defaultBlockState(), player.blockPosition());
  }

  private static void close(float actual, float expected, String message) {
    require(Math.abs(actual - expected) < 0.0001F, message + ": " + actual + " != " + expected);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
