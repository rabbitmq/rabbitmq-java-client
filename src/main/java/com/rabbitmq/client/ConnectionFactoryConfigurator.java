// Copyright (c) 2017-Present Pivotal Software, Inc.  All rights reserved.
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

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.nio.NioParams;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Helper class to load {@link ConnectionFactory} settings from a property file.
 *
 * The authorised keys are the constants values in this class (e.g. USERNAME).
 * The property file/properties instance/map instance keys can have
 * a prefix, the default being <code>rabbitmq.</code>.
 *
 * Property files can be loaded from the file system (the default),
 * but also from the classpath, by using the <code>classpath:</code> prefix
 * in the location.
 *
 * Client properties can be set by using
 * the <code>client.properties.</code> prefix, e.g. <code>client.properties.app.name</code>.
 * Default client properties and custom client properties are merged. To remove
 * a default client property, set its key to an empty value.
 *
 * @since 4.4.0
 * @see ConnectionFactory#load(String, String)
 */
public class ConnectionFactoryConfigurator {

    public static final String DEFAULT_PREFIX = "rabbitmq.";

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password"; //NOSONAR
    public static final String VIRTUAL_HOST = "virtual.host";
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String CONNECTION_CHANNEL_MAX = "connection.channel.max";
    public static final String CONNECTION_FRAME_MAX = "connection.frame.max";
    public static final String CONNECTION_HEARTBEAT = "connection.heartbeat";
    public static final String CONNECTION_TIMEOUT = "connection.timeout";
    public static final String HANDSHAKE_TIMEOUT = "handshake.timeout";
    public static final String SHUTDOWN_TIMEOUT = "shutdown.timeout";
    public static final String CLIENT_PROPERTIES_PREFIX = "client.properties.";
    public static final String CONNECTION_RECOVERY_ENABLED = "connection.recovery.enabled";
    public static final String TOPOLOGY_RECOVERY_ENABLED = "topology.recovery.enabled";
    public static final String CONNECTION_RECOVERY_INTERVAL = "connection.recovery.interval";
    public static final String CHANNEL_RPC_TIMEOUT = "channel.rpc.timeout";
    public static final String CHANNEL_SHOULD_CHECK_RPC_RESPONSE_TYPE = "channel.should.check.rpc.response.type";
    public static final String USE_NIO = "use.nio";
    public static final String NIO_READ_BYTE_BUFFER_SIZE = "nio.read.byte.buffer.size";
    public static final String NIO_WRITE_BYTE_BUFFER_SIZE = "nio.write.byte.buffer.size";
    public static final String NIO_NB_IO_THREADS = "nio.nb.io.threads";
    public static final String NIO_WRITE_ENQUEUING_TIMEOUT_IN_MS = "nio.write.enqueuing.timeout.in.ms";
    public static final String NIO_WRITE_QUEUE_CAPACITY = "nio.write.queue.capacity";

