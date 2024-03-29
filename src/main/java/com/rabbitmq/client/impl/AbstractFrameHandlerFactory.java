// Copyright (c) 2016-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.SocketConfigurator;

/**
 *
 */
public abstract class AbstractFrameHandlerFactory implements FrameHandlerFactory {

    protected final int connectionTimeout;
    protected final SocketConfigurator configurator;
    protected final boolean ssl;
    protected final int maxInboundMessageBodySize;

    protected AbstractFrameHandlerFactory(int connectionTimeout, SocketConfigurator configurator,
                                          boolean ssl, int maxInboundMessageBodySize) {
        this.connectionTimeout = connectionTimeout;
        this.configurator = configurator;
        this.ssl = ssl;
        this.maxInboundMessageBodySize = maxInboundMessageBodySize;
    }
}
