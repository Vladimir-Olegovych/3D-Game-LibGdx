// ===== vertex_simple.glsl =====
attribute vec3 a_Position;
attribute vec3 a_Normal;
attribute vec2 a_TexCoord;

uniform mat4 modelViewProjection;
uniform mat4 transform;
uniform mat4 u_lightViewProjection;
uniform vec3 viewPosition;
uniform float horizontalRadius;
uniform float verticalRadius;

varying vec2 v_TexCoord;
varying vec3 v_Normal;
varying vec3 v_WorldPos;
varying float v_FogFactor;
varying vec4 v_ShadowCoord;

const mat4 BIAS_MATRIX = mat4(
    0.5, 0.0, 0.0, 0.0,
    0.0, 0.5, 0.0, 0.0,
    0.0, 0.0, 0.5, 0.0,
    0.5, 0.5, 0.5, 1.0
);

void main() {
    vec4 worldPosition = transform * vec4(a_Position, 1.0);
    v_WorldPos = worldPosition.xyz;

    v_Normal = normalize(mat3(transform) * a_Normal);
    v_TexCoord = a_TexCoord;

    v_ShadowCoord = BIAS_MATRIX * u_lightViewProjection * worldPosition;

    // Туман
    float fogStart = 0.7;
    float fogEnd   = 0.95; // fogEnd > fogStart !
    vec3 diff = worldPosition.xyz - viewPosition;
    float ellipsoidDist = sqrt(
        (diff.x * diff.x) / (horizontalRadius * horizontalRadius) +
        (diff.y * diff.y) / (verticalRadius   * verticalRadius  ) +
        (diff.z * diff.z) / (horizontalRadius * horizontalRadius)
    );
    v_FogFactor = clamp(
        (ellipsoidDist - fogStart) / (fogEnd - fogStart),
        0.0, 1.0
    );

    gl_Position = modelViewProjection * worldPosition;
}