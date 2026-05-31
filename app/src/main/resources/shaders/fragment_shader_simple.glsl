#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_TexCoord;
varying vec3 v_Normal;
varying vec3 v_WorldPos;
varying float v_FogFactor;
varying float v_AO;
varying float v_SkyLight;

uniform sampler2D u_texture;
uniform vec3 objectColor;
uniform vec3 fogColor;
uniform float u_useTexture;
uniform float u_modelAO;

void main() {
    vec3 texColor = texture2D(u_texture, v_TexCoord).rgb;
    vec3 albedo   = mix(objectColor, texColor, u_useTexture);

    float ambient  = 0.05;
    float skyLight = v_SkyLight;
    float ao       = v_AO * u_modelAO;

    // Directional light только если есть skylight
    float dirLight;
    if      (v_Normal.y >  0.5) dirLight = 1.00;
    else if (v_Normal.y < -0.5) dirLight = 0.50;
    else if (abs(v_Normal.z) > 0.5) dirLight = 0.80;
    else                        dirLight = 0.65;

    // Итоговое освещение:
    // - под землёй: только ambient (темно)
    // - на воздухе: ambient + skylight * ao * dirlight
    float lit = ambient + (1.0 - ambient) * skyLight * ao * dirLight;

    vec3 litColor   = albedo * clamp(lit, 0.0, 1.5);
    vec3 finalColor = mix(fogColor, litColor, 1.0 - v_FogFactor);

    gl_FragColor = vec4(finalColor, 1.0);
}