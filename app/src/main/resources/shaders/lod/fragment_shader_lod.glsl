#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

varying vec3 v_normal;
varying vec3 v_color;
varying float v_fog;
varying vec2 v_rel;

uniform float u_fadeStart;
uniform float u_fadeEnd;
uniform vec3 fogColor;
uniform float u_dayPhase;
uniform float u_maxShadowThreshold;
uniform float u_nightBlueFilter;

void main() {
    // dither-растворение к границе уровня: сквозь него виден более грубый уровень
    float cheb = max(abs(v_rel.x), abs(v_rel.y));
    float fade = clamp((cheb - u_fadeStart) / (u_fadeEnd - u_fadeStart), 0.0, 1.0);
    if (fade > 0.0) {
        float ign = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
        if (fade > ign) discard;
    }

    if (v_fog >= 1.0) {
        gl_FragColor = vec4(fogColor, 1.0);
        return;
    }

    float ndl = max(dot(normalize(v_normal), normalize(vec3(0.35, 0.85, 0.4))), 0.0);
    float dirLight = 0.6 + 0.4 * ndl;

    vec3 albedo = v_color;
    float blueAmount = (1.0 - u_dayPhase) * u_nightBlueFilter;
    albedo = mix(albedo, albedo * vec3(0.55, 0.65, 1.0), blueAmount);

    float nightFactor = mix(u_maxShadowThreshold, 1.0, u_dayPhase);
    float sky = pow(nightFactor, 1.7);
    vec3 lit = vec3(0.05 + 0.95 * sky * dirLight);

    gl_FragColor = vec4(mix(fogColor, albedo * lit, 1.0 - v_fog), 1.0);
}