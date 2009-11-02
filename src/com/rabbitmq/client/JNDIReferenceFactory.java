package com.rabbitmq.client;

import javax.naming.spi.ObjectFactory;
import javax.naming.Name;
import javax.naming.Context;
import java.util.Hashtable;

public class JNDIReferenceFactory implements ObjectFactory {

    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> env) throws Exception {
        ConnectionParameters params = new ConnectionParameters();
        params.setUsername((String) env.get("hostname"));
        // TODO Delete this, it is just for debugging :-)
        System.err.println("BOOTING RABBIT on host: " + params.getHostName());
        return new ConnectionFactory(params);
    }
}
