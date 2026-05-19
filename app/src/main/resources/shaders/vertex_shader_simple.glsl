attribute vec3 a_Position;
attribute vec3 a_Normal;
attribute vec2 a_TexCoord;      // добавлено

uniform mat4 modelViewProjection;
uniform mat4 transform;          // world matrix
uniform mat4 normalMatrix;       // inverse transpose of transform (for normals)
uniform vec3 viewPosition;       // camera position in world space

varying vec3 varNormal;          // normal in world space
varying vec3 varViewPosition;    // fragment position in world space
varying vec2 v_TexCoord;         // texture coordinates

void main() {
    vec4 worldPosition = transform * vec4(a_Position, 1.0);
    varViewPosition = worldPosition.xyz;

    // Transform normal to world space
    varNormal = normalize((transform * vec4(a_Normal, 0.0)).xyz);

    v_TexCoord = a_TexCoord;

    gl_Position = modelViewProjection * worldPosition;
}