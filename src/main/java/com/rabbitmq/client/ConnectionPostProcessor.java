// Copyright (c) 2018-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

import java.io.IOException;

/**
 * Hook to add processing on the open TCP connection.
 * <p>
 * Used e.g. to add hostname verification on TLS connection.
 *
 * @since 4.8.0
 */
public interface ConnectionPostProcessor {

    /**
     * Post-process the open TCP connection.
     *
     * @param context some TCP connection context (e.g. socket)
     * @throws IOException
     */
    void postProcess(ConnectionContext context) throws IOException;
}
