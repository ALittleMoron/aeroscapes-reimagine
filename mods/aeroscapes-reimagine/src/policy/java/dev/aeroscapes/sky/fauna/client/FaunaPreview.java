package dev.aeroscapes.sky.fauna.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.aeroscapes.aquatic.PreviewRaster.Quad;
import dev.aeroscapes.aquatic.PreviewRaster.Vertex;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy.Form;
import dev.aeroscapes.sky.fauna.CloudJellyMotion;
import dev.aeroscapes.sky.fauna.SkyRayWings;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/**
 * Poses the pack's own model exactly as its animation code does and captures its native vertex
 * buffer.
 */
public final class FaunaPreview {
  private FaunaPreview() {}

  public static List<Quad> capture(String subject, float tick) throws IOException {
    if (subject.equals("sky_ray/pair")) {
      var pair = new ArrayList<Quad>();
      appendOffset(pair, capture("sky_ray", tick), -5);
      appendOffset(pair, capture("sky_ray/calf", tick + 5), 5);
      return pair;
    }
    ModelPart root;
    String texture;
    float diameter = 1;
    if (subject.equals("sky_ray") || subject.equals("sky_ray/calf")) {
      boolean calf = subject.endsWith("/calf");
      var ray = new SkyRayModel(SkyRayModel.layer().bakeRoot());
      ray.pose(new SkyRayWings.Pose(tick * .16f, .85f, 0, 0), false, calf);
      root = ray.root();
      texture = "textures/entity/sky_ray.png";
      diameter = calf ? SkyRayWings.CALF_SCALE : 1;
    } else {
      String prefix = "cloud_jelly/";
      if (!subject.startsWith(prefix))
        throw new IllegalArgumentException("Unsupported preview creature: " + subject);
      String[] state = subject.substring(prefix.length()).split("/", -1);
      if (state.length > 2)
        throw new IllegalArgumentException("Expected form and an optional jelly skin");
      String id = state[0];
      Form form = Form.byId(id);
      if (!form.id().equals(id)) throw new IllegalArgumentException("Unknown jelly form: " + id);
      String skin = state.length == 1 ? "idle" : state[1];
      diameter =
          switch (skin) {
            case "idle", "pulse" -> 1;
            case "large", "large_pulse" -> 4;
            default -> throw new IllegalArgumentException("Unknown jelly skin: " + skin);
          };
      var jelly = new CloudJellyModel(CloudJellyModel.layer().bakeRoot());
      jelly.pose(form, CloudJellyMotion.sample(form, diameter, 0L, tick, 0, false));
      root = jelly.root();
      texture = FaunaRenderers.jellyTexture(form, diameter, skin.endsWith("pulse")).getPath();
    }
    BufferedImage img;
    try (var stream =
        FaunaPreview.class.getResourceAsStream("/assets/aeroscapes_reimagine/" + texture)) {
      if (stream == null) throw new IOException("Missing creature texture " + texture);
      img = ImageIO.read(stream);
    }
    var pose = new PoseStack();
    pose.scale(diameter, -diameter, -diameter);
    try (var storage = new ByteBufferBuilder(32768)) {
      var format = DefaultVertexFormat.NEW_ENTITY;
      var builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, format);
      root.render(pose, builder, 15728880, 0);
      try (var mesh = builder.buildOrThrow()) {
        var buffer = mesh.vertexBuffer();
        int stride = format.getVertexSize(), uv = format.getOffset(VertexFormatElement.UV0);
        var faces = new ArrayList<Quad>();
        for (int face = 0; face < mesh.drawState().vertexCount(); face += 4) {
          var vertices = new ArrayList<Vertex>();
          for (int i = face; i < face + 4; i++) {
            int pos = i * stride;
            vertices.add(
                new Vertex(
                    new Vector3f(
                        buffer.getFloat(pos), buffer.getFloat(pos + 4), buffer.getFloat(pos + 8)),
                    buffer.getFloat(pos + uv),
                    buffer.getFloat(pos + uv + 4)));
          }
          faces.add(new Quad(vertices, img, 0xffffffff));
        }
        return faces;
      }
    }
  }

  private static void appendOffset(List<Quad> output, List<Quad> model, float x) {
    for (Quad face : model) {
      var vertices =
          face.vertices().stream()
              .map(
                  vertex ->
                      new Vertex(
                          new Vector3f(vertex.position()).add(x, 0, 0), vertex.u(), vertex.v()))
              .toList();
      output.add(new Quad(vertices, face.texture(), face.color()));
    }
  }
}
