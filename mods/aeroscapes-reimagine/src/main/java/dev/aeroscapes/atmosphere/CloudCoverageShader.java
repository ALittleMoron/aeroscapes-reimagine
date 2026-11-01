package dev.aeroscapes.atmosphere;

import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/** Corrects the pinned cloud depth comparison without replacing resource-pack overrides. */
public final class CloudCoverageShader {
  private static final String RESOURCE = "betterclouds:shaders/core/betterclouds_coverage.fsh";
  private static final String SOURCE_HASH =
      "dbbe307a51f42abf9cc0492c9e3d130734dd2facb088e0a33aedaca0bb88ec41";
  private static final AtomicBoolean WARNED = new AtomicBoolean();
  private static final Logger LOGGER = LogUtils.getLogger();

  private CloudCoverageShader() {}

  public static String adapt(String resource, String source) {
    if (!RESOURCE.equals(resource)) return source;
    if (!SOURCE_HASH.equals(digest(source))) {
      if (WARNED.compareAndSet(false, true)) {
        LOGGER.warn("Cloud depth adapter left an overridden Better Clouds shader unchanged");
      }
      return source;
    }
    return source
        .replace(
            "uniform sampler2D u_dh_depth_texture;",
            "uniform sampler2D u_dh_depth_texture;\nuniform mat4 u_dh_p_matrix;")
        .replace(
            "if(pass_dh_depth < depth) discard;",
            "if(u_dh_p_matrix[2][2] < 0.0 ? pass_dh_depth > depth : pass_dh_depth < depth)"
                + " discard;");
  }

  private static String digest(String source) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime has no SHA-256", exception);
    }
  }
}
