package core.mesh

import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes

class RawMeshData(
    val vertices: FloatArray,
    val indices: ShortArray,
) {
    fun createMeshData() : MeshData {
        val mesh = Mesh(true, vertices.size / 8, indices.size,
            VertexAttribute(VertexAttributes.Usage.Position, 3, "a_Position"),
            VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_Normal"),
            VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_TexCoord")
        )
        mesh.setVertices(vertices)
        mesh.setIndices(indices)

        return MeshData(
            mesh = mesh
        )
    }
}

class MeshData(
    val mesh: Mesh
)
