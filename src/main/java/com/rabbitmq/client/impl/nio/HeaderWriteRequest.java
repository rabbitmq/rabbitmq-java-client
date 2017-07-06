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

package com.rabbitmq.client.impl.nio;

import com.rabbitmq.client.AMQP;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 *
 */
public class HeaderWriteRequest implements WriteRequest {

    @Override
    public void handle(DataOutputStream outputStream) throws IOException {
        outputStream.write("AMQP".getBytes("US-ASCII"));
        outputStream.write(0);
        outputStream.write(AMQP.PROTOCOL.MAJOR);
        outputStream.write(AMQP.PROTOCOL.MINOR);
        outputStream.write(AMQP.PROTOCOL.REVISION);
    }
}
