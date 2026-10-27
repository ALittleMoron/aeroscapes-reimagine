package dev.aeroscapes.worldgen.opencl;

/** A world can use GPU generation only after a complete program has been published. */
public interface OpenClReadiness {
  boolean aeroscapesReimagine$hasCompiledDevice();
}
