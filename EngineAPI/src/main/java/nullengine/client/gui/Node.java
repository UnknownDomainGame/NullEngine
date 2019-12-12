package nullengine.client.gui;

import com.github.mouse0w0.observable.collection.ObservableCollections;
import com.github.mouse0w0.observable.collection.ObservableMap;
import com.github.mouse0w0.observable.value.*;
import nullengine.client.gui.event.*;
import nullengine.client.gui.input.KeyEvent;
import nullengine.client.gui.input.MouseActionEvent;
import nullengine.client.gui.input.MouseButton;
import nullengine.client.gui.input.MouseEvent;
import nullengine.client.gui.rendering.ComponentRenderer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;

public abstract class Node implements EventTarget {

    final MutableObjectValue<Scene> scene = new SimpleMutableObjectValue<>();
    final MutableObjectValue<Parent> parent = new SimpleMutableObjectValue<>();

    private final MutableFloatValue x = new SimpleMutableFloatValue();
    private final MutableFloatValue y = new SimpleMutableFloatValue();

    public static final float FOLLOW_PARENT = -1;
    final MutableFloatValue width = new SimpleMutableFloatValue();
    final MutableFloatValue height = new SimpleMutableFloatValue();

    protected final MutableBooleanValue visible = new SimpleMutableBooleanValue(true);
    protected final MutableBooleanValue disabled = new SimpleMutableBooleanValue(false);

    protected final MutableBooleanValue focused = new SimpleMutableBooleanValue(false);
    protected final MutableBooleanValue hover = new SimpleMutableBooleanValue(false);
    protected final MutableBooleanValue pressed = new SimpleMutableBooleanValue(false);

    private ComponentRenderer renderer;

    private EventHandlerManager eventHandlerManager = new EventHandlerManager();

    public EventHandlerManager getEventHandlerManager() {
        return eventHandlerManager;
    }

    public Node() {
        visible.addChangeListener((observable, oldValue, newValue) -> requestParentLayout());
        this.addEventHandler(MouseEvent.MOUSE_EXITED, (e) -> {
            if (!disabled.get()) {
                hover.set(false);
                pressed.set(false);
            }
        });
        this.addEventHandler(MouseEvent.MOUSE_ENTERED, (e) -> {
            if (!disabled.get()) {
                hover.set(true);
            }
        });
        this.addEventHandler(MouseActionEvent.MOUSE_PRESSED, (e) -> {
            if (!disabled.get() && e.getButton() == MouseButton.MOUSE_BUTTON_PRIMARY) {
                pressed.set(true);
            }
        });
        this.addEventHandler(MouseActionEvent.MOUSE_RELEASED, (e) -> {
            if (!disabled.get() && e.getButton() == MouseButton.MOUSE_BUTTON_PRIMARY) {
                pressed.set(false);

            }
        });
    }

    public ObservableObjectValue<Scene> scene() {
        return scene;
    }

    public final ObservableObjectValue<Parent> parent() {
        return parent.toUnmodifiable();
    }

    public final MutableFloatValue x() {
        return x;
    }

    public final MutableFloatValue y() {
        return y;
    }

    public final ObservableFloatValue width() {
        return width.toUnmodifiable();
    }

    public final ObservableFloatValue height() {
        return height.toUnmodifiable();
    }

    public final MutableBooleanValue visible() {
        return visible;
    }

    public final MutableBooleanValue disabled() {
        return disabled;
    }

    public final ObservableBooleanValue focused() {
        return focused.toUnmodifiable();
    }

    public final ObservableBooleanValue hover() {
        return hover.toUnmodifiable();
    }

    public final ObservableBooleanValue pressed() {
        return pressed.toUnmodifiable();
    }

    public final void requestParentLayout() {
        Parent parent = parent().getValue();
        if (parent != null && !parent.isNeedsLayout()) {
            parent.needsLayout();
        }
    }

    public float minWidth() {
        return prefWidth();
    }

    public float minHeight() {
        return prefHeight();
    }

    abstract public float prefWidth();

    abstract public float prefHeight();

    public float maxWidth() {
        return prefWidth();
    }

    public float maxHeight() {
        return prefHeight();
    }

    public boolean contains(float posX, float posY) {
        return (x().get() <= posX) &&
                (posX <= x().get() + width().get()) &&
                (y().get() <= posY) &&
                (posY <= y().get() + height().get());
    }

    public ComponentRenderer getRenderer() {
        if (renderer == null)
            renderer = createDefaultRenderer();
        return renderer;
    }

    public void overrideRenderer(ComponentRenderer r) {
        renderer = r;
    }

    protected abstract ComponentRenderer createDefaultRenderer();

    private ObservableMap<Object, Object> properties;

    public ObservableMap<Object, Object> getProperties() {
        if (properties == null) {
            properties = ObservableCollections.observableMap(new HashMap<>());
        }
        return properties;
    }

    public boolean isResizable() {
        return false;
    }

    public void resize(float width, float height) {
        this.width.set(width);
        this.height.set(height);
    }

    public void relocate(float x, float y) {
        this.x.set(x);
        this.y.set(y);
    }

    public boolean hasProperties() {
        return properties != null && !properties.isEmpty();
    }

    public void forceFocus() {
        focused.set(true);
    }

    public Pair<Float, Float> relativePos(float x, float y) {
        if (parent().isEmpty()) {
            return Pair.of(x, y);
        } else {
            return parent().getValue().relativePos(x - x().get(), y - y().get());
        }
    }

    public EventDispatchChain buildEventDispatchChain(EventDispatchChain tail) {
        return appendEventDispatchChain(tail, this);
    }


    public <T extends Event> void addEventHandler(EventType<T> eventType, EventHandler<T> eventHandler) {
        eventHandlerManager.addEventHandler(eventType, eventHandler);
    }

    public <T extends Event> void removeEventHandler(EventType<T> eventType, EventHandler<T> eventHandler) {
        eventHandlerManager.removeEventHandler(eventType, eventHandler);
    }

    private final MutableObjectValue<EventHandler<MouseActionEvent>> onClick = new SimpleMutableObjectValue<>() {
        @Override
        public void setValue(EventHandler<MouseActionEvent> value) {
            removeEventHandler(MouseActionEvent.MOUSE_CLICKED, getValue());
            super.setValue(value);
            if (value != null) {
                addEventHandler(MouseActionEvent.MOUSE_CLICKED, getValue());
            }
        }
    };

    public EventHandler<MouseActionEvent> getOnClick() {
        return onClick.get();
    }

    public void setOnClick(EventHandler<MouseActionEvent> onClick) {
        this.onClick.set(onClick);
    }

    private final MutableObjectValue<EventHandler<KeyEvent>> onKeyPressed = new SimpleMutableObjectValue<>() {
        @Override
        public void setValue(EventHandler<KeyEvent> value) {
            removeEventHandler(KeyEvent.KEY_PRESSED, getValue());
            super.setValue(value);
            if (value != null) {
                addEventHandler(KeyEvent.KEY_PRESSED, getValue());
            }
        }
    };

    public EventHandler<KeyEvent> getOnKeyPressed() {
        return onKeyPressed.get();
    }

    public void setOnKeyPressed(EventHandler<KeyEvent> onKeyPressed) {
        this.onKeyPressed.set(onKeyPressed);
    }

    private EventDispatchChain appendEventDispatchChain(EventDispatchChain tail, Node node) {
        var parent = node.parent();
        tail.append(node.getEventHandlerManager());
        if (parent.isEmpty()) return tail;
        return appendEventDispatchChain(tail, parent.get());
    }
}
