package dev.aeroscapes.regression;

import dev.aeroscapes.qualityoflife.CompassSearchNetwork.Request;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;

/** Exercises the actual owned request codec; callable with regression runtime classpath. */
public final class CompassSearchCodecRegression {
  private CompassSearchCodecRegression() {}

  public static void main(String[] arguments) {
    verify(RegistryAccess.EMPTY);
  }

  public static void verify(RegistryAccess registries) {
    for (int flags = 0; flags < 16; flags++) {
      var request =
          new Request(
              (flags & 1) != 0,
              ResourceLocation.parse("minecraft:plains"),
              (flags & 2) != 0,
              (flags & 4) != 0,
              (flags & 8) != 0);
      RegistryFriendlyByteBuf buffer = buffer(registries);
      try {
        Request.CODEC.encode(buffer, request);
        require(request.equals(Request.CODEC.decode(buffer)), "Valid request roundtrip");
      } finally {
        buffer.release();
      }
    }
    reject(registries, 16, "minecraft:plains");
    reject(registries, 128, "minecraft:plains");
    reject(registries, 0, "invalid namespace:bad target");
    reject(registries, 0, "minecraft:" + "a".repeat(257));

    RegistryFriendlyByteBuf boundary = buffer(registries);
    try {
      String maximum = "minecraft:" + "a".repeat(246);
      var request = new Request(true, ResourceLocation.parse(maximum), false, false, true);
      Request.CODEC.encode(boundary, request);
      require(
          Request.CODEC.decode(boundary).equals(request),
          "Exactly256-character identifier accepted");
    } finally {
      boundary.release();
    }

    RegistryFriendlyByteBuf oversized = buffer(registries);
    try {
      var request =
          new Request(
              true, ResourceLocation.parse("minecraft:" + "a".repeat(257)), false, false, true);
      try {
        Request.CODEC.encode(oversized, request);
        throw new IllegalStateException("Overlong encode was accepted");
      } catch (EncoderException expected) {
        // Actual writeUtf length guard.
      }
    } finally {
      oversized.release();
    }
  }

  private static void reject(RegistryAccess registries, int flags, String target) {
    RegistryFriendlyByteBuf buffer = buffer(registries);
    try {
      buffer.writeByte(flags);
      buffer.writeUtf(target);
      try {
        Request.CODEC.decode(buffer);
        throw new IllegalStateException("Malformed request accepted: " + flags);
      } catch (DecoderException expected) {
        // Actual flags, ResourceLocation and readUtf guards.
      }
    } finally {
      buffer.release();
    }
  }

  private static RegistryFriendlyByteBuf buffer(RegistryAccess registries) {
    return new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
