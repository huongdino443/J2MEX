// ============================================================================
//  lcd_grid.frag  --  R29-MED: Medium CRT (clearly visible CRT character)
// ----------------------------------------------------------------------------
//  Visible scanlines + subpixel mask + mild vignette + moderate bloom.
//  The "standard" CRT look — clearly retro but not overwhelming.
//  Warm tint preserved, brightness compensated for mask/scanline darkening.
// ============================================================================
#version 100
precision highp float;

uniform sampler2D uTexture;
uniform vec2 uScreenSize;
uniform vec2 uOutputSize;
uniform vec2 uOutputOffset;
uniform vec2 uSrcSize;

varying vec2 vTexCoord;
varying vec2 vPixCoord;

// --- R29-MED tunables -------------------------------------------------------
const float MASK_DARK      = 0.25;  // visible subpixel mask
const float SCANLINE_DARK  = 0.22;  // clearly visible scanlines
const float BRIGHTNESS     = 1.12;  // compensate for mask+scan darkening
const float BLOOM_STRENGTH = 0.06;  // moderate glow
const float WARM_R         = 1.03;
const float WARM_B         = 0.97;
const float VIGNETTE       = 0.10;  // subtle corner darkening
const float CONTRAST       = 1.05;  // slight contrast boost

void main() {
    vec2 fboUv = vec2(vTexCoord.x, 1.0 - vTexCoord.y);
    vec3 color = texture2D(uTexture, fboUv).rgb;

    // --- bloom (moderate) ---
    vec2 fboTexel = 1.0 / uOutputSize;
    vec3 blur = (
        texture2D(uTexture, fboUv + vec2( fboTexel.x, 0.0)).rgb +
        texture2D(uTexture, fboUv + vec2(-fboTexel.x, 0.0)).rgb +
        texture2D(uTexture, fboUv + vec2(0.0,  fboTexel.y)).rgb +
        texture2D(uTexture, fboUv + vec2(0.0, -fboTexel.y)).rgb
    ) * 0.25;
    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    color += blur * BLOOM_STRENGTH * smoothstep(0.65, 1.0, lum);

    // --- RGB subpixel mask ---
    float stripe = mod(gl_FragCoord.x, 3.0);
    float d = MASK_DARK;
    vec3 mask = vec3(1.0 - d);
    if      (stripe < 0.5) mask.r = 1.0;
    else if (stripe < 1.5) mask.g = 1.0;
    else                   mask.b = 1.0;
    color *= mask;
    color *= 3.0 / (3.0 - 2.0 * d);

    // --- warm tint ---
    color.r *= WARM_R;
    color.b *= WARM_B;

    // --- scanlines ---
    float row = mod(gl_FragCoord.y, 2.0);
    float scan = (row < 1.0) ? 1.0 : (1.0 - SCANLINE_DARK);
    color *= scan;

    // --- contrast ---
    color = (color - 0.5) * CONTRAST + 0.5;

    // --- vignette ---
    vec2 vUv = vTexCoord - 0.5;
    float vig = 1.0 - dot(vUv, vUv) * VIGNETTE * 2.0;
    color *= clamp(vig, 0.0, 1.0);

    // --- brightness ---
    color *= BRIGHTNESS;

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
