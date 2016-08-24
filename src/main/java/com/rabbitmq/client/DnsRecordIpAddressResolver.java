package com.rabbitmq.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class DnsRecordIpAddressResolver implements AddressResolver {

    private final Address address;

    private final boolean ssl;

    public DnsRecordIpAddressResolver(String hostname, int port, boolean ssl) {
        this(new Address(hostname, port), ssl);
    }

    public DnsRecordIpAddressResolver(String hostname, int port) {
        this(new Address(hostname, port), false);
    }

    public DnsRecordIpAddressResolver() {
        this("localhost");
    }

    public DnsRecordIpAddressResolver(String hostname) {
        this(new Address(hostname), false);
    }

    public DnsRecordIpAddressResolver(Address address) {
        this(address, false);
    }

    public DnsRecordIpAddressResolver(Address address, boolean ssl) {
        this.address = address;
        this.ssl = ssl;
    }

    @Override
    public List<Address> getAddresses() throws IOException {
        String hostName = address.getHost();
        int portNumber = ConnectionFactory.portOrDefault(address.getPort(), ssl);
        InetAddress[] inetAddresses;
        try {
            inetAddresses = resolveIpAddresses(hostName);
        } catch (UnknownHostException e) {
            throw new IOException("Could not resolve IP addresses for host "+hostName, e);
        }
        List<Address> addresses = new ArrayList<Address>();
        for (InetAddress inetAddress : inetAddresses) {
            addresses.add(new Address(inetAddress.getHostAddress(), portNumber));
        }
        return addresses;
    }

    protected InetAddress[] resolveIpAddresses(String hostName) throws UnknownHostException {
        return InetAddress.getAllByName(hostName);
    }

}
