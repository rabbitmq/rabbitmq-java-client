package com.rabbitmq.client;

/**
 * Contract to log outbound and inbound {@link Command}s.
 *
 * @see ConnectionFactory#setTrafficListener(TrafficListener)
 * @since 5.5.0
 */
public interface TrafficListener {

    /**
     * No-op {@link TrafficListener}.
     */
    TrafficListener NO_OP = new TrafficListener() {

        @Override
        public void write(Command outboundCommand) {

        }

        @Override
        public void read(Command inboundCommand) {

        }
    };

    /**
     * Notified for each outbound {@link Command}.
     *
     * @param outboundCommand
     */
    void write(Command outboundCommand);

    /**
     * Notified for each inbound {@link Command}.
     *
     * @param inboundCommand
     */
    void read(Command inboundCommand);
}
