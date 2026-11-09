package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import traben.entity_model_features.EMFAnimationApi;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class SleeveGesture {
  private static KeyMapping binding;
  private static boolean keyWasDown;

  private SleeveGesture() {}

  @SubscribeEvent
  public static void registerKeys(RegisterKeyMappingsEvent event) {
    if (!ModList.get().isLoaded("playeranimator")) return;
    binding =
        new KeyMapping(
            "key.aeroscapes_reimagine.brush_sleeve",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.aeroscapes_reimagine");
    event.register(binding);
  }

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    if (ModList.get().isLoaded("playeranimator")) event.enqueueWork(GestureLayer::register);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (binding == null) return;
    boolean requested = false;
    while (binding.consumeClick()) requested = !keyWasDown;
    keyWasDown = binding.isDown();
    GestureLayer.update(Minecraft.getInstance(), requested);
  }

  private static final class GestureLayer extends ModifierLayer<IAnimation> {
    private static final ResourceLocation GESTURE =
        ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "brush_sleeve");

    private static void register() {
      // Iron's Spells applies its casting layer at priority 42.
      PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
          GESTURE, 10, player -> player instanceof LocalPlayer ? new GestureLayer() : null);
      if (ModList.get().isLoaded("entity_model_features")
          && ModList.get().isLoaded("entity_texture_features")) EmfCompatibility.register();
    }

    private static void update(Minecraft minecraft, boolean requested) {
      LocalPlayer player = minecraft.player;
      if (player == null) return;
      var layers = PlayerAnimationAccess.getPlayerAssociatedData(player);
      if (!(layers.get(GESTURE) instanceof GestureLayer gesture)) return;
      if (!requested && gesture.getAnimation() == null) return;

      if (minecraft.screen != null
          || minecraft.options.getCameraType().isFirstPerson()
          || !player.isAlive()
          || player.isSpectator()
          || player.isPassenger()
          || !player.onGround()
          || player.getPose() != Pose.STANDING
          || player.input.getMoveVector().lengthSquared() > 0
          || player.swinging
          || player.isUsingItem()
          || !player.getMainHandItem().isEmpty()
          || !player.getOffhandItem().isEmpty()
          || (ModList.get().isLoaded("irons_spellbooks")
              && (ClientMagicData.isCasting()
                  || active(layers.get(SpellAnimations.ANIMATION_RESOURCE))))) {
        gesture.setAnimation(null);
      } else if (!gesture.isActive()) {
        gesture.setAnimation(
            requested
                ? PlayerAnimationRegistry.getAnimationOptional(GESTURE)
                    .map(
                        animation ->
                            animation.playAnimation().setFirstPersonMode(FirstPersonMode.NONE))
                    .orElse(null)
                : null);
      }
    }

    private static boolean active(IAnimation animation) {
      return animation != null && animation.isActive();
    }
  }

  private static final class EmfCompatibility {
    private static void register() {
      try {
        // FA Player replaces the model's arm transforms after Player Animator sets them up.
        EMFAnimationApi.registerPauseCondition(
            entity -> {
              if (!(entity instanceof AbstractClientPlayer player)) return false;
              var layers = PlayerAnimationAccess.getPlayerAssociatedData(player);
              return GestureLayer.active(layers.get(GestureLayer.GESTURE))
                  || (ModList.get().isLoaded("irons_spellbooks")
                      && GestureLayer.active(layers.get(SpellAnimations.ANIMATION_RESOURCE)));
            });
      } catch (Exception failure) {
        throw new IllegalStateException("Cannot register Player Animator compatibility", failure);
      }
    }
  }
}
