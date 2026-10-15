#version 330

// The lens, for VR (Realistic Damage 1.7 alpha.10). In flat play the screen
// effects are HUD draws; in VR the HUD is a panel floating in the world, so
// they are recorded instead (client/Lens) and replayed here onto each eye, in
// the order the HUD would have drawn them. Coordinates are the HUD's: 0..1
// across, 0..1 down from the top.
//
// Ops[i*4 + 0] rect: x0 y0 x1 y1
// Ops[i*4 + 1] colour A (fill: top colour; vignette: tint; sprite: modulate)
// Ops[i*4 + 2] colour B (fill: bottom colour)
// Ops[i*4 + 3] mode, then a sprite's atlas cell: u0 v0 size
//   mode 0: alpha-blended fill, A at the top fading to B at the bottom
//   mode 1: vanilla's VIGNETTE blend, dst *= 1 - texture * tint
//   mode 2: alpha-blended sprite from the lens atlas, kept round on any eye
//
// Header: op count, blur 0..1, aspect of the HUD the ops were drawn on.

#define MAX_OPS 48

uniform sampler2D InSampler;
uniform sampler2D VignetteSampler;
uniform sampler2D LensSampler;

layout(std140) uniform LensConfig {
    vec4 Header;
    vec4 Ops[MAX_OPS * 4];
};

in vec2 texCoord;

out vec4 fragColor;

const float GOLDEN_ANGLE = 2.39996323;

// Stands in for vanilla's menu blur, which in VR would only blur the HUD panel.
vec3 scene(vec2 uv, float blur, float aspect) {
    vec3 centre = texture(InSampler, uv).rgb;
    if (blur <= 0.0) {
        return centre;
    }
    vec2 radius = vec2(0.011 / aspect, 0.011) * blur;
    vec3 sum = centre;
    for (int i = 1; i < 16; i++) {
        float r = sqrt(float(i) / 15.0);
        float a = float(i) * GOLDEN_ANGLE;
        sum += texture(InSampler, uv + vec2(cos(a), sin(a)) * r * radius).rgb;
    }
    return sum / 16.0;
}

void main() {
    vec2 size = vec2(textureSize(InSampler, 0));
    float eyeAspect = size.x / size.y;
    vec2 p = vec2(texCoord.x, 1.0 - texCoord.y);
    vec3 colour = scene(texCoord, Header.y, eyeAspect);

    int count = min(int(Header.x + 0.5), MAX_OPS);
    for (int i = 0; i < count; i++) {
        vec4 rect = Ops[i * 4];
        vec4 a = Ops[i * 4 + 1];
        vec4 b = Ops[i * 4 + 2];
        vec4 m = Ops[i * 4 + 3];
        int mode = int(m.x + 0.5);
        if (mode == 2) {
            // A drop drawn square on a 16:9 HUD stays round on a near-square eye.
            vec2 centre = (rect.xy + rect.zw) * 0.5;
            vec2 halfSize = (rect.zw - rect.xy) * 0.5;
            halfSize.x *= Header.z / eyeAspect;
            rect = vec4(centre - halfSize, centre + halfSize);
        }
        if (p.x < rect.x || p.y < rect.y || p.x >= rect.z || p.y >= rect.w) {
            continue;
        }
        vec2 local = (p - rect.xy) / max(rect.zw - rect.xy, vec2(1.0e-6));
        if (mode == 1) {
            colour *= 1.0 - texture(VignetteSampler, local).rgb * a.rgb;
        } else if (mode == 2) {
            vec4 drop = texture(LensSampler, m.yz + local * m.w) * a;
            colour = mix(colour, drop.rgb, drop.a);
        } else {
            vec4 fill = mix(a, b, local.y);
            colour = mix(colour, fill.rgb, fill.a);
        }
    }
    fragColor = vec4(colour, 1.0);
}
