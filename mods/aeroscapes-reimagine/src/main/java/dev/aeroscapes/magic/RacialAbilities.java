package dev.aeroscapes.magic;

import dev.aeroscapes.races.RaceIdentity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RacialAbilities {
  public static final int LIGHT_TICKS = 60 * 20;
  private static final String SIGHT = "dwarf_night_vision";
  private static final DeferredRegister<MobEffect> EFFECTS =
      DeferredRegister.create(Registries.MOB_EFFECT, "aeroscapes_reimagine");
  public static final DeferredHolder<MobEffect, MobEffect> ELF_LIGHT =
      EFFECTS.register("elf_light", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xe5db86) {});

  private RacialAbilities() {}

  public static void register(IEventBus modBus) {
    EFFECTS.register(modBus);
    NeoForge.EVENT_BUS.addListener(RacialAbilities::startTracking);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RacialAbilities::removed);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RacialAbilities::expired);
  }

  static void use(ServerPlayer player, InnateAbility ability) {
    if (!player.isAlive() || player.isSpectator()) {
      SpellKnowledge.message(player, "state");
      return;
    }
    if (RaceIdentity.race(player) != ability.race) {
      SpellKnowledge.message(player, "race");
      return;
    }
    String feedback;
    if (ability == InnateAbility.NIGHT_VISION) {
      boolean enabled = !SpellKnowledge.data(player).getBoolean(SIGHT);
      SpellKnowledge.data(player).putBoolean(SIGHT, enabled);
      feedback = enabled ? "sight_on" : "sight_off";
    } else {
      player.addEffect(new MobEffectInstance(ELF_LIGHT, LIGHT_TICKS, 0, false, false, true));
      var effect = player.getEffect(ELF_LIGHT);
      if (effect == null) return;
      player
          .serverLevel()
          .getChunkSource()
          .broadcast(player, new ClientboundUpdateMobEffectPacket(player.getId(), effect, false));
      feedback = "light_lit";
    }
    player.displayClientMessage(
        Component.translatable("aeroscapes_reimagine.abilities." + feedback), true);
  }

  public static boolean nightVision(ServerPlayer player) {
    return SpellKnowledge.data(player).getBoolean(SIGHT);
  }

  static void reconcile(ServerPlayer player, Preparation prepared) {
    if (RaceIdentity.race(player) != InnateAbility.NIGHT_VISION.race
        || !prepared.slots().contains(InnateAbility.NIGHT_VISION.id)) {
      SpellKnowledge.data(player).remove(SIGHT);
    }
    if (player.hasEffect(ELF_LIGHT) && RaceIdentity.race(player) != InnateAbility.LIGHT.race)
      player.removeEffect(ELF_LIGHT);
  }

  // Vanilla only sends player effects to their owner and passengers. Nearby clients also render
  // this particular effect as an entity light, including when an already-lit elf enters range.
  private static void startTracking(PlayerEvent.StartTracking event) {
    if (event.getEntity() instanceof ServerPlayer viewer
        && event.getTarget() instanceof ServerPlayer elf) {
      var effect = elf.getEffect(ELF_LIGHT);
      if (effect != null)
        viewer.connection.send(new ClientboundUpdateMobEffectPacket(elf.getId(), effect, false));
    }
  }

  private static void removed(MobEffectEvent.Remove event) {
    if (event.getEffect().equals(ELF_LIGHT) && event.getEntity() instanceof ServerPlayer player)
      removeTrackedLight(player);
  }

  private static void expired(MobEffectEvent.Expired event) {
    var effect = event.getEffectInstance();
    if (effect != null && effect.is(ELF_LIGHT) && event.getEntity() instanceof ServerPlayer player)
      removeTrackedLight(player);
  }

  private static void removeTrackedLight(ServerPlayer player) {
    player
        .serverLevel()
        .getChunkSource()
        .broadcast(player, new ClientboundRemoveMobEffectPacket(player.getId(), ELF_LIGHT));
  }
}
