package nullengine.client.gui.event;

public interface EventDispatcher {

    Event dispatchEvent(Event event, EventDispatchChain eventDispatchChain);
}
