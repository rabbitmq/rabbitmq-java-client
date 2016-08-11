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

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.impl.AMQContentHeader;

/**
 * Constant holder class with useful static instances of {@link AMQContentHeader}.
 * These are intended for use with {@link Channel#basicPublish} and other Channel methods.
 */
public class MessageProperties {

    /** Empty basic properties, with no fields set */
    public static final BasicProperties MINIMAL_BASIC =
        new BasicProperties(null, null, null, null,
                            null, null, null, null,
                            null, null, null, null,
                            null, null);
    /** Empty basic properties, with only deliveryMode set to 2 (persistent) */
    public static final BasicProperties MINIMAL_PERSISTENT_BASIC =
        new BasicProperties(null, null, null, 2,
                            null, null, null, null,
                            null, null, null, null,
                            null, null);

    /** Content-type "application/octet-stream", deliveryMode 1 (nonpersistent), priority zero */
    public static final BasicProperties BASIC =
        new BasicProperties("application/octet-stream",
                            null,
                            null,
                            1,
                            0, null, null, null,
                            null, null, null, null,
                            null, null);

    /** Content-type "application/octet-stream", deliveryMode 2 (persistent), priority zero */
    public static final BasicProperties PERSISTENT_BASIC =
        new BasicProperties("application/octet-stream",
                            null,
                            null,
                            2,
                            0, null, null, null,
                            null, null, null, null,
                            null, null);

    /** Content-type "text/plain", deliveryMode 1 (nonpersistent), priority zero */
    public static final BasicProperties TEXT_PLAIN =
        new BasicProperties("text/plain",
                            null,
                            null,
                            1,
                            0, null, null, null,
                            null, null, null, null,
                            null, null);

    /** Content-type "text/plain", deliveryMode 2 (persistent), priority zero */
    public static final BasicProperties PERSISTENT_TEXT_PLAIN =
        new BasicProperties("text/plain",
                            null,
                            null,
                            2,
                            0, null, null, null,
                            null, null, null, null,
                            null, null);
}
