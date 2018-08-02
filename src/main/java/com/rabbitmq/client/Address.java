// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.


package com.rabbitmq.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A representation of network addresses, i.e. host/port pairs,
 * with some utility functions for parsing address strings.
*/
public class Address {
    private static final Logger LOGGER = LoggerFactory.getLogger(Address.class);

    /**
     * host name
     **/
    private final String _host;
    /**
     * port number
     **/
    private final int _port;

    /**
     * Construct an address from a host name and port number.
     *
     * @param host the host name
     * @param port the port number
     */
    public Address(String host, int port) {
        _host = host;
        _port = port;
    }

    /**
     * Construct an address from a host.
     *
     * @param host the host name
     */
    public Address(String host) {
        _host = host;
        _port = ConnectionFactory.USE_DEFAULT_PORT;
    }

    /**
     * Get the host name
     *
     * @return the host name
     */
    public String getHost() {
        return _host;
    }

    /**
     * Get the port number
     *
     * @return the port number
     */
    public int getPort() {
        return _port;
    }

    /**
     * Extracts hostname or IP address from a string containing a hostname, IP address,
     * hostname:port pair or IP address:port pair.
     * Note that IPv6 addresses must be quoted with square brackets, e.g. [2001:db8:85a3:8d3:1319:8a2e:370:7348].
     *
     * @param addressString the string to extract hostname from
     * @return the hostname or IP address
     */
    public static String parseHost(String addressString) {
        // we need to handle cases such as [2001:db8:85a3:8d3:1319:8a2e:370:7348]:5671
        int lastColon = addressString.lastIndexOf(":");
        int lastClosingSquareBracket = addressString.lastIndexOf("]");
        if (lastClosingSquareBracket == -1) {
            String[] parts = addressString.split(":");
            if (parts.length > 2) {
                String msg = "Address " +
                                    addressString +
                                    " seems to contain an unquoted IPv6 address. Make sure you quote IPv6 addresses like so: [2001:db8:85a3:8d3:1319:8a2e:370:7348]";
                LOGGER.error(msg);
                throw new IllegalArgumentException(msg);
            }

            return parts[0];
        }

        if (lastClosingSquareBracket < lastColon) {
            // there is a port
            return addressString.substring(0, lastColon);
        } else {
            return addressString;
        }
    }

    public static int parsePort(String addressString) {
        // we need to handle cases such as [2001:db8:85a3:8d3:1319:8a2e:370:7348]:5671
        int lastColon = addressString.lastIndexOf(":");
        int lastClosingSquareBracket = addressString.lastIndexOf("]");
        if (lastClosingSquareBracket == -1) {
            String[] parts = addressString.split(":");
            if (parts.length > 2) {
                String msg = "Address " +
                                    addressString +
                                    " seems to contain an unquoted IPv6 address. Make sure you quote IPv6 addresses like so: [2001:db8:85a3:8d3:1319:8a2e:370:7348]";
                LOGGER.error(msg);
                throw new IllegalArgumentException(msg);
            }

            if (parts.length == 2) {
                return Integer.parseInt(parts[1]);
            }

            return ConnectionFactory.USE_DEFAULT_PORT;
        }

        if (lastClosingSquareBracket < lastColon) {
            // there is a port
            return Integer.parseInt(addressString.substring(lastColon + 1));
        }

        return ConnectionFactory.USE_DEFAULT_PORT;
    }

    public static boolean isHostWithPort(String addressString) {
        // we need to handle cases such as [2001:db8:85a3:8d3:1319:8a2e:370:7348]:5671
        int lastColon = addressString.lastIndexOf(":");
        int lastClosingSquareBracket = addressString.lastIndexOf("]");

        if (lastClosingSquareBracket == -1) {
            return addressString.contains(":");
        } else {
            return lastClosingSquareBracket < lastColon;
        }
    }

    /**
     * Factory method: takes a formatted addressString string as construction parameter
     * @param addressString an addressString of the form "host[:port]".
     * @return an {@link Address} from the given data
     */
    public static Address parseAddress(String addressString) {
        if (isHostWithPort(addressString)) {
            return new Address(parseHost(addressString), parsePort(addressString));
        } else {
            return new Address(addressString);
        }
    }

    /**
     * Array-based factory method: takes an array of formatted address strings as construction parameter
     * @param addresses array of strings of form "host[:port],..."
     * @return a list of {@link Address} values
     */
    public static Address[] parseAddresses(String addresses) {
        String[] addrs = addresses.split(" *, *");
        Address[] res = new Address[addrs.length];
        for (int i = 0; i < addrs.length; i++) {
            res[i] = Address.parseAddress(addrs[i]);
        }
        return res;
    }

    @Override public int hashCode() {
        return 31 * _host.hashCode() + _port;
    }

    @Override public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        final Address addr = (Address)obj;
        return _host.equals(addr._host) && _port == addr._port;
    }

    @Override public String toString() {
        return _port == -1 ? _host : _host + ":" + _port;
    }

}
