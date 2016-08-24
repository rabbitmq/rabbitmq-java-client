package com.rabbitmq.client.test;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DnsRecordIpAddressResolver;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 *
 */
public class DnsRecordIpAddressResolverTests extends BrokerTestCase {

    public void testLocalhostResolution() throws IOException, TimeoutException {
        DnsRecordIpAddressResolver addressResolver = new DnsRecordIpAddressResolver("localhost");
        ConnectionFactory connectionFactory = newConnectionFactory();
        Connection connection = connectionFactory.newConnection(addressResolver);
        try {
            connection.createChannel();
        } finally {
            connection.abort();
        }
    }

    public void testLoopbackInterfaceResolution() throws IOException, TimeoutException {
        DnsRecordIpAddressResolver addressResolver = new DnsRecordIpAddressResolver("127.0.0.1");
        ConnectionFactory connectionFactory = newConnectionFactory();
        Connection connection = connectionFactory.newConnection(addressResolver);
        try {
            connection.createChannel();
        } finally {
            connection.abort();
        }
    }

    public void testResolutionFails() throws IOException, TimeoutException {
        DnsRecordIpAddressResolver addressResolver = new DnsRecordIpAddressResolver(
            "afancyandunlikelyhostname"
        );
        try {
            connectionFactory.newConnection(addressResolver);
            fail("The host resolution should have failed");
        } catch (IOException e) {
            // expected
        }
    }
}
