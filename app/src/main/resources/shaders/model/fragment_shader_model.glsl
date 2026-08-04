#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_TexCoord;
varying vec3 v_Normal;
varying vec3 v_WorldPos;

uniform sampler2D u_texture;
uniform vec3 objectColor;
uniform float u_useTexture;
uniform float u_dayPhase;
uniform float u_maxShadowThreshold;
uniform float u_nightBlueFilter;

void main() {
    vec4 texSample = texture2D(u_texture, v_TexCoord);
    if (u_useTexture > 0.0 && texSample.a <= 0.5) {
        discard;
    }

    vec3 texColor = texSample.rgb;
    vec3 albedo   = mix(objectColor, texColor, u_useTexture);

    float blueAmount = (1.0 - u_dayPhase) * u_nightBlueFilter;
    vec3 nightTint = vec3(0.55, 0.65, 1.0);
    albedo = mix(albedo, albedo * nightTint, blueAmount);

    float ambient = 0.05;
    float nightFactor = mix(u_maxShadowThreshold, 1.0, u_dayPhase);

    float dirLight;
    if      (v_Normal.y >  0.5) dirLight = 1.00;
    else if (v_Normal.y < -0.5) dirLight = 0.50;
    else if (abs(v_Normal.z) > 0.5) dirLight = 0.80;
    else                        dirLight = 0.65;

    float lit = ambient + (1.0 - ambient) * dirLight * nightFactor;

    vec3 litColor = albedo * clamp(lit, 0.0, 1.5);

    float outAlpha = mix(1.0, texSample.a, u_useTexture);
    gl_FragColor = vec4(litColor, outAlpha);
}
