package dev.aeroscapes.equipment.client;

import dev.aeroscapes.equipment.client.WornCurioLayer.Shape;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

final class WornCurioModels {
  private WornCurioModels() {}

  static Map<Shape, ModelPart> create() {
    var models = new EnumMap<Shape, ModelPart>(Shape.class);
    models.put(Shape.BAND, ring(false));
    models.put(Shape.GEM_RING, ring(true));
    models.put(Shape.CHAIN, necklace(Shape.CHAIN));
    models.put(Shape.PENDANT, necklace(Shape.PENDANT));
    models.put(Shape.TALISMAN, necklace(Shape.TALISMAN));
    models.put(Shape.PEARLS, necklace(Shape.PEARLS));
    models.put(Shape.CHALICE, necklace(Shape.CHALICE));
    models.put(Shape.KEY, key());
    models.put(Shape.THERMOMETER, thermometer());
    models.put(Shape.TERMINAL, terminal());
    return models;
  }

  private static ModelPart ring(boolean stone) {
    var band =
        CubeListBuilder.create()
            .addBox(-0.47f, -0.3f, -2.43f, 0.94f, 1.08f, 0.24f)
            .addBox(-0.47f, 0.78f, -2.43f, 0.94f, 0.32f, 4.86f)
            .addBox(-0.47f, -0.3f, 2.19f, 0.94f, 1.08f, 0.24f);
    var face = CubeListBuilder.create();
    if (stone) {
      band.addBox(-0.61f, -0.5f, -2.64f, 1.22f, 1.22f, 0.31f);
      face.addBox(-0.46f, -0.36f, -2.92f, 0.92f, 0.94f, 0.31f);
    } else {
      face.addBox(-0.54f, -0.17f, -2.59f, 1.08f, 0.42f, 0.21f);
    }
    return bake(band, face);
  }

  private static ModelPart necklace(Shape shape) {
    float thickness = shape == Shape.CHAIN ? 0.59f : 0.29f;
    var mount =
        CubeListBuilder.create()
            .addBox(-2.05f, 0.1f, 2.31f, 4.1f, thickness, thickness)
            .addBox(-2.33f, 0.1f, -2.4f, thickness, thickness, 5)
            .addBox(2.04f, 0.1f, -2.4f, thickness, thickness, 5);
    var face = CubeListBuilder.create();
    switch (shape) {
      case CHAIN, PEARLS -> {
        for (int bead = -4; bead <= 4; bead++) {
          float x = bead * 0.53f;
          float y = 2.15f - Math.abs(bead) * 0.41f;
          if (shape == Shape.CHAIN) {
            var link = bead % 2 == 0 ? face : mount;
            link.addBox(x - 0.33f, y - 0.3f, -2.83f, 0.66f, 0.68f, 0.48f);
          } else {
            float size = bead == 0 ? 0.86f : (bead % 2 == 0 ? 0.67f : 0.54f);
            mount.addBox(x - size / 2, y - size / 2, -2.77f, size, size, 0.42f);
            face.addBox(x - size / 3, y - size / 3, -2.93f, size * 0.66f, size * 0.66f, 0.21f);
          }
        }
      }
      case PENDANT -> {
        mount.addBox(-0.35f, 1.9f, -2.76f, 0.7f, 0.72f, 0.36f);
        mount.addBox(-0.75f, 2.32f, -2.97f, 1.5f, 1.64f, 0.65f);
        face.addBox(-0.51f, 2.52f, -3.22f, 1.02f, 1.13f, 0.32f);
      }
      case TALISMAN -> {
        mount.addBox(-0.25f, 1.9f, -2.66f, 0.5f, 0.78f, 0.3f);
        face.addBox(-0.29f, 2.35f, -2.97f, 0.58f, 2.01f, 0.59f);
        face.addBox(-0.78f, 2.67f, -2.97f, 1.56f, 0.53f, 0.59f);
      }
      case CHALICE -> {
        mount.addBox(-1.05f, 2.23f, -3.1f, 2.1f, 0.37f, 0.8f);
        mount.addBox(-0.78f, 2.55f, -2.95f, 1.56f, 0.55f, 0.62f);
        mount.addBox(-0.31f, 2.95f, -2.9f, 0.62f, 0.66f, 0.58f);
        mount.addBox(-0.62f, 3.54f, -2.95f, 1.24f, 0.28f, 0.62f);
        face.addBox(-0.77f, 2.16f, -2.94f, 1.54f, 0.22f, 0.47f);
      }
      default -> throw new IllegalArgumentException("Not a necklace: " + shape);
    }
    var mesh = new MeshDefinition();
    var base = mesh.getRoot().addOrReplaceChild("base", mount, PartPose.ZERO);
    mesh.getRoot().addOrReplaceChild("detail", face, PartPose.ZERO);
    if (shape != Shape.CHAIN && shape != Shape.PEARLS) {
      base.addOrReplaceChild(
          "left",
          CubeListBuilder.create().addBox(0, 0, 0, 2.9f, thickness, thickness),
          PartPose.offsetAndRotation(-2.21f, 0.18f, -2.51f, 0, 0, 0.63f));
      base.addOrReplaceChild(
          "right",
          CubeListBuilder.create().addBox(-2.9f, 0, 0, 2.9f, thickness, thickness),
          PartPose.offsetAndRotation(2.21f, 0.18f, -2.51f, 0, 0, -0.63f));
    }
    return LayerDefinition.create(mesh, 32, 32).bakeRoot();
  }

