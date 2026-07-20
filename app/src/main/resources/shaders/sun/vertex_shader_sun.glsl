attribute vec2 a_position;
attribute vec2 a_texCoord0;

varying vec2 v_uv;

void main() {
    v_uv = a_texCoord0;
    // w = z означает что после деления NDC z = 1.0 (на far plane)
    // солнце будет перекрываться любой геометрией
    gl_Position = vec4(a_position.xy, 1.0, 1.0);
}