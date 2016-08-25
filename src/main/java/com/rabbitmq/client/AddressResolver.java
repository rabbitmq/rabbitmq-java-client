package com.rabbitmq.client;

import java.io.IOException;
import java.util.List;

/**
 * Strategy interface to get the potential servers to connect to.
 */
public interface AddressResolver {

    /**
     * Get the potential {@link Address}es to connect to.
     * @return candidate {@link Address}es
     * @throws IOException if it encounters a problem
     */
    List<Address> getAddresses() throws IOException;

}
