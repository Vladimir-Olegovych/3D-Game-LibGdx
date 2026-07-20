attribute vec3 a_Position;
attribute vec3 a_Normal;
attribute vec2 a_TexCoord;

uniform mat4 modelViewProjection;
uniform mat4 transform;

varying vec2 v_TexCoord;
varying vec3 v_Normal;
varying vec3 v_WorldPos;

void main() {
    vec4 worldPosition = transform * vec4(a_Position, 1.0);
    v_WorldPos = worldPosition.xyz;
    v_Normal   = normalize(mat3(transform) * a_Normal);
    v_TexCoord = a_TexCoord;

    gl_Position = modelViewProjection * worldPosition;
}