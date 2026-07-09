#ifdef GL_ES
precision mediump int;
#endif

varying vec2 v_TexCoord;
varying vec3 v_Normal;
varying vec3 v_WorldPos;
varying float v_FogFactor;
varying float v_AO;
varying float v_Shadow;

uniform sampler2D u_texture;
uniform vec3 objectColor;
uniform vec3 fogColor;
uniform float u_useTexture;
uniform float u_useAO;
uniform float u_useShadow;

void main() {
    vec4 texSample = texture2D(u_texture, v_TexCoord);
    if (u_useTexture > 0.0 && texSample.a <= 0.5) {
        discard;
    }

    vec3 texColor = texSample.rgb;
    vec3 albedo   = mix(objectColor, texColor, u_useTexture);

    float ambient = 0.05;
    float shadow  = 1.0;
    float ao      = 1.0;

    if (u_useAO > 0.0)     { ao     = v_AO; }
    if (u_useShadow > 0.0) { shadow = v_Shadow; }

    float dirLight;
    if      (v_Normal.y >  0.5) dirLight = 1.00;
    else if (v_Normal.y < -0.5) dirLight = 0.50;
    else if (abs(v_Normal.z) > 0.5) dirLight = 0.80;
    else                        dirLight = 0.65;

    float lit = ambient + (1.0 - ambient) * shadow * ao * dirLight;

    vec3 litColor   = albedo * clamp(lit, 0.0, 1.5);
    vec3 finalColor = mix(fogColor, litColor, 1.0 - v_FogFactor);

    float outAlpha = mix(1.0, texSample.a, u_useTexture);
    gl_FragColor = vec4(finalColor, outAlpha);
}