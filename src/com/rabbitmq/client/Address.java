//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client;

/**
 * A representation of network addresses, i.e. host/port pairs,
 * with some utility functions for parsing address strings.
*/
public class Address {
    /** host name **/
    private final String _host;
    /** port number **/
    private final int _port;

    /**
     * Construct an address from a host name and port number.
     * @param host the host name
     * @param port the port number
     */
    public Address(String host, int port) {
        _host = host;
        _port = port;
    }

    /**
     * Construct an address from a host.
     * @param host the host name
     */
    public Address(String host) {
        _host = host;
        _port = -1;
    }

    /**
     * Get the host name
     * @return the host name
     */
    public String getHost() {
        return _host;
    }

    /**
     * Get the port number
     * @return the port number
     */
    public int getPort() {
        return _port;
    }

    /**
     * Factory method: takes a formatted addressString string as construction parameter
     * @param addressString an addressString of the form "host[:port]".
     * @return an {@link Address} from the given data
     */
    public static Address parseAddress(String addressString) {
        int idx = addressString.indexOf(':');
        return (idx == -1) ?
            new Address(addressString) :
            new Address(addressString.substring(0, idx),
                        Integer.parseInt(addressString.substring(idx+1)));
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
