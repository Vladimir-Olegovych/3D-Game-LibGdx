#ifdef GL_ES
precision mediump float;
#endif

uniform mat4 u_invViewProj;
uniform float u_nightFactor;
uniform float u_skyRotation;

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

    if (stars < 0.01) {
        discard;
    }

    vec3 color = mix(vec3(0.75, 0.82, 1.0), vec3(1.0), stars);
    gl_FragColor = vec4(color, stars * u_nightFactor);
}
