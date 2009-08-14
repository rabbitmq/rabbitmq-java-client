package com.rabbitmq.client.impl;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Hashtable;

import com.rabbitmq.client.TunnelProperties;

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
