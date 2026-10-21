#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_TexCoord;
varying vec3 v_Normal;
varying vec3 v_WorldPos;
varying float v_FogFactor;
varying float v_AO;
varying vec2 v_Light;

uniform sampler2D u_texture;
uniform vec3 objectColor;
uniform vec3 fogColor;
uniform float u_dayPhase;
uniform float u_maxShadowThreshold;
uniform float u_nightBlueFilter;

float curve(float l) { return pow(l, 1.7); }

void main() {
    vec4 texSample = texture2D(u_texture, v_TexCoord);
    if (texSample.a <= 0.9) {
        discard;
    }

    vec3 texColor = texSample.rgb;
    vec3 albedo   = mix(objectColor, texColor, texSample.a);

    float blueAmount = (1.0 - u_dayPhase) * u_nightBlueFilter;
    vec3 nightTint = vec3(0.55, 0.65, 1.0);
    albedo = mix(albedo, albedo * nightTint, blueAmount);

    float ambient = 0.03;
    float ao      = v_AO;

    float nightFactor = mix(u_maxShadowThreshold, 1.0, u_dayPhase);
    float skyB   = curve(v_Light.x * nightFactor);   // небо зависит от времени суток
    float blockB = curve(v_Light.y);
    vec3 warm    = vec3(1.0, 0.82, 0.62);            // тёплый цвет факелов
    vec3 lightRGB = max(vec3(skyB), blockB * warm);

    float dirLight;
    if      (v_Normal.y >  0.5) dirLight = 1.00;
    else if (v_Normal.y < -0.5) dirLight = 0.50;
    else if (abs(v_Normal.z) > 0.5) dirLight = 0.80;
    else                        dirLight = 0.65;
                            // в пещере без света почти чёрно, но не абсолютно
    vec3 lit = ambient + (1.0 - ambient) * lightRGB * v_AO * dirLight;

    vec3 litColor   = albedo * clamp(lit, 0.0, 1.5);

    vec3 finalColor = mix(fogColor, litColor, 1.0 - v_FogFactor);

    float outAlpha = mix(1.0, texSample.a, texSample.a);
    gl_FragColor = vec4(finalColor, outAlpha);
}
