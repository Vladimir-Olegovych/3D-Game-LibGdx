attribute vec4 a_Pos;
attribute vec4 a_Tile;
attribute vec4 a_Shade;
attribute vec2 a_Origin;

uniform mat4 modelViewProjection;
uniform mat4 transform;
uniform vec3 viewPosition;
uniform float horizontalRadius;
uniform float verticalRadius;

varying vec4 v_TileA;
varying vec4 v_TileB;
varying vec2 v_Misc;
varying vec2 v_Rel;

void main() {
    float w     = a_Pos.w;
    float shift = step(15.5, w);        // бит 16: сдвиг xz на 1/16 блока
    w -= 16.0 * shift;
    float fine  = step(7.5, w);         // бит 8: fine-модель
    float d     = w - 8.0 * fine;      // направление 0..5

    vec3 pos = a_Pos.xyz;
    pos.xz += 0.5 * shift;              // пол-шага сетки 1/8
    pos /= mix(1.0, 8.0, fine);
    vec2 loc = a_Tile.xy / mix(1.0, 256.0, fine);   // fine-UV в пикселях тайла

    vec4 worldPosition = transform * vec4(pos, 1.0);

    float dirLight = d < 1.5 ? 0.65 : (d < 2.5 ? 1.0 : (d < 3.5 ? 0.5 : 0.8));

    vec3 diff = worldPosition.xyz - viewPosition;
    float ellipsoidDist = sqrt(
        (diff.x * diff.x) / (horizontalRadius * horizontalRadius) +
        (diff.y * diff.y) / (verticalRadius   * verticalRadius  ) +
        (diff.z * diff.z) / (horizontalRadius * horizontalRadius)
    );
    float fog = clamp((ellipsoidDist - 0.7) / (0.95 - 0.7), 0.0, 1.0);

    v_Rel = diff.xz;
    v_TileA = vec4(loc, a_Tile.zw);
    v_TileB = vec4(a_Origin, a_Shade.y, a_Shade.z);
    v_Misc  = vec2(a_Shade.x * dirLight, fog);

    gl_Position = modelViewProjection * worldPosition;
}