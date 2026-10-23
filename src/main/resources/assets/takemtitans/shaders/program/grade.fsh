#version 150

// Cinematic colour grade for the world (the HUD and hand are left untouched): a touch more saturation and
// contrast, cool shadows against warm highlights, and a soft vignette. Strength 0 is the untouched picture.

uniform sampler2D DiffuseSampler;
uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));

    vec3 g = mix(vec3(l), c, 1.1);
    g = (g - 0.5) * 1.06 + 0.5;

    float k = smoothstep(0.12, 0.85, l);
    g += mix(vec3(-0.012, 0.0, 0.02), vec3(0.022, 0.008, -0.018), k);

    vec2 d = (texCoord - 0.5) * vec2(1.0, 0.82);
    float vignette = 1.0 - smoothstep(0.32, 0.86, length(d));
    g *= mix(0.84, 1.0, vignette);

    fragColor = vec4(mix(c, clamp(g, 0.0, 1.0), Strength), 1.0);
}
