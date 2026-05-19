attribute vec3 a_position;
uniform mat4 u_lightViewProj;
uniform mat4 u_transform;   // матрица модели (позиция чанка)

void main() {
    gl_Position = u_lightViewProj * u_transform * vec4(a_position, 1.0);
}