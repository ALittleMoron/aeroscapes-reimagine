package dev.aeroscapes.sky;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.dacite.skywhales.entity.SkyWhale;
import dev.dacite.skywhales.registry.SWEntities;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Explicit stationary previews, kept separate from natural encounters and physical carcasses. */
@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class WhalePreviewCommands {
  public static final String MARKER = "aeroscapes_reimagine:whale_preview";

  /** Excludes previews from the sky photo journal, whose predicates read entity tags. */
  public static final String PREVIEW_TAG = "aeroscapes_reimagine.whale_preview";

  private static final DynamicCommandExceptionType ERROR =
      new DynamicCommandExceptionType(value -> (Component) value);

  private WhalePreviewCommands() {}

  @SubscribeEvent
  public static void register(RegisterCommandsEvent event) {
    if (!ModList.get()
        .getModContainerById("skywhales")
        .map(container -> "0.1.9".equals(container.getModInfo().getVersion().toString()))
        .orElse(false)) return;
    event
        .getDispatcher()
        .register(
            Commands.literal("aeroscapes_whales")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("show")
                        .then(
                            Commands.argument("variety", StringArgumentType.word())
                                .suggests(
                                    (context, builder) ->
                                        SharedSuggestionProvider.suggest(
                                            Arrays.stream(WhaleVariety.values())
                                                .map(WhaleVariety::id),
                                            builder))
                                .executes(context -> Operations.show(context, false))))
                .then(Commands.literal("next").executes(context -> Operations.show(context, true)))
                .then(Commands.literal("clear").executes(Operations::clear)));
  }

  @SubscribeEvent
  public static void clonePlayer(PlayerEvent.Clone event) {
    CompoundTag reference = event.getOriginal().getPersistentData().getCompound(MARKER);
    if (reference.hasUUID("entity"))
      event.getEntity().getPersistentData().put(MARKER, reference.copy());
  }

  private static void require(boolean condition, String key, Object... arguments)
      throws CommandSyntaxException {
    if (!condition)
      throw ERROR.create(
          Component.translatable("message.aeroscapes_reimagine.whale_preview." + key, arguments));
  }

  private static final class Operations {
    private Operations() {}

    private static SkyWhale find(ServerPlayer player) throws CommandSyntaxException {
      CompoundTag reference = player.getPersistentData().getCompound(MARKER);
      if (!reference.hasUUID("entity")) return null;
      ServerLevel level = player.server.getLevel(Level.OVERWORLD);
      if (level == null) throw new IllegalStateException("Missing Overworld");
      var entity = level.getEntity(reference.getUUID("entity"));
      if (entity == null) {
        BlockPos position =
            new BlockPos(reference.getInt("x"), reference.getInt("y"), reference.getInt("z"));
        require(
            level.isLoaded(position),
            "unloaded",
            position.getX(),
            position.getY(),
            position.getZ());
        player.getPersistentData().remove(MARKER);
        return null;
      }
      require(
          entity instanceof SkyWhale
              && entity.getPersistentData().hasUUID(MARKER)
              && entity.getPersistentData().getUUID(MARKER).equals(player.getUUID()),
          "owner");
      return (SkyWhale) entity;
    }

    private static int show(CommandContext<CommandSourceStack> context, boolean next)
        throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      require(player.level().dimension() == Level.OVERWORLD, "overworld");
      SkyWhale existing = find(player);
      WhaleVariety variety;
      if (next) {
        variety =
            existing == null
                ? WhaleVariety.STONE
                : WhaleVariety.fromIndex(
                    (((WhaleVariantAccess) existing).aeroscapes$variety().ordinal() + 1)
                        % WhaleVariety.values().length);
      } else {
        String id = StringArgumentType.getString(context, "variety");
        variety = WhaleVariety.fromId(id);
        require(variety.id().equals(id), "variety");
      }
      // GeckoLib caches an unchanged RawAnimation even after the model resource changes.
      boolean replace =
          existing != null
              && !((WhaleVariantAccess) existing)
                  .aeroscapes$variety()
                  .animation()
                  .equals(variety.animation());
      SkyWhale whale = replace ? null : existing;
      if (whale == null) {
        whale = SWEntities.SKY_WHALE.get().create(player.serverLevel());
        require(whale != null, "failed");
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 position =
            replace ? existing.position() : player.position().add(forward.scale(32)).add(0, 8, 0);
        float yaw = replace ? existing.yBodyRot : player.getYRot() + 180;
        whale.moveTo(position.x, position.y, position.z, yaw, 0);
        whale.yBodyRot = yaw;
        whale.yBodyRotO = yaw;
        whale.yHeadRot = yaw;
        whale.yHeadRotO = yaw;
        whale.setNoAi(true);
        whale.setNoGravity(true);
        whale.setInvulnerable(true);
        whale.setPersistenceRequired();
        whale.getPersistentData().putUUID(MARKER, player.getUUID());
        whale.addTag(PREVIEW_TAG);
      }
      require(WhaleSpawnClearance.clear(player.serverLevel(), whale, variety), "blocked");
      whale.setSilent(false);
      ((WhaleVariantAccess) whale).aeroscapes$variety(variety);
      if (existing == null || replace) {
        require(player.serverLevel().addFreshEntity(whale), "failed");
        if (replace) existing.discard();
        CompoundTag reference = new CompoundTag();
        reference.putUUID("entity", whale.getUUID());
        reference.putInt("x", whale.blockPosition().getX());
        reference.putInt("y", whale.blockPosition().getY());
        reference.putInt("z", whale.blockPosition().getZ());
        player.getPersistentData().put(MARKER, reference);
      }
      WhaleVariety selected = variety;
      context
          .getSource()
          .sendSuccess(
              () ->
                  Component.translatable(
                      "message.aeroscapes_reimagine.whale_preview.shown",
                      Component.translatable(
                          "entity.aeroscapes_reimagine.sky_whale." + selected.id()),
                      selected.ordinal() + 1,
                      WhaleVariety.values().length),
              false);
      return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context)
        throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      SkyWhale whale = find(player);
      if (whale != null) whale.discard();
      player.getPersistentData().remove(MARKER);
      context
          .getSource()
          .sendSuccess(
              () -> Component.translatable("message.aeroscapes_reimagine.whale_preview.cleared"),
              false);
      return 1;
    }
  }
}
