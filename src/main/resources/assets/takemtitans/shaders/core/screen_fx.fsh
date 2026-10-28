#version 150

// In-game screen effects. Fx.x Ackerman Overdrive, Fx.y Meteor Dive heat, Fx.z tunnel gloom, Fx.w low health.
// Warp.x hyperspace jump, Warp.y phase dash.

uniform float Time;
uniform float Aspect;
uniform vec4 Fx;
uniform vec2 Blood;
uniform vec2 Warp;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

vec4 acc;

void over(vec3 c, float a) {
    a = clamp(a, 0.0, 1.0);
    acc.rgb = acc.rgb * (1.0 - a) + c * a;
    acc.a = acc.a + a * (1.0 - acc.a);
}

void main() {
    vec2 uv = texCoord;
    vec2 p = (uv - 0.5) * vec2(Aspect, 1.0);
    float r = length(p);
    acc = vec4(0.0);

    if (Fx.z > 0.001) {
        over(vec3(0.0), smoothstep(0.25, 1.05, r) * 0.82 * Fx.z);
        vec2 g = uv * vec2(Aspect, 1.0) * 34.0 + vec2(Time * 0.15, Time * 0.25);
        vec2 cell = floor(g);
        float h = hash(cell);
        vec2 f = fract(g) - 0.5 + vec2(sin(Time * 0.7 + h * 30.0), cos(Time * 0.5 + h * 17.0)) * 0.25;
        float mote = step(0.93, h) * (1.0 - smoothstep(0.0, 0.09, length(f)));
        over(vec3(0.95, 0.85, 0.65), mote * 0.45 * Fx.z);
    }

    if (Fx.w > 0.001) {
        float beat = pow(max(0.0, sin(Time * 2.6)), 12.0) + 0.6 * pow(max(0.0, sin(Time * 2.6 - 0.55)), 12.0);
        over(vec3(0.55, 0.0, 0.02), smoothstep(0.3, 1.0, r) * (0.35 + 0.45 * beat) * Fx.w);
    }

    if (Fx.y > 0.001) {
        float fl = noise(vec2(uv.x * 9.0, uv.y * 5.0 + Time * 3.5)) * 0.6 + noise(vec2(uv.x * 23.0, uv.y * 13.0 + Time * 6.0)) * 0.4;
        float a = smoothstep(0.32, 0.95, r + (fl - 0.5) * 0.35) * Fx.y;
        over(mix(vec3(1.0, 0.25, 0.02), vec3(1.0, 0.85, 0.35), fl), a * 0.9);
    }

    if (Fx.x > 0.001) {
        float ang = atan(p.y, p.x);
        float streak = noise(vec2(ang * 14.0, Time * 2.5 - r * 7.0));
        float a = smoothstep(0.38, 1.0, r) * (0.55 + 0.45 * sin(Time * 7.0)) * (0.5 + 0.8 * streak) * Fx.x;
        over(vec3(0.95, 0.04, 0.08), a * 0.8);
    }

    if (Blood.x > 0.003) {
        // blood splattered over the goggles: blobs with ragged edges, and runs dripping down from them
        float cover = 0.0;
        for (int i = 0; i < 10; i++) {
            float fi = float(i) + Blood.y * 0.37;
            vec2 c = vec2(hash(vec2(fi, 1.7)), hash(vec2(fi, 9.3)));
            c = mix(c, step(0.5, c), 0.45);
            float rad = 0.05 + 0.11 * hash(vec2(fi, 4.1));
            vec2 d = (uv - c) * vec2(Aspect, 1.0);
            float edge = noise(d * 22.0 + fi * 3.0) * 0.5;
            cover = max(cover, (1.0 - smoothstep(rad * 0.55, rad, length(d) * (1.0 + edge))));
            float w = rad * (0.08 + 0.12 * hash(vec2(fi, 2.2)));
            float len = rad * (1.5 + 3.0 * hash(vec2(fi, 7.7)));
            float drip = step(abs(d.x), w) * step(0.0, d.y) * (1.0 - smoothstep(len * 0.6, len, d.y));
            cover = max(cover, drip);
        }
        over(vec3(0.42, 0.0, 0.02) * (0.7 + 0.3 * noise(uv * 40.0)), cover * 0.88 * Blood.x);
    }

    if (Warp.y > 0.001) {
        // phase dash: a cold shimmer creeping in from the edge of the view
        float sh = noise(uv * vec2(Aspect, 1.0) * 14.0 + vec2(Time * 3.0, -Time * 2.0));
        over(vec3(0.6, 0.92, 1.0), smoothstep(0.45, 1.1, r + (sh - 0.5) * 0.18) * 0.55 * Warp.y);
    }

    if (Warp.x > 0.001) {
        // hyperspace: star streaks racing out from the centre on their own lanes, the rim burning violet
        float a = atan(p.y, p.x) / 6.2831853 + 0.5;
        float lanes = 110.0;
        float lane = floor(a * lanes);
        float h = hash(vec2(lane, 7.0));
        float within = abs(fract(a * lanes) - 0.5) * 2.0;
        float head = fract(h * 5.3 + Time * (0.9 + 1.8 * h));
        float len = 0.12 + 0.3 * h;
        float rr = r * 1.35;
        float along = smoothstep(head - len, head, rr) * (1.0 - step(head, rr));
        float line = (1.0 - smoothstep(0.05, 0.45, within)) * along * step(0.45, h);
        over(mix(vec3(0.72, 0.45, 1.0), vec3(1.0), h), line * Warp.x * smoothstep(0.05, 0.3, r));
        over(vec3(0.3, 0.04, 0.5), smoothstep(0.3, 1.1, r) * 0.7 * Warp.x);
        over(vec3(1.0, 0.92, 1.0), (1.0 - smoothstep(0.0, 0.22, r)) * 0.35 * Warp.x * Warp.x);
    }

    fragColor = acc.a > 0.0001 ? vec4(acc.rgb, acc.a) : vec4(0.0);
}
