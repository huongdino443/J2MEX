// ============================================================================
//  xbrz_bicubic.frag  --  Sharp-Bilinear upscaling (Pass 1)
// ----------------------------------------------------------------------------
//  R19: SRC_SIZE is now a uniform (uSrcSize) so the upscaler works for both
//  portrait (360x640) and landscape (640x360) source frames.  The maths are
//  otherwise unchanged from R18 -- pure sharp-bilinear, pixel-perfect at
//  integer scale, no xBRZ blur.
//
//  GLSL ES 1.00 (GLES 2.0 baseline).
// ============================================================================
#version 100
precision highp float;

uniform sampler2D uTexture;
uniform vec2 uOutputSize;     // size of the FBO we're rendering into
uniform vec2 uSrcSize;        // R19: dynamic source size (360x640 or 640x360)

varying vec2 vTexCoord;
varying vec2 vPixCoord;

const float SHARPNESS = 1.0;  // 1.0 = pixel-perfect at integer scale

void main() {
    // Scale factor from source to output
    vec2 scale = uOutputSize / uSrcSize;

    // Which source pixel this fragment maps to, and the fractional
    // position inside that source pixel.
    vec2 px = vPixCoord;               // 0..uOutputSize
    vec2 f  = fract(px / scale);       // 0..1 inside the source pixel

    // Sharp-bilinear: push the fractional coordinate toward 0.5 (the
    // pixel centre).  With SHARPNESS=1.0 at an integer scale this
    // collapses to exactly 0.5 -> pure nearest-neighbour -> no blur.
    vec2 sharp = mix(vec2(0.5), f, clamp(SHARPNESS, 0.0, 1.0));
    vec2 uv    = (floor(px / scale) + sharp) / uSrcSize;

    vec3 color = texture2D(uTexture, uv).rgb;

    gl_FragColor = vec4(color, 1.0);
}
