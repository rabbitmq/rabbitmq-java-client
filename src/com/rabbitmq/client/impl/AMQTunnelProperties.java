package com.rabbitmq.client.impl;

import com.rabbitmq.client.TunnelProperties;

import java.util.Hashtable;
import java.util.Map;

public abstract class AMQTunnelProperties
        extends AMQContentHeader implements TunnelProperties {

    public Object clone() throws CloneNotSupportedException {

        AMQTunnelProperties tpClone = (AMQTunnelProperties) super.clone();

        Map<String, Object> thisHeaders = getHeaders();
        if (thisHeaders != null) {
            Map<String, Object> headers = new Hashtable<String, Object>();
            headers.putAll(thisHeaders);
            tpClone.setHeaders(headers);
        }

        return tpClone;
    }

}
