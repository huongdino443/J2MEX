// ============================================================================
//  passthrough.frag  --  fallback (no-filter) fragment shader
// ----------------------------------------------------------------------------
//  Used by GLRenderer when either xbrz_bicubic.frag or lcd_grid.frag fail
//  to compile on a particular GPU.  It samples the source texture with the
//  vertex shader's interpolated UV - effectively a plain textured quad so
//  the emulator remains playable even if the fancy filters are unavailable.
// ============================================================================
#version 100
precision highp float;

uniform sampler2D uTexture;

varying vec2 vTexCoord;
varying vec2 vPixCoord;

void main() {
    gl_FragColor = texture2D(uTexture, vTexCoord);
}
