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

package com.rabbitmq.client;

/**
 *
 */
public class NoOpMetricsCollector implements MetricsCollector {

    @Override
    public void newConnection(Connection connection) {

    }

    @Override
    public void closeConnection(Connection connection) {

    }

    @Override
    public void newChannel(Channel channel) {

    }

    @Override
    public void closeChannel(Channel channel) {

    }

    @Override
    public void basicAck(Channel channel, long deliveryTag, boolean multiple) {

    }

    @Override
    public void basicNack(Channel channel, long deliveryTag) {

    }

    @Override
    public void basicReject(Channel channel, long deliveryTag) {

    }

    @Override
    public void basicConsume(Channel channel, String consumerTag, boolean autoAck) {

    }

    @Override
    public void basicCancel(Channel channel, String consumerTag) {

    }

    @Override
    public void basicPublish(Channel channel, long deliveryTag) {

    }

    @Override
    public void basicPublishFailure(Channel channel, Throwable cause) {

    }

    @Override
    public void basicPublishAck(Channel channel, long deliveryTag, boolean multiple) {

    }

    @Override
    public void basicPublishNack(Channel channel, long deliveryTag, boolean multiple) {

    }

    @Override
    public void basicPublishUnrouted(Channel channel) {

    }

    @Override
    public void consumedMessage(Channel channel, long deliveryTag, boolean autoAck) {

    }

    @Override
    public void consumedMessage(Channel channel, long deliveryTag, String consumerTag) {

    }

}
