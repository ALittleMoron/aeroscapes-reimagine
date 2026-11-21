package dev.aeroscapes.sky.fauna.client;

import dev.aeroscapes.sky.fauna.CloudJelly;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy.Form;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy.Shape;
import dev.aeroscapes.sky.fauna.CloudJellyMotion;
import java.util.EnumMap;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

final class CloudJellyModel extends HierarchicalModel<CloudJelly> {
  private static final int TEXTURE_SCALE = 4;
  private static final int TEXTURE_WIDTH = 512;
  private static final int TEXTURE_HEIGHT = 768;
  private final EnumMap<Form, Rig> forms = new EnumMap<>(Form.class);
  private Rig active;
  private CloudJellyMotion swim;

  CloudJellyModel(ModelPart layerRoot) {
    super(RenderType::entityTranslucentCull);
    for (Form form : Form.values()) {
      Shape shape = CloudJellyAnatomy.of(form);
      ModelPart root = layerRoot.getChild(form.id());
      root.xScale = root.yScale = root.zScale = 1F / TEXTURE_SCALE;
      var shell = new ModelPart[shape.bell().size()];
      var strands = new ModelPart[shape.strands().size()][];
      for (int i = 0; i < shell.length; i++) shell[i] = root.getChild("shell" + i);
      for (int i = 0; i < strands.length; i++) {
        strands[i] = new ModelPart[shape.strands().get(i).segments().size()];
        for (int j = 0; j < strands[i].length; j++)
          strands[i][j] = root.getChild("strand" + i + "_" + j);
      }
      forms.put(form, new Rig(root, shell, strands));
    }
    active = forms.get(Form.FRINGE);
  }

  @Override
  public ModelPart root() {
    return active.root();
  }

  static LayerDefinition layer() {
    var mesh = new MeshDefinition();
    for (Form form : Form.values()) {
      Shape shape = CloudJellyAnatomy.of(form);
      var root =
          mesh.getRoot()
              .addOrReplaceChild(
                  form.id(), CubeListBuilder.create(), PartPose.offset(0, 24 - shape.height(), 0));
      var atlas = new Atlas();
      for (int i = 0; i < shape.bell().size(); i++) {
        var b = shape.bell().get(i);
        add(
            root,
            atlas,
            "shell" + i,
            b.maxX() - b.minX(),
            b.bottom() - b.top(),
            b.maxZ() - b.minZ());
      }
      for (int i = 0; i < shape.strands().size(); i++) {
        var strand = shape.strands().get(i);
        for (int j = 0; j < strand.segments().size(); j++) {
          var b = strand.segments().get(j);
          add(root, atlas, "strand" + i + "_" + j, b.width(), b.bottom() - b.top(), b.depth());
        }
      }
    }
    return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
  }

  private static void add(
      PartDefinition root, Atlas atlas, String name, float w, float h, float d) {
    var cubes = CubeListBuilder.create();
    atlas.box(cubes, 0, 0, 0, w, h, d);
    root.addOrReplaceChild(name, cubes, PartPose.ZERO);
  }

  @Override
  public void setupAnim(
      CloudJelly jelly, float stride, float speed, float age, float yaw, float pitch) {
    pose(
        jelly.form(),
        jelly.swimFrame(
            jelly.level().getGameTime() - 1.0 + Mth.clamp(age - jelly.tickCount, 0, 1)));
  }

  void pose(Form form, CloudJellyMotion swim) {
    active = forms.get(form);
    this.swim = swim;
    var shape = swim.shape();
    var basis = swim.basis;
    // Convert world rotation to the living model's axes (world Y and Z are reversed).
    active.root.xRot = (float) Math.atan2(basis.y.z, basis.z.z);
    active.root.yRot = (float) Math.asin(Mth.clamp(basis.x.z, -1, 1));
    active.root.zRot = (float) Math.atan2(-basis.x.y, basis.x.x);
    for (int i = 0; i < active.shell.length; i++) {
      var b = shape.bell().get(i);
      place(
          active.shell[i],
          swim.shell(i),
          b.maxX() - b.minX(),
          b.bottom() - b.top(),
          b.maxZ() - b.minZ());
    }
    for (int i = 0; i < active.strands.length; i++) {
      var segments = shape.strands().get(i).segments();
      for (int j = 0; j < segments.size(); j++) {
        var b = segments.get(j);
        place(active.strands[i][j], swim.segment(i, j), b.width(), b.bottom() - b.top(), b.depth());
      }
    }
  }

  CloudJellyMotion swim() {
    return swim;
  }

  private static void place(ModelPart p, AABB b, float w, float h, float d) {
    p.setPos(
        (float) b.minX * TEXTURE_SCALE,
        (float) b.minY * TEXTURE_SCALE,
        (float) -b.maxZ * TEXTURE_SCALE);
    p.xScale = (float) b.getXsize() / w;
    p.yScale = (float) b.getYsize() / h;
    p.zScale = (float) b.getZsize() / d;
  }

  private record Rig(ModelPart root, ModelPart[] shell, ModelPart[][] strands) {}

  private static final class Atlas {
    private int x, y, rowHeight;

    private void box(
        CubeListBuilder cubes,
        float left,
        float top,
        float front,
        float width,
        float height,
        float depth) {
      float w = width * TEXTURE_SCALE, h = height * TEXTURE_SCALE, d = depth * TEXTURE_SCALE;
      int span = Mth.ceil(2 * (w + d)) + 2, rows = Mth.ceil(h + d) + 2;
      if (x + span > TEXTURE_WIDTH) {
        x = 0;
        y += rowHeight;
        rowHeight = 0;
      }
      if (y + rows > TEXTURE_HEIGHT)
        throw new IllegalStateException("Cloud jelly geometry exceeds its texture atlas");
      cubes
          .texOffs(x + 1, y + 1)
          .addBox(left * TEXTURE_SCALE, top * TEXTURE_SCALE, front * TEXTURE_SCALE, w, h, d);
      x += span;
      rowHeight = Math.max(rowHeight, rows);
    }
  }
}
