#ifdef GL_ES
precision mediump float;
#endif

uniform vec2  u_sunScreenPos;   // позиция солнца в NDC [-1,1]
uniform float u_radius;         // радиус круга в NDC-пространстве
uniform float u_aspectRatio;    // width / height экрана
uniform vec4  u_sunColor;       // цвет солнца

varying vec2 v_uv;

void main() {
    vec2 ndc = v_uv * 2.0 - 1.0;

    vec2 diff = ndc - u_sunScreenPos;
    diff.x *= u_aspectRatio;

    float dist = length(diff);

    float alpha = 1.0 - smoothstep(u_radius - 0.005, u_radius, dist);

    if (alpha < 0.001) discard;

    float glow = 1.0 - smoothstep(u_radius, u_radius * 2.5, dist);
    vec4 glowColor = vec4(u_sunColor.rgb, glow * 0.25);

    gl_FragColor = mix(glowColor, u_sunColor, alpha);
}