package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aeroscapes.sky.WhaleEncounters;
import dev.aeroscapes.sky.WhalePreviewCommands;
import dev.aeroscapes.sky.WhaleVariantAccess;
import dev.aeroscapes.sky.WhaleVariety;
import dev.dacite.skywhales.entity.SkyWhale;
import dev.dacite.skywhales.registry.SWEntities;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/** Runs the owner's preview commands through the real registered command tree. */
public final class WhalePreviewRegression {
  private WhalePreviewRegression() {}

  public static void verify(ServerLevel level) throws CommandSyntaxException {
    BlockPos origin = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 64);
    for (int x = -3; x <= 3; x++)
      for (int z = -3; z <= 3; z++) level.getChunk(origin.offset(x * 16, 0, z * 16));
    var player =
        new FakePlayer(
            level,
            new GameProfile(
                UUID.fromString("0fb507eb-bb07-4fb3-bbc0-19932134527d"), "WhalePreview"));
    player.moveTo(origin.getX(), origin.getY(), origin.getZ(), 0, 0);
    CommandSourceStack source =
        player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
    var dispatcher = level.getServer().getCommands().getDispatcher();
    var command = dispatcher.getRoot().getChild("aeroscapes_whales");
    require(
        command != null && command.canUse(source) && !command.canUse(source.withPermission(0)),
        "Preview commands require administrative permission");
    SkyWhale preview = null;
    try {
      dispatcher.execute("aeroscapes_whales show stone", source);
      UUID id =
          player.getPersistentData().getCompound(WhalePreviewCommands.MARKER).getUUID("entity");
      preview = (SkyWhale) level.getEntity(id);
      require(
          preview != null
              && preview.isNoAi()
              && preview.isNoGravity()
              && preview.isInvulnerable()
              && !preview.isSilent()
              && preview.isPersistenceRequired(),
          "Actual native preview is stationary, protected and persistent");
      require(
          preview.position().distanceTo(player.position().add(0, 8, 32)) < 0.01,
          "Preview is placed horizontally ahead even when the player looks up or down");
      verifySound(level, preview);
      preview.setSilent(true);
      dispatcher.execute("aeroscapes_whales show stone", source);
      require(!preview.isSilent(), "Selecting an existing preview restores its native voice");
      for (WhaleVariety variety : WhaleVariety.values()) {
        SkyWhale previous = preview;
        boolean sameFamily =
            ((WhaleVariantAccess) previous)
                .aeroscapes$variety()
                .animation()
                .equals(variety.animation());
        dispatcher.execute("aeroscapes_whales show " + variety.id(), source);
        id = player.getPersistentData().getCompound(WhalePreviewCommands.MARKER).getUUID("entity");
        preview = (SkyWhale) level.getEntity(id);
        require(
            preview != null
                && ((WhaleVariantAccess) preview).aeroscapes$variety() == variety
                && preview.position().equals(previous.position()),
            "Every synchronized variety appears at the same preview position");
        require(
            sameFamily
                ? preview == previous
                : preview != previous
                    && previous.getRemovalReason() == Entity.RemovalReason.DISCARDED
                    && !previous.isDeadOrDying(),
            "Changing animation family replaces only the preview without triggering death");
        CompoundTag saved = new CompoundTag();
        preview.saveWithoutId(saved);
        require(
            saved.getString("AeroscapesWhaleVariety").equals(variety.id())
                && saved
                    .getCompound("NeoForgeData")
                    .getUUID(WhalePreviewCommands.MARKER)
                    .equals(player.getUUID()),
            "Variant and preview ownership survive native entity serialization");
      }
      dispatcher.execute("aeroscapes_whales next", source);
      preview =
          (SkyWhale)
              level.getEntity(
                  player
                      .getPersistentData()
                      .getCompound(WhalePreviewCommands.MARKER)
                      .getUUID("entity"));
      require(
          ((WhaleVariantAccess) preview).aeroscapes$variety() == WhaleVariety.STONE,
          "Next wraps from the thirteenth variant to the first");
      for (int index = 1; index < WhaleVariety.values().length; index++) {
        dispatcher.execute("aeroscapes_whales next", source);
        preview =
            (SkyWhale)
                level.getEntity(
                    player
                        .getPersistentData()
                        .getCompound(WhalePreviewCommands.MARKER)
                        .getUUID("entity"));
        require(
            ((WhaleVariantAccess) preview).aeroscapes$variety() == WhaleVariety.fromIndex(index),
            "Next traverses every variant in the advertised order");
      }
      var stranger =
          new FakePlayer(
              level,
              new GameProfile(
                  UUID.fromString("0fb507eb-bb07-4fb3-bbc0-19932134527e"), "OtherPreview"));
      stranger
          .getPersistentData()
          .put(
              WhalePreviewCommands.MARKER,
              player.getPersistentData().getCompound(WhalePreviewCommands.MARKER).copy());
      try {
        dispatcher.execute(
            "aeroscapes_whales clear",
            stranger.createCommandSourceStack().withPermission(2).withSuppressedOutput());
        throw new AssertionError(
            "A forged player reference cannot remove another player's preview");
      } catch (CommandSyntaxException expected) {
        require(!preview.isRemoved(), "Ownership rejection leaves the actual preview intact");
      }
      dispatcher.execute("aeroscapes_whales clear", source);
      require(
          preview.getRemovalReason() == Entity.RemovalReason.DISCARDED
              && !preview.isDeadOrDying()
              && level.getEntity(preview.getUUID()) == null
              && !player.getPersistentData().contains(WhalePreviewCommands.MARKER),
          "Clear discards only the preview without death, drops or a carcass");
    } finally {
      if (preview != null && !preview.isRemoved()) preview.discard();
      player.getPersistentData().remove(WhalePreviewCommands.MARKER);
    }
  }

  private static void verifySound(ServerLevel level, SkyWhale preview) {
    var captured = new ArrayList<PlayLevelSoundEvent.AtPosition>();
    Consumer<PlayLevelSoundEvent.AtPosition> listener = captured::add;
    NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, listener);
    var natural = SWEntities.SKY_WHALE.get().create(level);
    require(natural != null, "Native whale is available for the sound control");
    try {
      preview.ambientSoundTime = 1000;
      preview.baseTick();
      require(captured.size() == 1, "NoAI previews still emit their real ambient sound");
      var sound = captured.removeFirst();
      require(
          BuiltInRegistries.SOUND_EVENT
                  .getKey(sound.getSound().value())
                  .toString()
                  .equals("skywhales:whale_ambient")
              && sound.getSource() == SoundSource.NEUTRAL
              && sound.getOriginalVolume() == WhaleEncounters.VOICE_VOLUME
              && sound.getSound().value().getRange(sound.getNewVolume()) >= 96,
          "Preview voice retains its native event/category and natural whale range");
      natural.moveTo(preview.position());
      natural.ambientSoundTime = 1000;
      natural.baseTick();
      require(
          captured.size() == 1
              && captured.getFirst().getOriginalVolume() == WhaleEncounters.VOICE_VOLUME,
          "Natural whales sound exactly like previews and reach their encounter distance");
      CompoundTag saved = new CompoundTag();
      preview.saveWithoutId(saved);
      saved.putBoolean("Silent", true);
      natural.load(saved);
      require(
          !natural.isSilent() && natural.isNoAi(),
          "Loading an older silent preview restores voice while retaining stationary viewing");
    } finally {
      NeoForge.EVENT_BUS.unregister(listener);
      natural.discard();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
