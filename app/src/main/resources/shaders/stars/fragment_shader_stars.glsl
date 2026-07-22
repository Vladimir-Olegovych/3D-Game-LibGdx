#ifdef GL_ES
precision mediump float;
#endif

uniform mat4 u_invViewProj;
uniform float u_nightFactor;
uniform float u_skyRotation;
uniform float u_time;

varying vec2 v_uv;

vec3 rotateAroundWorldX(vec3 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float starCell(vec2 cellId, vec2 local) {
    float h = hash21(cellId);
    if (h > 0.86) {
        vec2 starPos = vec2(hash21(cellId + 1.3), hash21(cellId + 2.7));
        float dist = length(local - starPos);
        float brightness = hash21(cellId + 3.1);
        float size = mix(0.032, 0.144, brightness);
        return smoothstep(size, 0.0, dist) * mix(0.35, 1.0, brightness);
    }
    return 0.0;
}

float starLayer(vec2 uv, float scale) {
    vec2 gv = uv * scale;
    vec2 id = floor(gv);
    vec2 local = fract(gv);

    float stars = 0.0;
    stars += starCell(id, local);
    stars += starCell(id + vec2(1.0, 0.0), local - vec2(1.0, 0.0));
    stars += starCell(id + vec2(0.0, 1.0), local - vec2(0.0, 1.0));
    stars += starCell(id + vec2(1.0, 1.0), local - vec2(1.0, 1.0));
    stars += starCell(id + vec2(-1.0, 0.0), local - vec2(-1.0, 0.0));
    stars += starCell(id + vec2(0.0, -1.0), local - vec2(0.0, -1.0));
    stars += starCell(id + vec2(-1.0, -1.0), local - vec2(-1.0, -1.0));
    stars += starCell(id + vec2(-1.0, 1.0), local - vec2(-1.0, 1.0));
    stars += starCell(id + vec2(1.0, -1.0), local - vec2(1.0, -1.0));
    return stars;
}

vec2 rayToCubeUV(vec3 dir) {
    vec3 ad = abs(dir);
    vec2 uv;
    if (ad.x >= ad.y && ad.x >= ad.z) {
        uv = dir.yz / ad.x;
    } else if (ad.y >= ad.z) {
        uv = dir.xz / ad.y;
    } else {
        uv = dir.xy / ad.z;
    }
    return uv * 0.5 + 0.5;
}

float meteorStreak(vec2 uv, float slot, float time) {
    float interval = 4.0 + slot * 1.7;
    float duration = 0.45 + hash21(vec2(slot, 9.1)) * 0.35;
    float window = floor(time / interval);
    float localT = fract(time / interval) * interval;

    float spawnChance = hash21(vec2(window, slot * 13.7));
    if (spawnChance > 0.35) {
        return 0.0;
    }
    if (localT > duration) {
        return 0.0;
    }

    float progress = localT / duration;
    vec2 seed = vec2(window + slot * 17.0, slot * 3.1);

    vec2 start = vec2(
        hash21(seed + 1.1),
        mix(0.35, 0.95, hash21(seed + 2.2))
    );
    float angle = mix(-0.9, -0.35, hash21(seed + 3.3));
    float lengthTrail = mix(0.12, 0.28, hash21(seed + 4.4));
    vec2 dir = vec2(cos(angle), sin(angle));

    vec2 head = start + dir * (progress * lengthTrail);
    vec2 toPixel = uv - head;
    float along = -dot(toPixel, dir);
    float across = abs(dot(toPixel, vec2(-dir.y, dir.x)));

    float trailLen = lengthTrail * mix(0.35, 1.0, progress);
    float alongMask = step(0.0, along) * (1.0 - smoothstep(0.0, trailLen, along));
    float width = mix(0.0025, 0.0012, along / max(trailLen, 0.001));
    float acrossMask = 1.0 - smoothstep(width, width * 2.5, across);

    float headGlow = 1.0 - smoothstep(0.0, 0.012, length(toPixel));
    float fade = sin(progress * 3.14159265);
    float brightness = mix(0.7, 1.0, hash21(seed + 5.5));

    return (alongMask * acrossMask + headGlow * 0.85) * fade * brightness;
}

float meteors(vec2 uv, float time) {
    float result = 0.0;
    result += meteorStreak(uv, 0.0, time);
    result += meteorStreak(uv, 1.0, time + 1.3);
    result += meteorStreak(uv, 2.0, time + 2.7);
    result += meteorStreak(uv, 3.0, time + 4.1);
    result += meteorStreak(uv, 4.0, time + 5.9);
    result += meteorStreak(uv, 5.0, time + 7.2);
    return min(result, 1.5);
}

void main() {
    if (u_nightFactor < 0.001) {
        discard;
    }

    vec2 ndc = v_uv * 2.0 - 1.0;
    vec4 nearPoint = u_invViewProj * vec4(ndc, -1.0, 1.0);
    vec4 farPoint  = u_invViewProj * vec4(ndc,  1.0, 1.0);
    vec3 ray = normalize(farPoint.xyz / farPoint.w - nearPoint.xyz / nearPoint.w);
    vec3 skyRay = rotateAroundWorldX(ray, -u_skyRotation);

    vec2 skyUv = rayToCubeUV(skyRay);

    float stars = 0.0;
    stars += starLayer(skyUv, 100.0);
    stars += starLayer(skyUv + 0.17, 180.0);
    stars += starLayer(skyUv + 0.43, 320.0);
    stars += starLayer(skyUv + 0.71, 520.0);
    stars = min(stars, 1.0);

    float meteor = meteors(skyUv, u_time);
    float intensity = max(stars, meteor);

    if (intensity < 0.01) {
        discard;
    }

    vec3 starColor = mix(vec3(0.75, 0.82, 1.0), vec3(1.0), stars);
    vec3 meteorColor = mix(vec3(0.85, 0.92, 1.0), vec3(1.0, 0.98, 0.9), clamp(meteor, 0.0, 1.0));
    vec3 color = mix(starColor, meteorColor, clamp(meteor, 0.0, 1.0));

    gl_FragColor = vec4(color, clamp(intensity, 0.0, 1.0) * u_nightFactor);
}
