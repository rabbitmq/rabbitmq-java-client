package com.rabbitmq.client.test;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DnsRecordIpAddressResolver;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.fail;

/**
 *
 */
public class DnsRecordIpAddressResolverTests extends BrokerTestCase {

    @Test public void localhostResolution() throws IOException, TimeoutException {
        DnsRecordIpAddressResolver addressResolver = new DnsRecordIpAddressResolver("localhost");
        ConnectionFactory connectionFactory = newConnectionFactory();
        Connection connection = connectionFactory.newConnection(addressResolver);
        try {
            connection.createChannel();
        } finally {
            connection.abort();
        }
    }

    @Test public void loopbackInterfaceResolution() throws IOException, TimeoutException {
        DnsRecordIpAddressResolver addressResolver = new DnsRecordIpAddressResolver("127.0.0.1");
        ConnectionFactory connectionFactory = newConnectionFactory();
        Connection connection = connectionFactory.newConnection(addressResolver);
        try {
            connection.createChannel();
        } finally {
            connection.abort();
        }
    }

    @Test public void resolutionFails() throws IOException, TimeoutException {
        DnsRecordIpAddressResolver addressResolver = new DnsRecordIpAddressResolver(
            "afancyandunlikelyhostname"
        );
        try {
            connectionFactory.newConnection(addressResolver);
            fail("The host resolution should have failed");
        } catch (UnknownHostException e) {
            // expected
        }
    }
}
