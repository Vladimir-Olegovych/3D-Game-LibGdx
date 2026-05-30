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
uniform float u_shadowMapSize;

float calculateShadow(vec4 shadowCoord, vec3 normal) {
    vec3 lightDir = normalize(-u_lightDirection);
    float NdotL   = dot(normal, lightDir);
    if (NdotL <= 0.0) {
        return 1.0 - u_shadowIntensity;
    }
    vec3 projCoords = shadowCoord.xyz / shadowCoord.w;
    if (projCoords.z >= 1.0 ||
        projCoords.x < 0.0 || projCoords.x > 1.0 ||
        projCoords.y < 0.0 || projCoords.y > 1.0) {
        return 1.0;
    }
    float currentDepth = projCoords.z;
    float bias = 0.0001;
    float shadowDepth = texture2D(u_shadowMap, projCoords.xy).r;
    float shadow = (currentDepth - bias > shadowDepth) ? 0.0 : 1.0;
    return mix(1.0, 1.0 - u_shadowIntensity, 1.0 - shadow);
}


void main() {
    vec3 texColor = texture2D(u_texture, v_TexCoord).rgb;
    vec3 albedo   = mix(objectColor, texColor, u_useTexture);
    vec3 normal   = normalize(v_Normal);
    vec3 lightDir = normalize(-u_lightDirection);

    float NdotL   = dot(normal, lightDir);
    float diffuse = max(NdotL, 0.0);
    float ambient = 0.25;

    float shadowFactor = calculateShadow(v_ShadowCoord, normal);
    float lit = ambient + diffuse * mix(1.0, shadowFactor, u_shadowIntensity);
    vec3 litColor = albedo * clamp(lit, 0.0, 1.5);

    vec3 finalColor = mix(fogColor, litColor, 1.0 - v_FogFactor);
    gl_FragColor = vec4(finalColor, 1.0);
}