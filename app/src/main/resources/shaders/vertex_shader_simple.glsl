attribute vec3 a_Position;
attribute vec3 a_Normal;
attribute vec2 a_TexCoord;

attribute float a_AO;
attribute float a_SkyLight;

uniform mat4 modelViewProjection;
uniform mat4 transform;
uniform vec3 viewPosition;
uniform float horizontalRadius;
uniform float verticalRadius;

varying vec2 v_TexCoord;
varying vec3 v_Normal;
varying vec3 v_WorldPos;
varying float v_FogFactor;

varying float v_AO;
varying float v_SkyLight;

void main() {
    vec4 worldPosition = transform * vec4(a_Position, 1.0);
    v_WorldPos = worldPosition.xyz;
    v_Normal   = normalize(mat3(transform) * a_Normal);
    v_TexCoord = a_TexCoord;
    v_AO       = a_AO;
    v_SkyLight = a_SkyLight;

    float fogStart = 0.7;
    float fogEnd   = 0.95;
    vec3  diff     = worldPosition.xyz - viewPosition;
    float ellipsoidDist = sqrt(
        (diff.x * diff.x) / (horizontalRadius * horizontalRadius) +
        (diff.y * diff.y) / (verticalRadius   * verticalRadius  ) +
        (diff.z * diff.z) / (horizontalRadius * horizontalRadius)
    );
    v_FogFactor = clamp((ellipsoidDist - fogStart) / (fogEnd - fogStart), 0.0, 1.0);

    gl_Position = modelViewProjection * worldPosition;
}