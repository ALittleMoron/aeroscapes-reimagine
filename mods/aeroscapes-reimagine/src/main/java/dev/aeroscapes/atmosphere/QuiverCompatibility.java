package dev.aeroscapes.atmosphere;

import java.util.LinkedList;
import traben.entity_model_features.models.jem_objects.EMFJemData;
import traben.entity_model_features.models.jem_objects.EMFPartData;

/** Combines locally loaded provider models without shipping their artwork. */
public final class QuiverCompatibility {
  private QuiverCompatibility() {}

  public static boolean compose(EMFJemData selected, EMFJemData base, String mob) {
    var attachments =
        selected.models.stream()
            .filter(
                part ->
                    "body".equals(part.part)
                        && "quiver".equals(part.id)
                        && (mob + "_quiver.jpm").equals(part.model)
                        && part.attach)
            .toList();
    if (attachments.size() != 1) return false;
    EMFPartData quiver = attachments.getFirst();
    selected.models = new LinkedList<>(base.models);
    selected.models.add(quiver);
    selected.texture = base.texture;
    selected.textureSize = base.textureSize;
    selected.shadow_size = base.shadow_size;
    return true;
  }
}
