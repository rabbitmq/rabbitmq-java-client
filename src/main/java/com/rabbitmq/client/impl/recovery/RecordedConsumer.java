package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.Consumer;

import java.io.IOException;
import java.util.Map;

/**
 * @since 3.3.0
 */
public class RecordedConsumer extends RecordedEntity {
    private String queue;
    private String consumerTag;
    private Consumer consumer;
    private boolean exclusive;
    private boolean autoAck;
    private Map<String, Object> arguments;

    public RecordedConsumer(AutorecoveringChannel channel, String queue) {
        super(channel);
        this.queue = queue;
    }

    public RecordedConsumer consumerTag(String value) {
        this.consumerTag = value;
        return this;
    }

    public RecordedConsumer consumer(Consumer value) {
        this.consumer = value;
        return this;
    }

    public RecordedConsumer exclusive(boolean value) {
        this.exclusive = value;
        return this;
    }

    public RecordedConsumer autoAck(boolean value) {
        this.autoAck = value;
        return this;
    }

    public String recover() throws IOException {
        this.consumerTag = this.channel.basicConsume(this.queue, this.autoAck, this.consumerTag, false, this.exclusive, this.arguments, this.consumer);
        return this.consumerTag;
    }

    public RecordedConsumer arguments(Map<String, Object> value) {
        this.arguments = value;
        return this;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getConsumerTag() {
        return consumerTag;
    }
}
