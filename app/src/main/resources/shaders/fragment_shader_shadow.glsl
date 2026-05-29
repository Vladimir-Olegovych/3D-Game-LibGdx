// ===== fragment_shadow.glsl =====
#ifdef GL_ES
precision mediump float;
#endif

void main() {
    gl_FragColor = vec4(gl_FragCoord.z, gl_FragCoord.z, gl_FragCoord.z, 1.0);
}