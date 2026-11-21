package dev.aeroscapes.regression;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.PatternShapeMatch;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.env.CircleCastEnv;
import at.petrak.hexcasting.api.casting.eval.env.PackagedItemCastEnv;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.casting.mishaps.MishapDisallowedSpell;
import at.petrak.hexcasting.common.lib.HexBlocks;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class HexLimitsRegression {
  private HexLimitsRegression() {}

  static void verify(ServerLevel level) {
    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos().above(26));
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "HexLimits", position, null)) {
      var player = session.player;
      AbilitiesRegression.initialize(player);
      player.setGameMode(GameType.SURVIVAL);
      HexLensRegression.verify(player);
      var staff = new StaffCastEnv(player, InteractionHand.MAIN_HAND);
      var dust = new ItemStack(HexItems.AMETHYST_DUST, 32);
      player.setItemInHand(
          InteractionHand.MAIN_HAND,
          BuiltInRegistries.ITEM.get(id("staff/oak")).getDefaultInstance());
      player.getInventory().setItem(5, dust);
      var spot = player.blockPosition();
      level.setBlockAndUpdate(spot, Blocks.AIR.defaultBlockState());
      List<Iota> light =
          List.of(pattern("get_caster"), pattern("entity_pos/foot"), pattern("conjure_light"));
      var outcome = CastingVM.empty(staff).queueExecuteAndWrapIotas(light, level);
      require(
          outcome.getResolutionType().getSuccess()
              && level.getBlockState(spot).is(HexBlocks.CONJURED_LIGHT)
              && dust.getCount() == 31,
          "Draw three patterns to light the player's position for one dust");

      for (var context :
          List.of(
              staff,
              new PackagedItemCastEnv(player, InteractionHand.MAIN_HAND),
              new CircleCastEnv(level, null))) {
        for (var forbidden : List.of("flight/range", "flight/time", "interop/pehkui/set"))
          denied(context, forbidden);
        enabled(context, "interop/pehkui/get");
      }
      var target = new EntityIota(player);
      rejected(staff, "blink", List.of(target, new DoubleIota(17)), "aeroscapes_blink");
      rejected(
          staff,
          "teleport/great",
          List.of(target, new Vec3Iota(new Vec3(65, 0, 0))),
          "aeroscapes_teleport");
      rejected(
          staff,
          "add_motion",
          List.of(target, new Vec3Iota(new Vec3(0, 2, 0))),
          "aeroscapes_impulse");
      rejected(staff, "potion/levitation", List.of(target, new DoubleIota(5)), "aeroscapes_lift");
      rejected(
          staff, "explode", List.of(new Vec3Iota(position), new DoubleIota(5)), "aeroscapes_blast");
      rejected(
          staff,
          "potion/strength",
          List.of(target, new DoubleIota(1), new DoubleIota(3)),
          "aeroscapes_effect");
      var vm = new CastingVM(stack(List.of(target, new DoubleIota(4))), staff);
      var blink = vm.queueExecuteAndWrapIota(pattern("blink"), level);
      require(
          blink.getResolutionType().getSuccess() && player.position().distanceTo(position) > 3.9,
          "Native short blink moves its target");
      rejected(staff, "flight", List.of(target), "aeroscapes_travel_interval");
      level.setBlockAndUpdate(spot, Blocks.AIR.defaultBlockState());
      LogUtils.getLogger().info("Hex casting limits regression passed");
    }
  }

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("hexcasting", path);
  }

  private static ActionRegistryEntry entry(String path) {
    return IXplatAbstractions.INSTANCE.getActionRegistry().getOptional(id(path)).orElseThrow();
  }

  private static PatternIota pattern(String path) {
    return new PatternIota(entry(path).prototype());
  }

  private static CastingImage stack(List<Iota> args) {
    return new CastingImage().copy(args, 0, List.of(), false, 0, new CompoundTag());
  }

  private static void checkAction(CastingEnvironment env, String path) throws Mishap {
    var registry = IXplatAbstractions.INSTANCE.getActionRegistry();
    var key = registry.getResourceKey(entry(path)).orElseThrow();
    env.precheckAction(new PatternShapeMatch.Normal(key));
  }

  private static void denied(CastingEnvironment env, String path) {
    try {
      checkAction(env, path);
    } catch (MishapDisallowedSpell expected) {
      return;
    } catch (Mishap unexpected) {
      throw new IllegalStateException(path, unexpected);
    }
    throw new IllegalStateException("Allowed forbidden pattern " + path);
  }

  private static void enabled(CastingEnvironment env, String path) {
    try {
      checkAction(env, path);
    } catch (Mishap unexpected) {
      throw new IllegalStateException(path, unexpected);
    }
  }

  private static void operate(CastingEnvironment env, String path, List<Iota> args) throws Mishap {
    entry(path).action().operate(env, stack(args), SpellContinuation.Done.INSTANCE);
  }

  private static void rejected(
      CastingEnvironment env, String path, List<Iota> args, String reason) {
    try {
      operate(env, path, args);
    } catch (MishapDisallowedSpell expected) {
      require(expected.getType().equals(reason), path + " produced " + expected.getType());
      return;
    } catch (Mishap unexpected) {
      throw new IllegalStateException(path, unexpected);
    }
    throw new IllegalStateException("Accepted out-of-bounds action " + path);
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new IllegalStateException(message);
  }
}
