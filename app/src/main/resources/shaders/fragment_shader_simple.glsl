varying vec3 varNormal;
varying vec3 varViewPosition;
varying vec2 v_TexCoord;

uniform sampler2D u_texture;
uniform vec3 lightDirection;      // in world space
uniform vec3 lightColor;
uniform vec3 ambientLight;
uniform vec3 viewPosition;        // camera position in world space
uniform vec3 objectColor;         // optional base color (multiplied by texture)

void main() {
    vec3 normal = normalize(varNormal);
    vec3 lightDir = normalize(lightDirection);

    // Diffuse lighting
    float diff = max(dot(normal, lightDir), 0.0);

    // Simple bounce light for dark areas
    float bounceLight = 0.15 * (1.0 - diff) * (1.0 + dot(normal, vec3(0.0, 1.0, 0.0)));

    // Ambient with variation based on normal Y
    vec3 ambient = ambientLight * (0.7 + 0.3 * abs(normal.y));

    // Diffuse color
    vec3 diffuseColor = objectColor * (diff * lightColor);

    // Specular (blinn-phong style)
    vec3 viewDir = normalize(viewPosition - varViewPosition);
    vec3 reflectDir = reflect(-lightDir, normal);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
    vec3 specularColor = spec * lightColor * 0.5;

    // Final lighting
    vec3 result = ambient + diffuseColor + specularColor + (bounceLight * objectColor);

    // Gamma correction
    result = pow(result, vec3(1.0/2.2));

    // Apply texture
    vec3 texColor = texture2D(u_texture, v_TexCoord).rgb;
    result = result * texColor;

    gl_FragColor = vec4(result, 1.0);
}