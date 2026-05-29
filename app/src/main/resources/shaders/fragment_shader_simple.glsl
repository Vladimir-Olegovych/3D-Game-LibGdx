varying vec3 varNormal;
varying vec3 varViewPosition;
varying vec2 v_TexCoord;
varying float v_FogFactor;

uniform sampler2D u_texture;
uniform vec3 lightDirection;
uniform vec3 lightColor;
uniform vec3 ambientLight;
uniform vec3 viewPosition;
uniform vec3 objectColor;
uniform vec3 fogColor;
uniform float u_useTexture;

void main() {
    vec3 normal = normalize(varNormal);
    vec3 lightDir = normalize(lightDirection);

    float diff = max(dot(normal, lightDir), 0.0);
    float bounceLight = 0.15 * (1.0 - diff) * (1.0 + dot(normal, vec3(0.0, 1.0, 0.0)));
    vec3 ambient = ambientLight * (0.7 + 0.3 * abs(normal.y));
    vec3 diffuseColor = objectColor * (diff * lightColor);

    vec3 viewDir = normalize(viewPosition - varViewPosition);
    vec3 reflectDir = reflect(-lightDir, normal);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
    vec3 specularColor = spec * lightColor * 0.5;

    vec3 result = ambient + diffuseColor + specularColor + (bounceLight * objectColor);
    result = pow(result, vec3(1.0/2.2));

    vec3 texColor = texture2D(u_texture, v_TexCoord).rgb;
    vec3 albedo = mix(objectColor, texColor, u_useTexture);
    result = result * albedo;

    vec3 finalColor = mix(fogColor, result, v_FogFactor);
    gl_FragColor = vec4(finalColor, 1.0);
}