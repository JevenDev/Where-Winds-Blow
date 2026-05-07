vec3 wwb_apply_grass_wind(vec3 localPosition, vec3 chunkOffset, float vertexAlpha, float time, float strength, float speed, vec4 options) {
    float marker = floor((1.0 - vertexAlpha) * 255.0 + 0.5);
    float type = floor(marker / 32.0);
    float encodedHeight = mod(marker, 32.0) / 31.0;
    float shortGrass = 1.0 - step(0.5, abs(type - 1.0));
    float tallGrass = 1.0 - step(0.5, abs(type - 2.0));
    float fern = 1.0 - step(0.5, abs(type - 3.0));
    float flower = 1.0 - step(0.5, abs(type - 4.0));
    float enabled = shortGrass * options.x + tallGrass * options.y + fern * options.z + flower * options.w;

    if (enabled <= 0.0 || strength <= 0.0 || speed <= 0.0) {
        return localPosition + chunkOffset;
    }

    vec3 worldPosition = localPosition + chunkOffset;
    float bladeHeight = clamp(encodedHeight, 0.0, 1.0);
    float bendMask = smoothstep(0.45, 1.0, bladeHeight);
    bendMask = bendMask * bendMask * bendMask;
    float tipMask = smoothstep(0.72, 1.0, bladeHeight);

    float phase = dot(worldPosition.xz, vec2(0.18, 0.14));
    float windTime = time * 0.08 * speed;
    float patchPhaseA = fract(sin(dot(floor(worldPosition.xz), vec2(12.9898, 78.233))) * 43758.5453) * 6.2831853;
    float patchPhaseB = fract(sin(dot(floor(worldPosition.xz * 0.5) + vec2(19.0, 7.0), vec2(39.3468, 11.135))) * 24634.6345) * 6.2831853;
    float gustEnvelope = 0.6 + 0.4 * sin(dot(worldPosition.xz, vec2(0.037, -0.041)) + time * 0.035 * speed + patchPhaseA);
    gustEnvelope *= gustEnvelope;
    float primaryWave = sin(phase + windTime + patchPhaseA * 0.35);
    float secondaryWave = sin(phase * 1.73 - windTime * 1.21 + worldPosition.y * 0.18 + patchPhaseB * 0.6);
    float tertiaryWave = sin(dot(worldPosition.xz, vec2(-0.11, 0.16)) + windTime * 0.63 + patchPhaseA * 0.7);
    float pushWave = max(0.0, sin(dot(worldPosition.xz, vec2(0.07, 0.05)) - windTime * 0.37 + patchPhaseB));
    float flutter = sin(phase * 2.41 + windTime * 1.84 + patchPhaseB);
    float gust = (primaryWave * 0.5 + secondaryWave * 0.3 + tertiaryWave * 0.2) * mix(0.75, 1.2, gustEnvelope) + pushWave * 0.18;
    vec2 windDirection = normalize(vec2(0.9 + sin(phase * 0.31) * 0.12, 0.35 + cos(phase * 0.27) * 0.08));
    float bendStrength = strength * enabled * bendMask;
    float flutterStrength = strength * 0.18 * enabled * tipMask;

    worldPosition.xz += windDirection * gust * bendStrength * bladeHeight;
    worldPosition.xz += vec2(-windDirection.y, windDirection.x) * flutter * flutterStrength;
    worldPosition.y -= abs(gust) * bendStrength * 0.08;
    return worldPosition;
}
