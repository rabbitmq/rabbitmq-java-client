package com.rabbitmq.client.test.ssl;

import com.rabbitmq.client.ConnectionFactory;
import junit.framework.TestCase;
import org.junit.Assert;

public class ConnectionFactoryDefaultTlsVersion extends TestCase {

    private ConnectionFactory connectionFactory = new ConnectionFactory();

    public void testDefaultTlsVersionJdk16ShouldTakeFallback() {
        String [] supportedProtocols = {"SSLv2Hello", "SSLv3", "TLSv1"};
        String tlsProtocol = connectionFactory.computeDefaultTlsProcotol(supportedProtocols);
        Assert.assertEquals("TLSv1",tlsProtocol);
    }

    public void testDefaultTlsVersionJdk17ShouldTakePrefered() {
        String [] supportedProtocols = {"SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"};
        String tlsProtocol = connectionFactory.computeDefaultTlsProcotol(supportedProtocols);
        Assert.assertEquals("TLSv1.2",tlsProtocol);
    }

    public void testDefaultTlsVersionJdk18ShouldTakePrefered() {
        String [] supportedProtocols = {"SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"};
        String tlsProtocol = connectionFactory.computeDefaultTlsProcotol(supportedProtocols);
        Assert.assertEquals("TLSv1.2",tlsProtocol);
    }

}
