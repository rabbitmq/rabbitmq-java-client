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

    private ConnectionParameters[] _connectionParams;

    /**
     * Instantiate a ConnectionFactory with a default set of parameters.
     */
    public ConnectionFactory() {
      _connectionParams = new ConnectionParameters[]{ new ConnectionParameters() };  
    }

    /**
     * Instantiate a ConnectionFactory with the given connection parameters.
     * @param params the relevant parameters for instantiating the broker connection
     */
    public ConnectionFactory(AMQPConnectionParameters amqpParams, TCPConnectionParameters tcpParams) {
      _connectionParams = new ConnectionParameters[]{ new ConnectionParameters(amqpParams, tcpParams) };
    }

    public ConnectionFactory(ConnectionParameters... connectionParams){
      _connectionParams = connectionParams;
    }

    public ConnectionFactory(TCPConnectionParameters tcpParams){
      this(new AMQPConnectionParameters(), tcpParams);
    }

    public ConnectionFactory(AMQPConnectionParameters amqpParams, TCPConnectionParameters... tcpParams){
      if(tcpParams.length == 0)
        tcpParams = new TCPConnectionParameters[]{ new TCPConnectionParameters() };

      _connectionParams = new ConnectionParameters[tcpParams.length];
      for(int i = 0; i < tcpParams.length; i++)
        _connectionParams[i] = new ConnectionParameters(amqpParams, tcpParams[i]);
    }


    public ConnectionParameters[] getConnectionParameters(){
      return _connectionParams;
    }

    public void setConnectionParameters(ConnectionParameters... params){
      _connectionParams = params;
    }

    protected FrameHandler createFrameHandler(TCPConnectionParameters params)
        throws IOException {
        String hostName = params.getHost();
        int portNumber = params.getPort();
        return new SocketFrameHandler(params.getSocketFactory(), hostName, portNumber);
    }

    private Connection newConnection(ConnectionParameters[] connectionParams,
                                     int maxRedirects,
                                     Map<Address,Integer> redirectAttempts)
        throws IOException
    {
        IOException lastException = null;

        for (ConnectionParameters params : connectionParams) {            
            TCPConnectionParameters tcpParams = params.getTCPParameters();
            Address addr = tcpParams.getAddress();
            ConnectionParameters[] redirectionTargets = new ConnectionParameters[0];
            try {
                while(true) {
                    FrameHandler frameHandler = createFrameHandler(tcpParams);
                    Integer redirectCount = redirectAttempts.get(addr);
                    if (redirectCount == null)
                        redirectCount = 0;
                    boolean allowRedirects = redirectCount < maxRedirects;
                    try {
                        AMQConnection conn = 
                          new AMQConnection(
                            params.getAMQPParameters(),
                            frameHandler);
                        conn.start(!allowRedirects);
                        return conn;
                    } catch (RedirectException e) {
                        if (!allowRedirects) {
                            //this should never happen with a well-behaved server
                            throw new IOException("server ignored 'insist'");
                        } else {
                            redirectAttempts.put(addr, redirectCount+1);
                            Address[] knownAddresses = e.getKnownAddresses();
                            redirectionTargets = new ConnectionParameters[knownAddresses.length];
                            for(int i = 0; i < redirectionTargets.length; i++){
                              ConnectionParameters redirectParams = new ConnectionParameters();
                              redirectParams.setAMQPParameters(params.getAMQPParameters());
                              TCPConnectionParameters redirectTcp = new TCPConnectionParameters();
                              redirectTcp.setSocketFactory(tcpParams.getSocketFactory());
                              redirectTcp.setAddress(knownAddresses[i]);
                              redirectParams.setTCPParameters(redirectTcp);
                              redirectionTargets[i] = redirectParams;
                            }
                            addr = e.getAddress();
                            //TODO: we may want to log redirection attempts.
                        }
                    }
                }
            } catch (IOException e) {
                lastException = e;
                if (redirectionTargets.length > 0) {
                    // If there aren't any, don't bother trying, since
                    // a recursive call with empty redirectionTargets
                    // will cause our lastException to be stomped on
                    // by an uninformative IOException. See bug 16273.
                    try {
                        return newConnection(redirectionTargets,
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
    public Connection newConnection(int maxRedirects)
        throws IOException
    {
        return newConnection(_connectionParams,
                             maxRedirects,
                             new HashMap<Address,Integer>());
    }

    /**
     * Create a new broker connection (no redirects allowed)
     * @param addrs an array of known broker addresses (hostname/port pairs) to try in order
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    public Connection newConnection()
        throws IOException
    {
        return newConnection(0);
    }
}
