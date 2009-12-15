package com.rabbitmq.client;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLSocketFactory;

public class TCPConnectionParameters {
    /**
     * Holds the SocketFactory used to manufacture outbound sockets.
     */
    private SocketFactory _factory;
    private Address _address;

    /**
     * The default SSL protocol (currently "SSLv3").
     */
    public static final String DEFAULT_SSL_PROTOCOL = "SSLv3";

    /**
     * The default port to use for SSL connections. This is not part of the 
     * spec but seems to have been settled on as a convention.  
     */
    public static final int DEFAULT_SSL_PORT = AMQP.PROTOCOL.PORT - 1;

    public TCPConnectionParameters(SocketFactory factory, Address address) {
        this._factory = factory;
        this._address = address;
    }

    public TCPConnectionParameters(String host, int port){
      this(SocketFactory.getDefault(), new Address(host, port));
    }

    public TCPConnectionParameters(String host, boolean useSSL) throws NoSuchAlgorithmException{
      this( 
        useSSL ? SSLContext.getInstance(DEFAULT_SSL_PROTOCOL).getSocketFactory()
               : SocketFactory.getDefault()
        , new Address(host, -1));
    }

    public TCPConnectionParameters(String host){
      this(host, -1);
    }

    public TCPConnectionParameters(){
        this("localhost");
    }


    /**
     * @return the socket factory used to make connections with.
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
     * @returns the host of the underlying address
     */
    public String getHost(){
      return _address.getHost();
    }

    /**
     * @returns the port of the underlying address or a suitable 
     * default if none was provided.
     */
    public int getPort(){
      if(_address.getPort() == -1){
        return isSSL() ? DEFAULT_SSL_PORT : AMQP.PROTOCOL.PORT;
      } else {
        return _address.getPort();
      }
    }

    public boolean isSSL(){
      return _factory instanceof SSLSocketFactory;
    }

    /**
     * Convenience method for setting up a SSL socket factory, using
     * the DEFAULT_SSL_PROTOCOL and a trusting TrustManager.
     */
    public void useSslProtocol()
            throws NoSuchAlgorithmException, KeyManagementException {
        useSslProtocol(DEFAULT_SSL_PROTOCOL);
    }

    /**
     * Convenience method for setting up a SSL socket factory, using
     * the DEFAULT_SSL_PROTOCOL and a trusting TrustManager.
     */
    public void useSslProtocol(String protocol)
            throws NoSuchAlgorithmException, KeyManagementException {
        useSslProtocol(protocol, new NullTrustManager());
    }

    /**
     * Convenience method for setting up an SSL socket factory.
     * Pass in the SSL protocol to use, e.g. "TLS" or "SSLv3".
     *
     * @param protocol SSL protocol to use.
     */
    public void useSslProtocol(String protocol, TrustManager trustManager)
            throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext c = SSLContext.getInstance(protocol);
        c.init(null, new TrustManager[]{trustManager}, null);
        useSslProtocol(c);
    }

    /**
     * Convenience method for setting up an SSL socket factory.
     * Pass in an initialized SSLContext.
     *
     * @param context An initialized SSLContext
     */
    public void useSslProtocol(SSLContext context) {
        setSocketFactory(context.getSocketFactory());
    }

    public Address getAddress() {
        return _address;
    }

    public void setAddress(Address _address) {
        this._address = _address;
    }

        @Override
    public String toString() {
        return "TCPConnectionParameters{" +
                "_factory=" + _factory +
                ", _address=" + _address +
                '}';
    }
}
