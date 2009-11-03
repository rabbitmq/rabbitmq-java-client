package com.rabbitmq.client;

/**
 * This is a abstraction that allows you to describe all of the endpoints
 * you would like to create a connection to
 */
public class EndpointDescriptor {

    public static final EndpointDescriptor DEFAULT = new EndpointDescriptor("localhost:5672"); 

    private Address[] addresses;

    public EndpointDescriptor(String hostnames) {
        addresses = Address.parseAddresses(hostnames);
    }

    public Address[] getAddresses() {
        return addresses;
    }

    public void setAddresses(Address[] addresses) {
        this.addresses = addresses;
    }
}
