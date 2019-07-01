package nullengine.client.gui.rendering;

import nullengine.client.gui.component.ItemView;
import nullengine.client.rendering.RenderContext;
import nullengine.client.rendering.item.ItemRenderManager;
import nullengine.client.rendering.shader.ShaderManager;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

public class ItemViewRenderer implements ComponentRenderer<ItemView> {

    public static final ItemViewRenderer INSTANCE = new ItemViewRenderer();

    @Override
    public void render(ItemView component, Graphics graphics, RenderContext context) {
        Optional<ItemRenderManager> optionalItemRenderManager = context.getComponent(ItemRenderManager.class);
        if (optionalItemRenderManager.isPresent()) {

            ShaderManager.setUniform("u_ModelMatrix", new Matrix4f().translationRotateScale(new Vector3f
                (component.viewSize().get() * 0.08f, component.viewSize().get() * 0.75f, 0),
                new Quaternionf(new AxisAngle4f(3.141592625f, 1, 0, 0)).
                    rotateAxis((float) (Math.PI / 4), 0, 1, 0).rotateAxis((float) Math.PI / 6f,
                    (float) Math.cos(Math.PI / 4), 0, (float) Math.cos(Math.PI / 4)), component.viewSize().get() * 0.6f));
            optionalItemRenderManager.get().render(component.item().getValue(), 0);
            ShaderManager.setUniform("u_ModelMatrix", new Matrix4f());
        }
    }
}
