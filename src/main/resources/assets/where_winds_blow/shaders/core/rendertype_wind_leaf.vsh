#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float WindTime;
uniform float WeatherWindPower;
uniform vec2 WindDirection;
uniform vec3 CameraLeft;
uniform vec3 CameraUp;

out vec4 vertexColor;
out vec2 texCoord0;

float smoothCurve(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
}

void main() {
    float seed = clamp(float(UV2.x) / 255.0, 0.0, 1.0);
    float halfSize = max(0.025, clamp(float(UV2.y) / 255.0, 0.0, 1.0) * 0.18);
    float phase = seed * 6.2831853;
    float weatherBoost = 1.0 + WeatherWindPower * 0.35;
    float leafTime = WindTime + phase;
    vec2 windDir = normalize(WindDirection);
    vec2 crossDir = vec2(-windDir.y, windDir.x);
    float along = dot(Position.xz, windDir);
    float across = dot(Position.xz, crossDir);

    vec2 corner = vec2(UV0.x * 2.0 - 1.0, (1.0 - UV0.y) * 2.0 - 1.0);
    float flip = cos(leafTime * (2.0 + seed * 1.7) + sin(leafTime * 0.73 + across * 0.04) * 0.45);
    float flipScale = clamp(0.18 + abs(flip) * 0.82, 0.18, 1.0);
    float stretchScale = clamp(1.0 + sin(leafTime * (1.15 + seed) + phase * 0.37) * 0.13, 0.86, 1.14);
    float tilt = phase + leafTime * (0.52 + seed * 0.44)
            + sin(leafTime * 0.58 + along * 0.08) * 0.22
            + sin(leafTime * 1.34 + across * 0.05) * 0.16;
    float tiltCos = cos(tilt);
    float tiltSin = sin(tilt);
    vec2 local = vec2(
            corner.x * tiltCos - corner.y * tiltSin,
            corner.x * tiltSin + corner.y * tiltCos
    );
    local.x *= flipScale;
    local.y *= stretchScale;

    float flutter = (sin(leafTime * 1.45 + phase + across * 0.08)
            + sin(leafTime * 0.53 + phase * 1.6 + along * 0.06) * 0.38) * halfSize * 0.62 * weatherBoost;
    float bob = cos(leafTime * 1.17 + phase * 0.7) * halfSize * 0.48;
    float forwardPulse = cos(leafTime * 1.9 + phase) * halfSize * 0.12 * weatherBoost;

    vec3 pos = Position;
    pos += CameraLeft * (local.x * halfSize);
    pos += CameraUp * (local.y * halfSize);
    pos.xz += crossDir * flutter + windDir * forwardPulse;
    pos.y += bob;

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
}
