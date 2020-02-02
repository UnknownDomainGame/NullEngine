package nullengine.client.rendering.gl;

import nullengine.client.rendering.gl.util.GLCleaner;
import nullengine.client.rendering.gl.util.GLHelper;
import nullengine.client.rendering.util.Cleaner;
import nullengine.client.rendering.util.DrawMode;
import nullengine.client.rendering.vertex.VertexDataBuf;
import nullengine.client.rendering.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public class SingleBufferVAO {

    private int id;
    private Cleaner.Disposable disposable;
    private GLVertexBuffer vbo;
    private VertexFormat vertexFormat;
    private GLDrawMode drawMode;

    private int vertexCount;

    public SingleBufferVAO() {
        this(GLBufferUsage.STATIC_DRAW, GLDrawMode.TRIANGLES);
    }

    public SingleBufferVAO(DrawMode drawMode) {
        this(GLBufferUsage.STATIC_DRAW, GLDrawMode.valueOf(drawMode));
    }

    public SingleBufferVAO(GLBufferUsage usage) {
        this(usage, GLDrawMode.TRIANGLES);
    }

    public SingleBufferVAO(GLBufferUsage usage, GLDrawMode drawMode) {
        this.vbo = new GLVertexBuffer(GLBufferType.ARRAY_BUFFER, usage);
        id = GL30.glGenVertexArrays();
        disposable = GLCleaner.registerVertexArray(this, id);
        this.drawMode = drawMode;
    }

    public GLVertexBuffer getVbo() {
        return vbo;
    }

    public VertexFormat getVertexFormat() {
        return vertexFormat;
    }

    public void setVertexFormat(VertexFormat vertexFormat) {
        this.vertexFormat = vertexFormat;
        bind();
        vbo.bind();
        GLHelper.enableVertexFormat(vertexFormat);
    }

    public DrawMode getDrawMode() {
        return drawMode.peer;
    }

    public void setDrawMode(DrawMode drawMode) {
        this.drawMode = GLDrawMode.valueOf(drawMode);
    }

    public void bind() {
        if (id == 0) {
            throw new IllegalStateException("Object has been disposed");
        }
        GL30.glBindVertexArray(id);
    }

    public void unbind() {
        GL30.glBindVertexArray(0);
    }

    public void uploadData(VertexDataBuf buffer) {
        uploadData(buffer.getBackingBuffer(), buffer.getVertexCount());
    }

    public void uploadData(ByteBuffer buffer) {
        uploadData(buffer, buffer.limit() / vertexFormat.getBytes());
    }

    public void uploadData(ByteBuffer buffer, int vertexCount) {
        vbo.uploadData(buffer);
        this.vertexCount = vertexCount;
    }

    public void draw() {
        bind();
        drawArrays();
    }

    public void drawArrays() {
        GL11.glDrawArrays(drawMode.gl, 0, this.vertexCount);
    }

    public void dispose() {
        if (id == 0) return;
        vbo.dispose();
        disposable.dispose();
        id = 0;
    }

    public boolean isDisposed() {
        return id == 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private GLBufferUsage bufferUsage = GLBufferUsage.STATIC_DRAW;
        private VertexFormat vertexFormat;
        private DrawMode drawMode = DrawMode.TRIANGLES;

        private Builder() {
        }

        public Builder bufferUsage(GLBufferUsage bufferUsage) {
            this.bufferUsage = bufferUsage;
            return this;
        }

        public Builder vertexFormat(VertexFormat vertexFormat) {
            this.vertexFormat = vertexFormat;
            return this;
        }

        public Builder drawMode(DrawMode drawMode) {
            this.drawMode = drawMode;
            return this;
        }

        public SingleBufferVAO build() {
            SingleBufferVAO singleBufferVAO = new SingleBufferVAO(bufferUsage);
            singleBufferVAO.setVertexFormat(vertexFormat);
            singleBufferVAO.setDrawMode(drawMode);
            return singleBufferVAO;
        }

        public SingleBufferVAO build(VertexDataBuf buffer) {
            SingleBufferVAO singleBufferVAO = new SingleBufferVAO(bufferUsage);
            singleBufferVAO.setVertexFormat(vertexFormat);
            singleBufferVAO.setDrawMode(drawMode);
            singleBufferVAO.uploadData(buffer);
            return singleBufferVAO;
        }
    }
}
