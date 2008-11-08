package com.rabbitmq.client;

/**
 * Interface for components that are shutdown capable and
 * that allow listeners to be added for shutdown signals
 *
 * @see ShutdownListener
 * @see ShutdownSignalException
 */
public interface ShutdownNotifier {
    /**
     * Add shutdown listener.
     * If the component is already closed, handler is fired immediately
     *
     * @param listener {@link ShutdownListener} to the component
     */
    public void addShutdownListener(ShutdownListener listener);

    /**
     * Remove shutdown listener for the component.
     *
     * @param listener {@link ShutdownListener} to be removed
     */
    public void removeShutdownListener(ShutdownListener listener);

    /**
     * Get the shutdown reason object
     * @return ShutdownSignalException if component is closed, null otherwise
     */
    public ShutdownSignalException getCloseReason();

    /**
     * Protected API - notify the listeners attached to the component
     * @see com.rabbitmq.client.ShutdownListener
     */
    public void notifyListeners();

    /**
     * Determine whether the component is currently open.
     * Will return false if we are currently closing.
     * Checking this method should be only for information,
     * because of the race conditions - state can change after the call.
     * Instead just execute and try to catch ShutdownSignalException
     *
     * @return true when component is open, false otherwise
     */
    boolean isOpen();
}
