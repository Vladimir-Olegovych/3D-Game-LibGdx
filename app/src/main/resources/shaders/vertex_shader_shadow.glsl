// ===== vertex_shadow.glsl =====
attribute vec3 a_Position;
uniform mat4 u_projViewWorldTrans;
uniform mat4 u_worldTrans;

void main() {
    gl_Position = u_projViewWorldTrans * u_worldTrans * vec4(a_Position, 1.0);
}