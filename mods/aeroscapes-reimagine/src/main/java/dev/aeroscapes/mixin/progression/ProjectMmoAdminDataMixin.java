package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.progression.ProgressionBridge;
import harmonised.pmmo.commands.CmdNodeAdmin;
import harmonised.pmmo.core.IDataStorage;
import harmonised.pmmo.storage.Experience;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes native admin edits persistent and visible before an integrated server pauses. */
@Mixin(value = CmdNodeAdmin.class, remap = false)
abstract class ProjectMmoAdminDataMixin {
  @WrapOperation(
      method = {"adminSetOrAdd", "adminClearSkill"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lharmonised/pmmo/core/IDataStorage;getXpMap(Ljava/util/UUID;)Ljava/util/Map;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked at the pinned native map lookup.
  private static Map<String, Experience> aeroscapes$managedExperience(
      IDataStorage storage, UUID player, Operation<Map<String, Experience>> original) {
    Map<String, Experience> experience = original.call(storage, player);
    // getXpMap returns a detached empty map for new players; setXpMap also marks SavedData dirty.
    storage.setXpMap(player, experience);
    return experience;
  }

  @Inject(method = "adminSetOrAdd", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Runs after the native mutation and client XP packet.
  private static void aeroscapes$updateTalents(
      CommandContext<CommandSourceStack> context,
      boolean set,
      CallbackInfoReturnable<Integer> result)
      throws CommandSyntaxException {
    aeroscapes$syncTargets(
        context, "magic".equals(context.getArgument("Skill Name", String.class)));
  }

  @Inject(method = "adminClear", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native clear commands have a different signature.
  private static void aeroscapes$clearTalents(
      CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> result)
      throws CommandSyntaxException {
    aeroscapes$syncTargets(context, true);
  }

  @Inject(method = "adminClearSkill", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$clearSkillTalents(
      CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> result)
      throws CommandSyntaxException {
    aeroscapes$syncTargets(
        context, "magic".equals(context.getArgument("Skill Name", String.class)));
  }

  private static void aeroscapes$syncTargets(
      CommandContext<CommandSourceStack> context, boolean magicChanged)
      throws CommandSyntaxException {
    for (var player : EntityArgument.getPlayers(context, "Target")) {
      if (magicChanged) MagicPractice.adminEdited(player);
      ProgressionBridge.sync(player);
    }
  }
}
