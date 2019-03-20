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
 * Public interface to objects representing an AMQP 0-9-1 method
 * @see https://www.rabbitmq.com/specification.html.
 */

public interface Method {
    /**
     * Retrieve the protocol class ID
     * @return the AMQP protocol class ID of this Method
     */
    int protocolClassId(); /* properly an unsigned short */

    /**
     * Retrieve the protocol method ID
     * @return the AMQP protocol method ID of this Method
     */
    int protocolMethodId(); /* properly an unsigned short */

    /**
     * Retrieve the method name
     * @return the AMQP protocol method name of this Method
     */
    String protocolMethodName();
}
