package com.rabbitmq.client.impl;

public interface ChannelManagerFactory {

    ChannelManager instantiateChannelManager(int channelMax);

}
