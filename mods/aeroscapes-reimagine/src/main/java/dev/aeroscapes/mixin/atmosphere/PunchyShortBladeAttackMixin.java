package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.ShortBladeAttack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import punchy.config.PunchyTuningConfig.CustomAnimationEntry;

@Pseudo
@Mixin(targets = "punchy.client.state.CustomAnimationResolver", remap = false)
abstract class PunchyShortBladeAttackMixin {
  @Unique private static final CustomAnimationEntry aeroscapes$COMBO = aeroscapes$attackEntry();

  @Unique
  private static CustomAnimationEntry aeroscapes$attackEntry() {
    var entry = new CustomAnimationEntry();
    entry.type = "attack";
    entry.key = "aeroscapes_short_blade_attack";
    entry.items = List.of("#" + ShortBladeAttack.BLADES.location());
    entry.var_1 = "aeroscapes_blade_jab";
    entry.var_2 = "aeroscapes_blade_slash";
    entry.var_3 = "aeroscapes_blade_overhead";
    return entry;
  }

  @WrapOperation(
      method =
          "findEntries(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;Z)Ljava/util/List;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lpunchy/client/render/ToolTuning;getCustomAnimation(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The resource merger groups default attacks by type.
  private static List<CustomAnimationEntry> aeroscapes$attackCandidates(
      ItemStack stack,
      Operation<List<CustomAnimationEntry>> original,
      @Local(argsOnly = true) String type) {
    List<CustomAnimationEntry> entries = original.call(stack);
    if (!"attack".equals(type) || !stack.is(ShortBladeAttack.BLADES)) return entries;
    var candidates =
        entries == null ? new ArrayList<CustomAnimationEntry>() : new ArrayList<>(entries);
    candidates.addFirst(aeroscapes$COMBO);
    return candidates;
  }
}
