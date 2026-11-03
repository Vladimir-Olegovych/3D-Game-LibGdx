attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec4 a_color;

uniform mat4 modelViewProjection;
uniform vec3 u_origin;
uniform float u_bias;
uniform vec3 viewPosition;
uniform float u_fogRadius;

varying vec3 v_normal;
varying vec3 v_color;
varying float v_fog;
varying vec2 v_rel;

void main() {
    vec3 world = a_position + u_origin;
    world.y -= u_bias;

    // та же функция тумана, что и в chunk-шейдере (при одинаковых радиусах)
    float d = length(world - viewPosition) / u_fogRadius;
    v_fog = clamp((d - 0.7) / (0.95 - 0.7), 0.0, 1.0);

    v_rel = world.xz - viewPosition.xz;
    v_normal = a_normal;
    v_color = a_color.rgb;
    gl_Position = modelViewProjection * vec4(world, 1.0);
}