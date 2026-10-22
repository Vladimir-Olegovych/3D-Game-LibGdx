attribute vec4 a_Pos;      // xyz = позиция в чанке, w = направление грани 0..5 (+X -X +Y -Y +Z -Z)
attribute vec4 a_Tile;     // xy = локальные UV тайла, zw = число повторов тайла
attribute vec4 a_Shade;    // x = ao, y = свет неба, z = свет блоков (normalized 0..1)
attribute vec2 a_Origin;   // origin тайла в атласе (normalized)

uniform mat4 modelViewProjection;
uniform mat4 transform;
uniform vec3 viewPosition;
uniform float horizontalRadius;
uniform float verticalRadius;

varying vec4 v_TileA;      // xy = локальные UV, zw = повторы
varying vec4 v_TileB;      // xy = origin в атласе, z = sky, w = block
varying vec2 v_Misc;       // x = ao * dirLight, y = fog

void main() {
    vec4 worldPosition = transform * vec4(a_Pos.xyz, 1.0);

    // направленное затенение граней (раньше считалось в фрагментном шейдере по нормали)
    float d = a_Pos.w;
    float dirLight = d < 1.5 ? 0.65 : (d < 2.5 ? 1.0 : (d < 3.5 ? 0.5 : 0.8));

    vec3 diff = worldPosition.xyz - viewPosition;
    float ellipsoidDist = sqrt(
        (diff.x * diff.x) / (horizontalRadius * horizontalRadius) +
        (diff.y * diff.y) / (verticalRadius   * verticalRadius  ) +
        (diff.z * diff.z) / (horizontalRadius * horizontalRadius)
    );
    float fog = clamp((ellipsoidDist - 0.7) / (0.95 - 0.7), 0.0, 1.0);

    v_TileA = a_Tile;
    v_TileB = vec4(a_Origin, a_Shade.y, a_Shade.z);
    v_Misc  = vec2(a_Shade.x * dirLight, fog);

    gl_Position = modelViewProjection * worldPosition;
}
