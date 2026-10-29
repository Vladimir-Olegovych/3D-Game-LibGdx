#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

varying vec4 v_TileA;
varying vec4 v_TileB;
varying vec2 v_Misc;

uniform sampler2D u_texture;
uniform vec2 u_tileSize;
uniform vec3 fogColor;
uniform float u_dayPhase;
uniform float u_maxShadowThreshold;
uniform float u_nightBlueFilter;

float curve(float l) { return pow(l, 1.7); }

void main() {
    float fog = v_Misc.y;

    #ifndef CUTOUT
        if (fog >= 1.0) {
            gl_FragColor = vec4(fogColor, 1.0);
            return;
        }
    #endif

    // повтор тайла в атласе; clamp защищает края квада от перескока в соседний тайл
    vec2 local = clamp(v_TileA.xy, vec2(0.0), v_TileA.zw - vec2(0.0005));
    vec2 uv = v_TileB.xy + fract(local) * u_tileSize;
    vec4 texSample = texture2D(u_texture, uv);

    #ifdef CUTOUT
        if (texSample.a <= 0.9) discard;
    #endif

    vec3 albedo = texSample.rgb;
    float blueAmount = (1.0 - u_dayPhase) * u_nightBlueFilter;
    albedo = mix(albedo, albedo * vec3(0.55, 0.65, 1.0), blueAmount);

    float nightFactor = mix(u_maxShadowThreshold, 1.0, u_dayPhase);
    float skyB   = curve(v_TileB.z * nightFactor);
    float blockB = curve(v_TileB.w) * 1.6;
    vec3 lightRGB = max(vec3(skyB), blockB * vec3(1.0, 0.82, 0.45));

    vec3 lit = 0.05 + 0.95 * lightRGB * v_Misc.x;
    vec3 litColor = albedo * clamp(lit, 0.0, 1.5);

    gl_FragColor = vec4(mix(fogColor, litColor, 1.0 - fog), 1.0);
}
