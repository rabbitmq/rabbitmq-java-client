package com.rabbitmq.client;

import java.io.IOException;
import java.util.List;

/**
 * Strategy interface to get the potential servers to connect to.
 */
public interface AddressResolver {

    List<Address> getAddresses() throws IOException;

}
