package com.rabbitmq.client;

/**
 * Holder class for a pair of AMQPConnectionParameters and TCPConnectionParameters
 */
public class ConnectionParameters {
    private AMQPConnectionParameters amqpParameters;
    private TCPConnectionParameters tcpParameters;

    public ConnectionParameters(AMQPConnectionParameters amqpp, TCPConnectionParameters tcp){
      this.amqpParameters = amqpp;
      this.tcpParameters = tcp;
    }

    public ConnectionParameters(){
      this(new AMQPConnectionParameters(), new TCPConnectionParameters());
    }

    public AMQPConnectionParameters getAMQPParameters() {
        return amqpParameters;
    }

    public void setAMQPParameters(AMQPConnectionParameters amqpParameters) {
        this.amqpParameters = amqpParameters;
    }

    public TCPConnectionParameters getTCPParameters() {
        return tcpParameters;
    }

    public void setTCPParameters(TCPConnectionParameters tcpParameters) {
        this.tcpParameters = tcpParameters;
    }

    @Override
    public String toString() {
        return "ConnectionParameters{" +
                "amqpParameters=" + amqpParameters +
                ", tcpParameters=" + tcpParameters +
                '}';
    }
}
