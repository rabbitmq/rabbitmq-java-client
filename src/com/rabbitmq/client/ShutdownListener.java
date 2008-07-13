package com.rabbitmq.client;

import java.util.EventListener;

public interface ShutdownListener extends EventListener {

    public void shutdownCompleted(ShutdownSignalException cause);

}
