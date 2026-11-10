#version 150

uniform sampler2D DhDepthSampler;
uniform float DhFarDepth;
uniform vec4 ColorModulator;

in vec4 vertexColor;
out vec4 fragColor;

void main() {
    if (vertexColor.a == 0.0) discard;

    // A non-clear LOD pixel is terrain, regardless of DH's projection or depth direction.
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    if (all(lessThan(pixel, textureSize(DhDepthSampler, 0)))
            && texelFetch(DhDepthSampler, pixel, 0).r != DhFarDepth) discard;

    gl_FragDepth = 1.0;
    fragColor = vertexColor * ColorModulator;
}
