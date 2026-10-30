#version 150

// Takem Titans - the combat post pass: heat haze, a shock ripple, a radial smear with the colour channels split along
// it, and a tinted grade with flash and vignette. Every term is off at zero, and the pass only runs while one is live.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float Clock;
uniform float Aberration;
uniform float Zoom;
uniform float CenterX;
uniform float CenterY;
uniform float RippleT;
uniform float RippleS;
uniform float RippleX;
uniform float RippleY;
uniform float Heat;
uniform float Tint;
uniform float TintR;
uniform float TintG;
uniform float TintB;
uniform float Vignette;
uniform float Flash;

out vec4 fragColor;

void main() {
    vec2 aspect = vec2(InSize.x / InSize.y, 1.0);
    vec2 uv = texCoord;

    // heat haze: a shimmer, strongest toward the edges of the view
    if (Heat > 0.001) {
        float edge = 0.35 + 0.65 * smoothstep(0.1, 0.8, length((uv - 0.5) * aspect));
        vec2 wobble = vec2(
            sin(uv.y * 41.0 + Clock * 5.3) + 0.5 * sin(uv.y * 97.0 - Clock * 7.9),
            cos(uv.x * 37.0 + Clock * 4.1) + 0.5 * cos(uv.x * 83.0 + Clock * 6.7));
        uv += wobble * edge * Heat * 0.0021;
    }

    // a shock ripple: the picture bulges outward along a ring racing away from where the blow landed
    if (RippleS > 0.001) {
        vec2 d = (uv - vec2(RippleX, RippleY)) * aspect;
        float r = length(d);
        float x = (r - RippleT * 1.4) / 0.075;
        float ring = exp(-x * x) * RippleS * (1.0 - RippleT);
        uv -= (d / max(r, 0.0001)) / aspect * ring * 0.05;
    }

    // radial smear from the centre of the blow, the colour channels pulled apart along it
    vec2 cen = vec2(CenterX, CenterY);
    vec2 dir = uv - cen;
    vec2 n = dir / max(length(dir), 0.0001);
    float split = Aberration * 0.011 * (0.3 + length(dir * aspect));
    int taps = Zoom > 0.002 ? 10 : 1;
    vec3 col = vec3(0.0);
    for (int i = 0; i < 10; i++) {
        if (i >= taps) {
            break;
        }
        float t = taps > 1 ? float(i) / float(taps - 1) : 0.0;
        vec2 p = cen + dir * (1.0 - Zoom * 0.085 * t);
        col.r += texture(DiffuseSampler, p + n * split).r;
        col.g += texture(DiffuseSampler, p).g;
        col.b += texture(DiffuseSampler, p - n * split).b;
    }
    col /= float(taps);

    // grade: pull toward a tinted duotone, add the flash, darken the corners
    vec3 tone = vec3(TintR, TintG, TintB);
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, tone * (luma * 1.3 + 0.06), Tint);
    col += tone * Flash;
    float v = smoothstep(0.38, 1.1, length((texCoord - 0.5) * aspect) * 1.3);
    col *= 1.0 - Vignette * v;
    fragColor = vec4(col, 1.0);
}
