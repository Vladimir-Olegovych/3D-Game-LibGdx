#ifdef GL_ES
precision mediump float;
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

void main() {
    vec4 texSample = texture2D(u_texture, v_TexCoord);
    if (texSample.a <= 0.9) {
        discard;
    }

    vec3 texColor = texSample.rgb;
    vec3 albedo   = mix(objectColor, texColor, texSample.a);

    float ambient = 0.05;
    float shadow  = v_Shadow;
    float ao      = v_AO;

    float dirLight;
    if      (v_Normal.y >  0.5) dirLight = 1.00;
    else if (v_Normal.y < -0.5) dirLight = 0.50;
    else if (abs(v_Normal.z) > 0.5) dirLight = 0.80;
    else                        dirLight = 0.65;

    float lit = ambient + (1.0 - ambient) * shadow * ao * dirLight;

    vec3 litColor   = albedo * clamp(lit, 0.0, 1.5);
    vec3 finalColor = mix(fogColor, litColor, 1.0 - v_FogFactor);

    float outAlpha = mix(1.0, texSample.a, texSample.a);
    gl_FragColor = vec4(finalColor, outAlpha);
}
