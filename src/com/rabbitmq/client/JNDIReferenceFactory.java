package com.rabbitmq.client;

import javax.naming.spi.ObjectFactory;
import javax.naming.Name;
import javax.naming.Context;
import java.util.Hashtable;
import java.util.Enumeration;
import javax.naming.Reference;

/**
 * This provides a way to declare the configuration parameters in a container resource descriptor.
 *
 * For example, with Tomcat, in the server.xml, you can declare the following stanza:
 *
 * <Resource    name="amqp/connectionFactory"
 *              auth="Container"
 *              type="com.rabbitmq.client.ConnectionFactory"
 *              factory="com.rabbitmq.client.JNDIReferenceFactory"
 *              hostname="localhost:5672"
 *              username="guest"
 *              password="password"
 *              vhost="/"
 * "/>
 * 
 */
public class JNDIReferenceFactory implements ObjectFactory {

    public Object getObjectInstance(Object object, Name name, Context nameCtx, Hashtable env) throws Exception {
        if (object instanceof Reference) {
            Reference reference = (Reference) object;
            String endpoints = (String) reference.get("endpoints").getContent();
            EndpointDescriptor descriptor = new EndpointDescriptor(endpoints);
            ConnectionParameters params = new ConnectionParameters();
            params.setUsername((String) reference.get("username").getContent());
            params.setPassword((String) reference.get("password").getContent());
            params.setVirtualHost((String) reference.get("vhost").getContent());
            return new ConnectionFactory(params, descriptor);            
        }
        else {
            // Probably the best way to log something in Rabbit
            throw new RuntimeException("Could not get a reference to the named object");
        }
    }
}
