package core.mesh

object MeshUtils {
    fun createBoxMeshData(): RawMeshData {
        val vertexPositions = arrayOf(
            floatArrayOf(-1f, -1f,  1f),
            floatArrayOf( 1f, -1f,  1f),
            floatArrayOf( 1f,  1f,  1f),
            floatArrayOf(-1f,  1f,  1f),
            floatArrayOf(-1f, -1f, -1f),
            floatArrayOf( 1f, -1f, -1f),
            floatArrayOf( 1f,  1f, -1f),
            floatArrayOf(-1f,  1f, -1f)
        )

        val normals = arrayOf(
            floatArrayOf( 0f,  0f,  1f),
            floatArrayOf( 0f,  0f, -1f),
            floatArrayOf(-1f,  0f,  0f),
            floatArrayOf( 1f,  0f,  0f),
            floatArrayOf( 0f,  1f,  0f),
            floatArrayOf( 0f, -1f,  0f)
        )

        val faceIndices = arrayOf(
            intArrayOf(0, 1, 2, 3),
            intArrayOf(5, 4, 7, 6),
            intArrayOf(4, 0, 3, 7),
            intArrayOf(1, 5, 6, 2),
            intArrayOf(3, 2, 6, 7),
            intArrayOf(0, 4, 5, 1)
        )

        val uvs = arrayOf(
            floatArrayOf(0f, 0f),
            floatArrayOf(1f, 0f),
            floatArrayOf(1f, 1f),
            floatArrayOf(0f, 1f)
        )

        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        for (face in 0 until 6) {
            val normal = normals[face]
            val faceVertexIndices = faceIndices[face]

            for (i in 0 until 4) {
                val vertexPos = vertexPositions[faceVertexIndices[i]]
                val uv = uvs[i]

                vertices.add(vertexPos[0])
                vertices.add(vertexPos[1])
                vertices.add(vertexPos[2])

                vertices.add(normal[0])
                vertices.add(normal[1])
                vertices.add(normal[2])

                vertices.add(uv[0])
                vertices.add(uv[1])
            }

            val baseIndex = (face * 4).toShort()
            indices.add((baseIndex + 0).toShort())
            indices.add((baseIndex + 1).toShort())
            indices.add((baseIndex + 2).toShort())
            indices.add((baseIndex + 2).toShort())
            indices.add((baseIndex + 3).toShort())
            indices.add((baseIndex + 0).toShort())
        }

        return RawMeshData(
            vertices = vertices.toFloatArray(),
            indices = indices.toShortArray()
        )
    }
}