package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.tools.ToolProficiency;
import dev.aeroscapes.progression.PracticeActions;
import dev.aeroscapes.progression.ProfessionDiscipline;
import harmonised.pmmo.api.enums.EventType;
import harmonised.pmmo.storage.DataAttachmentTypes;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "ht.treechop.compat.ProjectMMO", remap = false)
abstract class TreeChopPracticeMixin {
  // Individual chops bypass PMMO's break handler and its placed-block checks.
  @WrapOperation(
      method = "awardXPOnChop",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lharmonised/pmmo/api/APIUtils;getXpAwardMap(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lharmonised/pmmo/api/enums/EventType;Lnet/minecraft/world/entity/player/Player;)Ljava/util/Map;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static Map<String, Long> aeroscapes$treePractice(
      Level level,
      BlockPos pos,
      EventType event,
      Player player,
      Operation<Map<String, Long>> original) {
    if (!(level instanceof ServerLevel server)
        || !PracticeActions.personal(player)
        || server.getChunkAt(pos).getData(DataAttachmentTypes.PLACED_MAP).containsKey(pos))
      return Map.of();
    if (ModList.get().isLoaded("puffish_skills") && player.getMainHandItem().is(ItemTags.AXES))
      return Map.of(
          "mining", 1L, ProfessionDiscipline.AXE_HANDLING.skill(), ToolProficiency.PRACTICE_XP);
    return Map.of("mining", 1L);
  }
}
