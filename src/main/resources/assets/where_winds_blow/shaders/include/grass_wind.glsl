vec3 wwb_apply_grass_wind(vec3 localPosition, vec3 chunkOffset, float vertexAlpha, float time, float strength, float speed, vec4 options) {
    float marker = floor((1.0 - vertexAlpha) * 255.0 + 0.5);
    float shortGrass = 1.0 - step(0.5, abs(marker - 1.0));
    float tallGrass = 1.0 - step(0.5, abs(marker - 2.0));
    float fern = 1.0 - step(0.5, abs(marker - 3.0));
    float flower = 1.0 - step(0.5, abs(marker - 4.0));
    float enabled = shortGrass * options.x + tallGrass * options.y + fern * options.z + flower * options.w;

    if (enabled <= 0.0 || strength <= 0.0 || speed <= 0.0) {
        return localPosition + chunkOffset;
    }

    vec3 worldPosition = localPosition + chunkOffset;
    float localHeight = fract(localPosition.y);
    float heightMask = smoothstep(0.08, 0.92, localHeight);
    float phase = worldPosition.x * 0.37 + worldPosition.z * 0.21 + time * 0.08 * speed;
    float gust = sin(phase) * 0.72 + sin(phase * 0.47 + worldPosition.y * 0.31) * 0.28;
    float offset = gust * strength * heightMask * enabled;

    worldPosition.x += offset;
    worldPosition.z += offset * 0.35;
    return worldPosition;
}
