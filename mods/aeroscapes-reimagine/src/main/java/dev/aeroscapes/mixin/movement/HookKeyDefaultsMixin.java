package dev.aeroscapes.mixin.movement;

import com.yori3o.yo_hooks.common.YoHooksClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = YoHooksClient.class, remap = false)
public abstract class HookKeyDefaultsMixin {
  @ModifyArgs(
      method = "<clinit>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/KeyMapping;<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V"),
      require = 4)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Four native mappings; only the two rope keys change.
  private static void aeroscapes$ropeDefaults(Args arguments) {
    String name = arguments.get(0);
    if (name.equals("key.yo_hooks.climb")) arguments.set(2, GLFW.GLFW_KEY_LEFT_SHIFT);
    else if (name.equals("key.yo_hooks.climb_down")) arguments.set(2, GLFW.GLFW_KEY_LEFT_CONTROL);
  }
}
