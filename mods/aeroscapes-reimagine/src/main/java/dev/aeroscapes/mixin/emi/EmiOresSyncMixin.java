package dev.aeroscapes.mixin.emi;

import cc.abbie.emi_ores.networking.FeaturesSender;
import cc.abbie.emi_ores.networking.payload.S2CSendBiomeInfoPayload;
import cc.abbie.emi_ores.networking.payload.S2CSendFeaturesPayload;
import dev.aeroscapes.worldgen.OreInformation;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FeaturesSender.class, remap = false)
public abstract class EmiOresSyncMixin {
  @Inject(method = "onSyncDataPackContents", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Preserve the provider's native synchronization seam.
  private static void aeroscapesReimagine$activeFeatures(
      ServerPlayer player,
      BiPredicate<ServerPlayer, CustomPacketPayload.Type<?>> canSend,
      BiConsumer<ServerPlayer, CustomPacketPayload> sender,
      CallbackInfo callback) {
    callback.cancel();
    if (!canSend.test(player, S2CSendBiomeInfoPayload.TYPE)
        || !canSend.test(player, S2CSendFeaturesPayload.TYPE)
        || !NetworkRegistry.hasChannel(player.connection, S2CSendBiomeInfoPayload.TYPE.id())
        || !NetworkRegistry.hasChannel(player.connection, S2CSendFeaturesPayload.TYPE.id())) return;
    var snapshot = OreInformation.snapshot(player.server);
    sender.accept(player, new S2CSendBiomeInfoPayload(snapshot.biomes()));
    sender.accept(player, new S2CSendFeaturesPayload(snapshot.features()));
  }
}
