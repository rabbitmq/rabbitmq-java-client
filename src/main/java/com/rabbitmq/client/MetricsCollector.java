// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

package com.rabbitmq.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.rabbitmq.client.impl.AMQCommand;

/**
 * Interface to gather execution data of the client.
 * Note transactions are not supported: they deal with
 * publishing and acknowledgments and the collector contract
 * assumes then that published messages and acks sent
 * in a transaction are always counted, even if the
 * transaction is rolled back.
 *
 */
public interface MetricsCollector {

    void newConnection(Connection connection);

    void closeConnection(Connection connection);

    void newChannel(Channel channel);

    void closeChannel(Channel channel);

    void basicPublish(Channel channel, long deliveryTag);

    default void basicPublishFailure(Channel channel, Throwable cause) {

    }

    default void basicPublishAck(Channel channel, long deliveryTag, boolean multiple) {

    }

    default void basicPublishNack(Channel channel, long deliveryTag, boolean multiple) {

    }

    default void basicPublishUnrouted(Channel channel) {

    }

    void consumedMessage(Channel channel, long deliveryTag, boolean autoAck);

    void consumedMessage(Channel channel, long deliveryTag, String consumerTag);

    void basicAck(Channel channel, long deliveryTag, boolean multiple);

    void basicNack(Channel channel, long deliveryTag);

    void basicReject(Channel channel, long deliveryTag);

    void basicConsume(Channel channel, String consumerTag, boolean autoAck);

    default Consumer basicPreConsume(Channel channel, String consumerTag, boolean autoAck, AMQCommand amqCommand, Consumer callback) {
        return callback;
    }

    void basicCancel(Channel channel, String consumerTag);

    default void basicPrePublish(Channel channel, long deliveryTag, PublishArguments publishArguments) {

    }

    default void basicPublishFailure(Channel channel, Exception exception, PublishArguments publishArguments) {
        basicPublishFailure(channel, exception);
    }

    default void basicPublish(Channel channel, long deliveryTag, PublishArguments publishArguments) {
        basicPublish(channel, deliveryTag);
    }

    class PublishArguments {
        private AMQP.Basic.Publish publish;

        private AMQP.BasicProperties props;

        private byte[] body;

        private final Map<String, Object> headers = new HashMap<>();

        private final Map<Object, Object> context = new HashMap<>();

        public PublishArguments(AMQP.Basic.Publish publish, AMQP.BasicProperties props, byte[] body) {
            this.publish = Objects.requireNonNull(publish);
            this.props = Objects.requireNonNull(props);
            this.body = Objects.requireNonNull(body);
        }

        public AMQP.Basic.Publish getPublish() {
            return publish;
        }

        public AMQP.BasicProperties getProps() {
            return props;
        }

        public byte[] getBody() {
            return body;
        }

        public void setPublish(AMQP.Basic.Publish publish) {
            this.publish = publish;
        }

        public void setProps(AMQP.BasicProperties props) {
            this.props = props;
        }

        public void setBody(byte[] body) {
            this.body = body;
        }

        public Map<Object, Object> getContext() {
            return context;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }
    }
}
