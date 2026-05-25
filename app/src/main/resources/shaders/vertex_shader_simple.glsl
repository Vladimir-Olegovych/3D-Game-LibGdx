attribute vec3 a_Position;
attribute vec3 a_Normal;
attribute vec2 a_TexCoord;

uniform mat4 modelViewProjection;
uniform mat4 transform;
uniform mat4 normalMatrix;
uniform vec3 viewPosition;
uniform float cameraFar;

varying vec3 varNormal;
varying vec3 varViewPosition;
varying vec2 v_TexCoord;
varying float v_FogFactor;

void main() {
    vec4 worldPosition = transform * vec4(a_Position, 1.0);
    varViewPosition = worldPosition.xyz;

    float distance = length(viewPosition - worldPosition.xyz);

    float fogStart = cameraFar - 30;
    float fogEnd = cameraFar;

    v_FogFactor = clamp((fogEnd - distance) / (fogEnd - fogStart), 0.0, 1.0);

    varNormal = normalize((transform * vec4(a_Normal, 0.0)).xyz);
    v_TexCoord = a_TexCoord;
    gl_Position = modelViewProjection * worldPosition;
}