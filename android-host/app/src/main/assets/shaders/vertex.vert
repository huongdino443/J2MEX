// ============================================================================
//  vertex.vert  --  shared vertex shader for the phoneME GL pipeline
// ----------------------------------------------------------------------------
//  Used by BOTH the upscale pass (xbrz_bicubic.frag) and the screen pass
//  (lcd_grid.frag).  This is the standard Libretro/RetroArch "stock"
//  vertex shader, trimmed to the uniforms our two-pass chain needs:
//
//    uniform vec2  uOutputOffset;   // (screen pass only) top-left of the
//                                   //   integer-scaled quad, in pixels
//    uniform vec2  uOutputSize;     // size of the scaled quad, in pixels
//    uniform vec2  uScreenSize;     // full viewport, in pixels
//
//  The vertex shader maps the unit quad (-1..1) into the centred
//  integer-scaled rectangle so the fragment shader only ever has to
//  reason in texel/screen-pixel space - exactly like EKA2L1's
//  `stock.glsl` vertex stage.
//
//  GLSL ES 1.00 (compatible with every GLES 2.0 device, which is the
//  baseline phoneME is being raised to).
// ============================================================================
#version 100
precision highp float;

attribute vec2 aPosition;   // -1..1 quad
attribute vec2 aTexCoord;   //  0..1 uv

uniform vec2  uOutputOffset; // pixels (screen pass); 0 for upscale pass
uniform vec2  uOutputSize;   // pixels
uniform vec2  uScreenSize;   // pixels

varying vec2 vTexCoord;
varying vec2 vPixCoord;      // fragment position in output pixels

void main() {
    vTexCoord = aTexCoord;

    // Map the -1..1 quad into the centred integer-scaled rectangle.
    // If uScreenSize == uOutputSize (upscale pass writes into an FBO
    // sized exactly to the output) this collapses to the identity and
    // vPixCoord covers 0..uOutputSize.
    vec2 range   = uOutputSize / uScreenSize;        // 0..1 fraction
    vec2 origin  = uOutputOffset / uScreenSize * 2.0; // NDC offset
    vec2 pos     = (aPosition * 0.5 + 0.5) * range * 2.0 - 1.0 + origin;

    gl_Position  = vec4(pos, 0.0, 1.0);

    // pixel coordinate inside the output rect (0..uOutputSize)
    vPixCoord    = (aTexCoord * uOutputSize);
}
