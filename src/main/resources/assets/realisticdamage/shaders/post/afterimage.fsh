#version 330

// The onion skin (Realistic Damage 1.6.1). History holds what this shader
// wrote last frame; the frame that was fades under the frame that is, so
// anything that moves leaves itself behind. Params.x is how much of the
// past survives into this frame, 0 (none: a clean copy) to just under 1.
// It is written by the mod every frame, frame-rate corrected, so a trail
// lasts the same fraction of a second at 30 fps as at 240.

uniform sampler2D InSampler;
uniform sampler2D HistorySampler;

layout(std140) uniform AfterimageConfig {
    vec4 Params;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 now = texture(InSampler, texCoord).rgb;
    vec3 was = texture(HistorySampler, texCoord).rgb;
    float keep = clamp(Params.x, 0.0, 0.98);
    // Plain crossfade for the body of the image, but let bright things linger
    // a little longer than dark ones: a ghost is what the eye keeps of the
    // highlights, and a straight average of two frames just reads as blur.
    vec3 faded = mix(now, was, keep);
    vec3 lingering = max(now, was * keep);
    fragColor = vec4(mix(faded, lingering, 0.35), 1.0);
}
