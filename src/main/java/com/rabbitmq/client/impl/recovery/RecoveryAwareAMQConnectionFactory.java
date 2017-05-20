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

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.FrameHandlerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class RecoveryAwareAMQConnectionFactory {
    private final ConnectionParams params;
    private final FrameHandlerFactory factory;
    private final AddressResolver addressResolver;
    private final MetricsCollector metricsCollector;

    public RecoveryAwareAMQConnectionFactory(ConnectionParams params, FrameHandlerFactory factory, List<Address> addrs) {
        this(params, factory, new ListAddressResolver(addrs), new NoOpMetricsCollector());
    }

    public RecoveryAwareAMQConnectionFactory(ConnectionParams params, FrameHandlerFactory factory, AddressResolver addressResolver) {
        this(params, factory, addressResolver, new NoOpMetricsCollector());
    }

    public RecoveryAwareAMQConnectionFactory(ConnectionParams params, FrameHandlerFactory factory, AddressResolver addressResolver, MetricsCollector metricsCollector) {
        this.params = params;
        this.factory = factory;
        this.addressResolver = addressResolver;
        this.metricsCollector = metricsCollector;
    }

    /**
     * @return an interface to the connection
     * @throws java.io.IOException if it encounters a problem
     */
    // package protected API, made public for testing only
    public RecoveryAwareAMQConnection newConnection() throws IOException, TimeoutException {
        Exception lastException = null;
        List<Address> shuffled = shuffle(addressResolver.getAddresses());

        for (Address addr : shuffled) {
            try {
                FrameHandler frameHandler = factory.create(addr, connectionName());
                RecoveryAwareAMQConnection conn = createConnection(params, frameHandler, metricsCollector);
                conn.start();
                metricsCollector.newConnection(conn);
                return conn;
            } catch (IOException e) {
                lastException = e;
            } catch (TimeoutException te) {
                lastException = te;
            }
        }

        if (lastException != null) {
            if (lastException instanceof IOException) {
                throw (IOException) lastException;
            } else if (lastException instanceof TimeoutException) {
                throw (TimeoutException) lastException;
            }
        }
        throw new IOException("failed to connect");
    }

    private static List<Address> shuffle(List<Address> addrs) {
        List<Address> list = new ArrayList<Address>(addrs);
        Collections.shuffle(list);
        return list;
    }

    protected RecoveryAwareAMQConnection createConnection(ConnectionParams params, FrameHandler handler, MetricsCollector metricsCollector) {
        return new RecoveryAwareAMQConnection(params, handler, metricsCollector);
    }

    private String connectionName() {
        Map<String, Object> clientProperties = params.getClientProperties();
        if (clientProperties == null) {
            return null;
        } else {
            Object connectionName = clientProperties.get("connection_name");
            return connectionName == null ? null : connectionName.toString();
        }
    }
}
