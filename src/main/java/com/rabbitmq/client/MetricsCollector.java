// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

    default void basicNack(Channel channel, long deliveryTag, boolean requeue) {
        this.basicNack(channel, deliveryTag);
    }

    void basicReject(Channel channel, long deliveryTag);

    default void basicReject(Channel channel, long deliveryTag, boolean requeue) {
        this.basicReject(channel, deliveryTag);
    }

    void basicConsume(Channel channel, String consumerTag, boolean autoAck);

    void basicCancel(Channel channel, String consumerTag);

}