    @SuppressWarnings("unchecked")
    public static void load(ConnectionFactory cf, String propertyFileLocation, String prefix) throws IOException {
        if (propertyFileLocation == null || propertyFileLocation.isEmpty()) {
            throw new IllegalArgumentException("Property file argument cannot be null or empty");
        }
        Properties properties = new Properties();
        if (propertyFileLocation.startsWith("classpath:")) {
            InputStream in = null;
            try {
                in = ConnectionFactoryConfigurator.class.getResourceAsStream(
                    propertyFileLocation.substring("classpath:".length())
                );
                properties.load(in);
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        } else {
            Reader reader = null;
            try {
                reader = new BufferedReader(new FileReader(propertyFileLocation));
                properties.load(reader);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
        load(cf, (Map) properties, prefix);
    }

    public static void load(ConnectionFactory cf, Map<String, String> properties, String prefix) {
        prefix = prefix == null ? "" : prefix;
        String uri = properties.get(prefix + "uri");
        if (uri != null) {
            try {
                cf.setUri(uri);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Error while setting AMQP URI: "+uri, e);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Error while setting AMQP URI: "+uri, e);
            } catch (KeyManagementException e) {
                throw new IllegalArgumentException("Error while setting AMQP URI: "+uri, e);
            }
        }
        String username = properties.get(prefix + USERNAME);
        if (username != null) {
            cf.setUsername(username);
        }
        String password = properties.get(prefix + PASSWORD);
        if (password != null) {
            cf.setPassword(password);
        }
        String vhost = properties.get(prefix + VIRTUAL_HOST);
        if (vhost != null) {
            cf.setVirtualHost(vhost);
        }
        String host = properties.get(prefix + HOST);
        if (host != null) {
            cf.setHost(host);
        }
        String port = properties.get(prefix + PORT);
        if (port != null) {
            cf.setPort(Integer.valueOf(port));
        }
        String requestedChannelMax = properties.get(prefix + CONNECTION_CHANNEL_MAX);
        if (requestedChannelMax != null) {
            cf.setRequestedChannelMax(Integer.valueOf(requestedChannelMax));
        }
        String requestedFrameMax = properties.get(prefix + CONNECTION_FRAME_MAX);
        if (requestedFrameMax != null) {
            cf.setRequestedFrameMax(Integer.valueOf(requestedFrameMax));
        }
        String requestedHeartbeat = properties.get(prefix + CONNECTION_HEARTBEAT);
        if (requestedHeartbeat != null) {
            cf.setRequestedHeartbeat(Integer.valueOf(requestedHeartbeat));
        }
        String connectionTimeout = properties.get(prefix + CONNECTION_TIMEOUT);
        if (connectionTimeout != null) {
            cf.setConnectionTimeout(Integer.valueOf(connectionTimeout));
        }
        String handshakeTimeout = properties.get(prefix + HANDSHAKE_TIMEOUT);
        if (handshakeTimeout != null) {
            cf.setHandshakeTimeout(Integer.valueOf(handshakeTimeout));
        }
        String shutdownTimeout = properties.get(prefix + SHUTDOWN_TIMEOUT);
        if (shutdownTimeout != null) {
            cf.setShutdownTimeout(Integer.valueOf(shutdownTimeout));
        }

        Map<String, Object> clientProperties = new HashMap<String, Object>();
        Map<String, Object> defaultClientProperties = AMQConnection.defaultClientProperties();
        clientProperties.putAll(defaultClientProperties);

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey().startsWith(prefix + CLIENT_PROPERTIES_PREFIX)) {
                String clientPropertyKey = entry.getKey().substring((prefix + CLIENT_PROPERTIES_PREFIX).length());
                if (defaultClientProperties.containsKey(clientPropertyKey) && (entry.getValue() == null || entry.getValue().trim().isEmpty())) {
                    // if default property and value is empty, remove this property
                    clientProperties.remove(clientPropertyKey);
                } else {
                    clientProperties.put(
                        clientPropertyKey,
                        entry.getValue()
                    );
                }
            }
        }
        cf.setClientProperties(clientProperties);

        String automaticRecovery = properties.get(prefix + CONNECTION_RECOVERY_ENABLED);
        if (automaticRecovery != null) {
            cf.setAutomaticRecoveryEnabled(Boolean.valueOf(automaticRecovery));
        }
        String topologyRecovery = properties.get(prefix + TOPOLOGY_RECOVERY_ENABLED);
        if (topologyRecovery != null) {
            cf.setTopologyRecoveryEnabled(Boolean.getBoolean(topologyRecovery));
        }
        String networkRecoveryInterval = properties.get(prefix + CONNECTION_RECOVERY_INTERVAL);
        if (networkRecoveryInterval != null) {
            cf.setNetworkRecoveryInterval(Long.valueOf(networkRecoveryInterval));
        }
        String channelRpcTimeout = properties.get(prefix + CHANNEL_RPC_TIMEOUT);
        if (channelRpcTimeout != null) {
            cf.setChannelRpcTimeout(Integer.valueOf(channelRpcTimeout));
        }
        String channelShouldCheckRpcResponseType = properties.get(prefix + CHANNEL_SHOULD_CHECK_RPC_RESPONSE_TYPE);
        if (channelShouldCheckRpcResponseType != null) {
            cf.setChannelShouldCheckRpcResponseType(Boolean.valueOf(channelShouldCheckRpcResponseType));
        }

        String useNio = properties.get(prefix + USE_NIO);
        if (useNio != null && Boolean.valueOf(useNio)) {
            cf.useNio();

            NioParams nioParams = new NioParams();

            String readByteBufferSize = properties.get(prefix + NIO_READ_BYTE_BUFFER_SIZE);
            if (readByteBufferSize != null) {
                nioParams.setReadByteBufferSize(Integer.valueOf(readByteBufferSize));
            }
            String writeByteBufferSize = properties.get(prefix + NIO_WRITE_BYTE_BUFFER_SIZE);
            if (writeByteBufferSize != null) {
                nioParams.setWriteByteBufferSize(Integer.valueOf(writeByteBufferSize));
            }
            String nbIoThreads = properties.get(prefix + NIO_NB_IO_THREADS);
            if (nbIoThreads != null) {
                nioParams.setNbIoThreads(Integer.valueOf(nbIoThreads));
            }
            String writeEnqueuingTime = properties.get(prefix + NIO_WRITE_ENQUEUING_TIMEOUT_IN_MS);
            if (writeEnqueuingTime != null) {
                nioParams.setWriteEnqueuingTimeoutInMs(Integer.valueOf(writeEnqueuingTime));
            }
            String writeQueueCapacity = properties.get(prefix + NIO_WRITE_QUEUE_CAPACITY);
            if (writeQueueCapacity != null) {
                nioParams.setWriteQueueCapacity(Integer.valueOf(writeQueueCapacity));
            }
            cf.setNioParams(nioParams);
        }
    }

    public static void load(ConnectionFactory connectionFactory, String propertyFileLocation) throws IOException {
        load(connectionFactory, propertyFileLocation, DEFAULT_PREFIX);
    }

    @SuppressWarnings("unchecked")
    public static void load(ConnectionFactory connectionFactory, Properties properties) {
        load(connectionFactory, (Map) properties, DEFAULT_PREFIX);
    }

    @SuppressWarnings("unchecked")
    public static void load(ConnectionFactory connectionFactory, Properties properties, String prefix) {
        load(connectionFactory, (Map) properties, prefix);
    }

    public static void load(ConnectionFactory connectionFactory, Map<String, String> properties) {
        load(connectionFactory, properties, DEFAULT_PREFIX);
    }
}
