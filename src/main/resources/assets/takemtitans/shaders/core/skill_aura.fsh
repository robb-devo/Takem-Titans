#version 150

// Animated backdrop of the skill tree: slow smoke, a glow in the branch colour, rising embers.

uniform float Time;
uniform float Aspect;
uniform vec4 Accent;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 uv = texCoord;
    vec2 p = (uv - 0.5) * vec2(Aspect, 1.0);
    float t = Time * 0.04;
    vec2 q = vec2(fbm(p * 2.0 + t), fbm(p * 2.0 - t + 3.1));
    float n = fbm(p * 2.5 + q * 1.6 + vec2(t * 0.7, -t * 0.4));
    vec3 col = mix(vec3(0.035, 0.03, 0.05), vec3(0.17, 0.14, 0.11), smoothstep(0.35, 0.85, n));
    col += Accent.rgb * pow(smoothstep(0.55, 0.95, n), 2.0) * 0.6;

    vec2 g = uv * vec2(Aspect, 1.0) * 26.0;
    g.y += Time * 0.7;
    vec2 cell = floor(g);
    vec2 f = fract(g) - 0.5;
    float h = hash(cell);
    float ember = step(0.962, h) * (1.0 - smoothstep(0.0, 0.13, length(f + vec2(sin(h * 40.0 + Time) * 0.25, 0.0))));
    col += vec3(1.0, 0.62, 0.22) * ember * (0.55 + 0.45 * sin(Time * 3.0 + h * 20.0));

    col *= (1.0 - smoothstep(0.2, 1.2, length(p)));
    fragColor = vec4(col, 0.95);
}
