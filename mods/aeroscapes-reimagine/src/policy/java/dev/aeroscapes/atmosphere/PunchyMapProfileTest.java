package dev.aeroscapes.atmosphere;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import punchy.client.renderer.layer.ArmToolTuning;
import punchy.config.PunchyTuningConfig;

/** Exercises the provider's decoding and composition, including omitted primitive defaults. */
public final class PunchyMapProfileTest {
  private PunchyMapProfileTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    JsonObject resource =
        new Gson()
            .fromJson(
                Files.readString(Path.of(System.getProperty("aeroscapes.map.profile"))),
                JsonObject.class);
    var sides =
        new Gson()
            .fromJson(
                resource
                    .getAsJsonObject("itemSpecific")
                    .getAsJsonObject("minecraft:filled_map")
                    .get("armTransforms"),
                PunchyTuningConfig.SideSet.class);
    var compose =
        PunchyTuningConfig.class.getDeclaredMethod(
            "applyGlobalOffsetsToSpecificArmProfile",
            ArmToolTuning.Profile.class,
            ArmToolTuning.Profile.class);
    compose.setAccessible(true);
    for (var pose : List.of(sides.right, sides.left)) {
      var profile =
          (ArmToolTuning.Profile)
              compose.invoke(null, pose.toArm(), new ArmToolTuning.Profile(1, 0, 0, 0, 0, 0, 0));
      if (profile.scale != 1 || profile.offY != -0.2f)
        throw new AssertionError(
            "Map hand must stay visible at the raised height: scale="
                + profile.scale
                + " offY="
                + profile.offY);
    }
    System.out.println("Punchy map native profile decoding and both hand scales passed");
  }
}
