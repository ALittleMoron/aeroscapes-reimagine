package dev.aeroscapes.mixin.workshop.tools;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.event.AnvilMinigameEvents", remap = false)
abstract class WorkshopHammerReforgeMixin {
  @Shadow public static int perfectZoneStart;
  @Shadow public static int perfectZoneEnd;
  @Shadow public static int goodZoneStart;
  @Shadow public static int goodZoneEnd;

  @Shadow
  public static BlockPos getAnvilPos(UUID player) {
    throw new AssertionError();
  }

  // Adjust both rendering and scoring without feeding the expanded window back into native
  // shrinkage.
  @ModifyExpressionValue(
      method = {"getPerfectZoneStart", "handleHit"},
      at =
          @At(
              value = "FIELD",
              opcode = Opcodes.GETSTATIC,
              target = "Lnet/stirdrem/overgeared/event/AnvilMinigameEvents;perfectZoneStart:I"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static int aeroscapes$exactZoneStart(int start) {
    int adjustment = aeroscapes$hammerAdjustment();
    if (adjustment > 0) return Math.max(goodZoneStart, start - adjustment);
    return adjustment < 0 && perfectZoneEnd - start > 2 ? start + 1 : start;
  }

  @ModifyExpressionValue(
      method = {"getPerfectZoneEnd", "handleHit"},
      at =
          @At(
              value = "FIELD",
              opcode = Opcodes.GETSTATIC,
              target = "Lnet/stirdrem/overgeared/event/AnvilMinigameEvents;perfectZoneEnd:I"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static int aeroscapes$exactZoneEnd(int end) {
    int adjustment = aeroscapes$hammerAdjustment();
    if (adjustment > 0) return Math.min(goodZoneEnd, end + adjustment);
    return adjustment < 0 && end - perfectZoneStart > 2 ? end - 1 : end;
  }

  @Unique
  private static int aeroscapes$hammerAdjustment() {
    var player = Minecraft.getInstance().player;
    if (player == null) return 0;
    var position = getAnvilPos(player.getUUID());
    var level = player.level();
    if (position == null
        || !level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)
        || !(level.getBlockState(position).getBlock() instanceof WorkshopHammerTagAccess anvil))
      return 0;
    var tag = anvil.aeroscapes$hammerTag();
    var hammer = player.getMainHandItem();
    if (!hammer.is(tag)) hammer = player.getOffhandItem();
    if (!hammer.is(tag)) return 0;
    int adjustment =
        switch (ReforgeProfiles.path(hammer)) {
          case "smithing_hammer_poor" -> -1;
          case "smithing_hammer_fine" -> 1;
          case "smithing_hammer_exceptional" -> 2;
          case "smithing_hammer_epic" -> 3;
          case "smithing_hammer_legendary" -> 4;
          default -> 0;
        };
    return Math.min(adjustment, (perfectZoneEnd - perfectZoneStart) / 2);
  }
}
