// ===== fragment_simple.glsl =====
#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_TexCoord;
varying vec3 v_Normal;
varying vec3 v_WorldPos;
varying float v_FogFactor;
varying vec4 v_ShadowCoord;

uniform sampler2D u_texture;
uniform sampler2D u_shadowMap;
uniform vec3 objectColor;
uniform vec3 fogColor;
uniform float u_useTexture;
uniform vec3 u_lightDirection;
uniform float u_shadowIntensity;

float calculateShadow(vec4 shadowCoord, vec3 normal) {
    vec3 projCoords = shadowCoord.xyz / shadowCoord.w;
    if (projCoords.z >= 1.0 ||
        projCoords.x < 0.0 || projCoords.x > 1.0 ||
        projCoords.y < 0.0 || projCoords.y > 1.0) {
        return 1.0;
    }

    float shadowDepth  = texture2D(u_shadowMap, projCoords.xy).r;
    float currentDepth = projCoords.z;

    vec3 lightDir = normalize(-u_lightDirection);

    float cosTheta = clamp(dot(normal, lightDir), 0.0, 1.0);
    float bias = mix(0.005, 0.0005, cosTheta);

    return (currentDepth - bias > shadowDepth)
        ? (1.0 - u_shadowIntensity)
        : 1.0;
}

void main() {
    vec3 texColor = texture2D(u_texture, v_TexCoord).rgb;
    vec3 albedo   = mix(objectColor, texColor, u_useTexture);

    vec3 normal   = normalize(v_Normal);
    vec3 lightDir = normalize(-u_lightDirection);

    float diffuse = max(dot(normal, lightDir), 0.0);

    float ambient = 0.25;

    float shadow  = calculateShadow(v_ShadowCoord, normal);
    float lighting = ambient + diffuse * shadow;

    vec3 litColor   = albedo * clamp(lighting, 0.0, 1.5);
    vec3 finalColor = mix(fogColor, litColor, 1.0 - v_FogFactor);

    gl_FragColor = vec4(finalColor, 1.0);
}