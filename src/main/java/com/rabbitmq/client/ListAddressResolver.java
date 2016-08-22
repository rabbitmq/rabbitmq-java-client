package com.rabbitmq.client;

import java.util.List;

/**
 * Simple implementation of {@link AddressResolver} that returns a fixed list.
 */
public class ListAddressResolver implements AddressResolver {

    private final List<Address> addresses;

    public ListAddressResolver(List<Address> addresses) {
        this.addresses = addresses;
    }

    @Override
    public List<Address> getAddresses() {
        return addresses;
    }
}
