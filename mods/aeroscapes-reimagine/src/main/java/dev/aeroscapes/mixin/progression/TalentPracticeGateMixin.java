package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.CombatTalentRequirements;
import dev.aeroscapes.progression.ProgressionBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkillsMod.class, remap = false)
abstract class TalentPracticeGateMixin {
  @Inject(method = "tryUnlockSkill", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$readiness(
      ServerPlayer player,
      ResourceLocation category,
      String skill,
      boolean force,
      CallbackInfo callback) {
    if (force) return;
    if (category.toString().equals(ProgressionBridge.COMBAT_CATEGORY)) {
      var required = CombatTalentRequirements.missing(player, skill);
      if (required != null) {
        player.displayClientMessage(required, true);
        callback.cancel();
      }
      return;
    }
    if (!category.toString().equals(ProgressionBridge.MAGIC_CATEGORY)) return;
    int requiredLevel = MagicTalents.requiredLevel(skill);
    if (requiredLevel > 0 && requiredLevel > MagicPractice.level(player)) {
      player.displayClientMessage(
          net.minecraft.network.chat.Component.translatable(
              "aeroscapes_reimagine.magic_tree.practice_required", requiredLevel),
          true);
      callback.cancel();
      return;
    }
    String missing =
        MagicTalents.missingPriorMastery(
            skill,
            id ->
                SkillsMod.getInstance()
                        .getSkillState(player, category, id)
                        .orElse(Skill.State.LOCKED)
                    == Skill.State.UNLOCKED);
    if (missing != null) {
      player.displayClientMessage(SpellKnowledge.requiredMasteryMessage(missing), true);
      callback.cancel();
    }
  }
}
