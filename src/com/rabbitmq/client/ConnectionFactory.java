//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//
package com.rabbitmq.client;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.SocketFrameHandler;

/**
 * Convenience "factory" class to facilitate opening a {@link Connection} to an AMQP broker.
 */

public class ConnectionFactory {
    private final ConnectionParameters _params;

    /**
     * Holds the SocketFactory used to manufacture outbound sockets.
     */
    private SocketFactory _factory = SocketFactory.getDefault();

    /**
     * Instantiate a ConnectionFactory with a default set of parameters.
     */
    public ConnectionFactory() {
        _params = new ConnectionParameters();
    }

    /**
     * Instantiate a ConnectionFactory with the given connection parameters.
     * @param params the relevant parameters for instantiating the broker connection
     */
    public ConnectionFactory(ConnectionParameters params) {
        _params = params;
    }

    /**
     * Retrieve the connection parameters.
     * @return the initialization parameters used to open the connection
     */
    public ConnectionParameters getParameters() {
        return _params;
    }

    /**
     * Retrieve the socket factory used to make connections with.
     */
    public SocketFactory getSocketFactory() {
        return _factory;
    }

    /**
     * Set the socket factory used to make connections with. Can be
     * used to enable SSL connections by passing in a
     * javax.net.ssl.SSLSocketFactory instance.
     *
     * @see #useSslProtocol
     */
    public void setSocketFactory(SocketFactory factory) {
        _factory = factory;
    }

    /**
     * Convenience method for setting up a SSL socket factory, using
     * the DEFAULT_SSL_PROTOCOL and a trusting TrustManager.
     */
    public void useSslProtocol()
        throws NoSuchAlgorithmException, KeyManagementException
    {
        useSslProtocol(DEFAULT_SSL_PROTOCOL);
    }

    /**
     * Convenience method for setting up a SSL socket factory, using
     * the DEFAULT_SSL_PROTOCOL and a trusting TrustManager.
     */
    public void useSslProtocol(String protocol)
        throws NoSuchAlgorithmException, KeyManagementException
    {
        useSslProtocol(protocol, new NullTrustManager());
    }

    /**
     * Convenience method for setting up an SSL socket factory.
     * Pass in the SSL protocol to use, e.g. "TLS" or "SSLv3".
     *
     * @param protocol SSL protocol to use.
     */
    public void useSslProtocol(String protocol, TrustManager trustManager)
        throws NoSuchAlgorithmException, KeyManagementException
    {
        SSLContext c = SSLContext.getInstance(protocol);
        c.init(null, new TrustManager[] { trustManager }, null);
        setSocketFactory(c.getSocketFactory());
    }

    /**
     * The default SSL protocol (currently "SSLv3").
     */
    public static final String DEFAULT_SSL_PROTOCOL = "SSLv3";

    protected FrameHandler createFrameHandler(Address addr)
        throws IOException {

        String hostName = addr.getHost();
        int portNumber = addr.getPort();
        if (portNumber == -1) portNumber = AMQP.PROTOCOL.PORT;
        return new SocketFrameHandler(_factory, hostName, portNumber);
    }

    private Connection newConnection(Address[] addrs,
                                     int maxRedirects,
                                     Map<Address,Integer> redirectAttempts)
        throws IOException
    {
        IOException lastException = null;

        for (Address addr : addrs) {
            Address[] lastKnownAddresses = new Address[0];
            try {
                while(true) {
                    FrameHandler frameHandler = createFrameHandler(addr);
                    Integer redirectCount = redirectAttempts.get(addr);
                    if (redirectCount == null)
                        redirectCount = 0;
                    boolean allowRedirects = redirectCount < maxRedirects;
                    try {
                        return new AMQConnection(_params, !allowRedirects, frameHandler);
                    } catch (RedirectException e) {
                        if (!allowRedirects) {
                            //this should never happen with a well-behaved server
                            throw new IOException("server ignored 'insist'");
                        } else {
                            redirectAttempts.put(addr, redirectCount+1);
                            lastKnownAddresses = e.getKnownAddresses();
                            addr = e.getAddress();
                            //TODO: we may want to log redirection attempts.
                        }
                    }
                }
            } catch (IOException e) {
                lastException = e;
                if (lastKnownAddresses.length > 0) {
                    // If there aren't any, don't bother trying, since
                    // a recursive call with empty lastKnownAddresses
                    // will cause our lastException to be stomped on
                    // by an uninformative IOException. See bug 16273.
                    try {
                        return newConnection(lastKnownAddresses,
                                             maxRedirects,
                                             redirectAttempts);
                    } catch (IOException e1) {
                        lastException = e1;
                    }
                }
            }
        }

        if (lastException == null) {
            throw new IOException("failed to connect");
        } else {
            throw lastException;
        }
    }

    /**
     * Create a new broker connection
     * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
     * @param maxRedirects the maximum allowable number of redirects
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection(Address[] addrs, int maxRedirects)
        throws IOException
    {
        return newConnection(addrs,
                             maxRedirects,
                             new HashMap<Address,Integer>());
    }

    /**
     * Create a new broker connection (no redirects allowed)
     * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection(Address[] addrs)
        throws IOException
    {
        return newConnection(addrs, 0);
    }

    /**
     * Instantiates a connection and return an interface to it.
     * @param hostName the host to connect to
     * @param portNumber the port number to use
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection(String hostName, int portNumber) throws IOException {
        return newConnection(new Address[] {
                                 new Address(hostName, portNumber)
                             });
    }

    /**
     * Create a new broker connection, using the default AMQP port
     * @param hostName the host to connect to
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection(String hostName) throws IOException {
        return newConnection(hostName, -1);
    }
}
