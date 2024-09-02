package uxn;

public interface UXNEvent {
    /**
     * A UXN event. when this function is called implementations should set the PC of the uxn and relevant device bytes
     * @param uxn the UXN cpu handling this event
     */
    void handle(UXN uxn);
}
