package com.rabbitmq.client.impl;

import com.rabbitmq.client.Command;
import com.rabbitmq.client.TrafficListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link TrafficListener} that logs {@link Command} at <code>TRACE</code> level.
 * <p>
 * This implementation checks whether the <code>TRACE</code> log level
 * is enabled before logging anything. This {@link TrafficListener}
 * should only be activated for debugging purposes, not in a production
 * environment.
 *
 * @see TrafficListener
 * @see com.rabbitmq.client.ConnectionFactory#setTrafficListener(TrafficListener)
 * @since 5.5.0
 */
public class LogTrafficListener implements TrafficListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogTrafficListener.class);

    @Override
    public void write(Command outboundCommand) {
        if (shouldLog(outboundCommand)) {
            LOGGER.trace("Outbound command: {}", outboundCommand);
        }
    }

    @Override
    public void read(Command inboundCommand) {
        if (shouldLog(inboundCommand)) {
            LOGGER.trace("Inbound command: {}", inboundCommand);
        }
    }

    protected boolean shouldLog(Command command) {
        return LOGGER.isTraceEnabled();
    }
}
