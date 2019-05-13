package unknowndomain.engine.client.rendering.model.voxel;

import java.util.List;

public class Model {

    private List<Mesh> meshes;

    public Model(List<Mesh> meshes) {
        this.meshes = meshes;
    }

    public List<Mesh> getMeshes() {
        return meshes;
    }

    public static class Mesh {
        public final byte[] data;
        public final boolean[] cullFaces;

        public Mesh(byte[] data, boolean[] cullFaces) {
            this.data = data;
            this.cullFaces = cullFaces;
        }
    }

}
