package dev.aeroscapes.testship;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class TestShipCommands {
  private TestShipCommands() {}

  @SubscribeEvent
  public static void register(RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("aeroscapes_test_ship")
                .requires(source -> source.hasPermission(2))
                .executes(TestShipCommands::spawn)
                .then(Commands.literal("spawn").executes(TestShipCommands::spawn))
                .then(Commands.literal("board").executes(context -> run(context, "board")))
                .then(Commands.literal("leave").executes(context -> run(context, "leave")))
                .then(Commands.literal("remove").executes(context -> run(context, "remove"))));
  }

  @SubscribeEvent
  public static void clonePlayer(PlayerEvent.Clone event) {
    TestShips.copyReference(event.getOriginal(), event.getEntity());
  }

  private static int spawn(CommandContext<CommandSourceStack> context)
      throws CommandSyntaxException {
    TestShips.spawn(context.getSource().getPlayerOrException());
    feedback(context, "spawned");
    return 1;
  }

  private static int run(CommandContext<CommandSourceStack> context, String operation)
      throws CommandSyntaxException {
    ServerPlayer player = context.getSource().getPlayerOrException();
    switch (operation) {
      case "board" -> TestShips.board(player);
      case "leave" -> TestShips.leave(player);
      case "remove" -> TestShips.remove(player);
      default -> throw new IllegalArgumentException(operation);
    }
    feedback(context, operation.equals("board") ? "waiting" : operation + "_done");
    return 1;
  }

  private static void feedback(CommandContext<CommandSourceStack> context, String key) {
    context
        .getSource()
        .sendSuccess(
            () -> Component.translatable("message.aeroscapes_reimagine.test_ship." + key), false);
  }
}
