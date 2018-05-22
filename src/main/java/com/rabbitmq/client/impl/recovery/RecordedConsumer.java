// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

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
        this.consumerTag = this.channel.getDelegate().basicConsume(this.queue, this.autoAck, this.consumerTag, false, this.exclusive, this.arguments, this.consumer);
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
    
    @Override
    public String toString() {
        return "RecordedConsumer[tag=" + consumerTag + ", queue=" + queue + ", autoAck=" + autoAck + ", exclusive=" + exclusive + ", arguments=" + arguments + ", consumer=" + consumer + ", channel=" + channel + "]";
    }
}
