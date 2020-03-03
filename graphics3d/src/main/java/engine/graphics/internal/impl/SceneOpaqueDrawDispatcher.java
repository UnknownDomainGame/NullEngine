package engine.graphics.internal.impl;

import engine.graphics.Scene3D;
import engine.graphics.graph.DrawDispatcher;
import engine.graphics.graph.Frame;
import engine.graphics.graph.Renderer;
import engine.graphics.queue.StandardRenderTypes;
import engine.graphics.shader.ShaderResource;
import engine.graphics.viewport.Viewport;

public class SceneOpaqueDrawDispatcher implements DrawDispatcher {
    private final Viewport viewport;

    public SceneOpaqueDrawDispatcher(Viewport viewport) {
        this.viewport = viewport;
    }

    @Override
    public void init(ShaderResource resource) {

    }

    @Override
    public void draw(Frame frame, ShaderResource resource, Renderer renderer) {
        resource.setUniform("u_ProjMatrix", viewport.getProjectionMatrix());
        resource.setUniform("u_ViewMatrix", viewport.getViewMatrix());
        Scene3D scene = viewport.getScene();
        scene.doUpdate(frame.getTickLastFrame());
        scene.getRenderQueue().getGeometryList(StandardRenderTypes.OPAQUE).forEach(geometry -> {
            resource.setUniform("u_ModelMatrix", geometry.getWorldTransform().toTransformMatrix());
            geometry.getDrawable().draw();
        });
    }
}