  private static CubeListBuilder beltClip() {
    return CubeListBuilder.create().addBox(-0.43f, -0.68f, -0.29f, 0.86f, 1.12f, 0.63f);
  }

  private static ModelPart key() {
    var shaft =
        beltClip()
            .addBox(-0.16f, 1.3f, -0.64f, 0.34f, 1.87f, 0.42f)
            .addBox(0.08f, 2.45f, -0.64f, 0.61f, 0.32f, 0.42f)
            .addBox(0.08f, 2.97f, -0.64f, 0.44f, 0.32f, 0.42f);
    var bow =
        CubeListBuilder.create()
            .addBox(-0.61f, 0.13f, -0.75f, 1.22f, 0.29f, 0.56f)
            .addBox(-0.61f, 0.4f, -0.75f, 0.29f, 0.87f, 0.56f)
            .addBox(0.32f, 0.4f, -0.75f, 0.29f, 0.87f, 0.56f)
            .addBox(-0.61f, 1.22f, -0.75f, 1.22f, 0.29f, 0.56f);
    return bake(shaft, bow);
  }

  private static ModelPart thermometer() {
    var caseBody =
        beltClip()
            .addBox(-0.86f, -0.03f, -0.68f, 1.72f, 1.97f, 0.69f)
            .addBox(-0.58f, -0.28f, -0.68f, 1.16f, 0.34f, 0.69f)
            .addBox(-0.58f, 1.89f, -0.68f, 1.16f, 0.34f, 0.69f);
    var glass = CubeListBuilder.create().addBox(-0.58f, 0.31f, -0.8f, 1.16f, 1.25f, 0.2f);
    return bake(caseBody, glass);
  }

  private static ModelPart terminal() {
    var caseBody =
        beltClip()
            .addBox(-1.12f, -0.11f, -0.89f, 2.24f, 2.75f, 0.98f)
            .addBox(0.57f, -0.72f, -0.43f, 0.24f, 0.73f, 0.26f);
    var buttons = CubeListBuilder.create();
    for (int row = 0; row < 3; row++) {
      for (int column = 0; column < 3; column++)
        buttons.addBox(-0.76f + column * 0.53f, 0.49f + row * 0.57f, -1.02f, 0.46f, 0.5f, 0.2f);
    }
    return bake(caseBody, buttons);
  }

  private static ModelPart bake(CubeListBuilder base, CubeListBuilder detail) {
    var mesh = new MeshDefinition();
    mesh.getRoot().addOrReplaceChild("base", base, PartPose.ZERO);
    mesh.getRoot().addOrReplaceChild("detail", detail, PartPose.ZERO);
    return LayerDefinition.create(mesh, 32, 32).bakeRoot();
  }
}
