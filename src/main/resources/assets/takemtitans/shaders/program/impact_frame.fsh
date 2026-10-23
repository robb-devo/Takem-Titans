#version 150

// Anime impact frame: the picture snaps to stark black and white ink, blood stays red,
// speed lines burst from the middle. Phase 0 is the negative (white ink on black), phase 1 the print.
// Heat tints the whole frame towards blazing orange (used by the Colossal blast).

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Phase;
uniform float Strength;
uniform float Seed;
uniform float Heat;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float lum(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float lumAt(vec2 o) {
    return lum(texture(DiffuseSampler, texCoord + oneTexel * o).rgb);
}

float hash(float n) {
    return fract(sin(n) * 43758.5453);
}

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float l = lum(c);

    // Ink outlines from the brightness edges
    float tl = lumAt(vec2(-1.5, 1.5)), tc = lumAt(vec2(0.0, 1.5)), tr = lumAt(vec2(1.5, 1.5));
    float ml = lumAt(vec2(-1.5, 0.0)), mr = lumAt(vec2(1.5, 0.0));
    float bl = lumAt(vec2(-1.5, -1.5)), bc = lumAt(vec2(0.0, -1.5)), br = lumAt(vec2(1.5, -1.5));
    float gx = -tl - 2.0 * ml - bl + tr + 2.0 * mr + br;
    float gy = -tl - 2.0 * tc - tr + bl + 2.0 * bc + br;
    float edge = step(0.22, sqrt(gx * gx + gy * gy));

    float lit = step(0.40, l);

    // Speed lines radiating from the centre of the screen
    vec2 d = texCoord - vec2(0.5);
    d.x *= InSize.x / InSize.y;
    float ang = atan(d.y, d.x);
    float dist = length(d);
    float ray = floor((ang + 3.14159265) / 6.2831853 * 220.0);
    float h = hash(ray * 1.37 + Seed * 11.0);
    float start = 0.16 + 0.25 * hash(ray * 3.11 + Seed * 7.0);
    float lines = step(0.78, h) * smoothstep(start, start + 0.12, dist);

    float ink;
    if (Phase < 0.5) {
        ink = 1.0 - lit;
        ink = max(ink, edge);
        ink = max(ink, lines);
    } else {
        ink = lit;
        ink = min(ink, 1.0 - edge);
        ink = min(ink, 1.0 - lines);
    }
    vec3 paper = mix(vec3(ink), vec3(ink) * vec3(1.0, 0.55, 0.18) + vec3(0.25, 0.05, 0.0) * (1.0 - ink), Heat);

    // Blood and Ackerman red survive as pure red
    bool red = c.r > 0.28 && c.r > c.g * 1.8 && c.r > c.b * 1.8;
    if (red) paper = vec3(0.92, 0.04, 0.07);

    fragColor = vec4(mix(c, paper, Strength), 1.0);
}
