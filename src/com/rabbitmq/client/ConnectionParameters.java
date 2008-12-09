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

import java.security.AccessControlException;

/**
 * Properties bean to encapsulate parameters for a Connection.
 */
public class ConnectionParameters {

    private static String safeGetProperty(String key, String def) {
        try {
            return System.getProperty(key, def);
        } catch (AccessControlException ex) {
            return def;
        }
    }

    /** Default user name */
    public static final String DEFAULT_USER = "guest";

    /** Default password */
    public static final String DEFAULT_PASS = "guest";

    /** Default virtual host */
    public static final String DEFAULT_VHOST = "/";

    /** Default value for the desired maximum number of channels; zero for
     * unlimited */
    public static final int DEFAULT_CHANNEL_MAX = 0;

    /** Default value for the desired maximum frame size; zero for
     * unlimited */
    public static final int DEFAULT_FRAME_MAX = 0;

    /** Default value for desired heartbeat interval; zero for none */
    public static final int DEFAULT_HEARTBEAT = 3;

    private String _userName = DEFAULT_USER;
    private String _password = DEFAULT_PASS;
    private String _virtualHost = DEFAULT_VHOST;
    private int _requestedChannelMax = DEFAULT_CHANNEL_MAX;
    private int _requestedFrameMax = DEFAULT_FRAME_MAX;
    private int _requestedHeartbeat = DEFAULT_HEARTBEAT;

    /**
     * Instantiate a set of parameters with all values set to the defaults
     */
    public ConnectionParameters() {}

    /**
     * Retrieve the user name.
     * @return the AMQP user name to use when connecting to the broker
     */
    public String getUserName() {
        return _userName;
    }

    /**
     * Set the user name.
     * @param userName the AMQP user name to use when connecting to the broker
     */
    public void setUsername(String userName) {
        _userName = userName;
    }

    /**
     * Retrieve the password.
     * @return the password to use when connecting to the broker
     */
    public String getPassword() {
        return _password;
    }

    /**
     * Set the password.
     * @param password the password to use when connecting to the broker
     */
    public void setPassword(String password) {
        _password = password;
    }

    /**
     * Retrieve the virtual host.
     * @return the virtual host to use when connecting to the broker
     */
    public String getVirtualHost() {
        return _virtualHost;
    }

    /**
     * Set the virtual host.
     * @param virtualHost the virtual host to use when connecting to the broker
     */
    public void setVirtualHost(String virtualHost) {
        _virtualHost = virtualHost;
    }

    /**
     * Retrieve the requested maximum number of channels
     * @return the initially requested maximum number of channels; zero for unlimited
     */
    public int getRequestedChannelMax() {
        return _requestedChannelMax;
    }

    /**
     * Set the requested maximum frame size
     * @param requestedFrameMax initially requested maximum frame size, in octets; zero for unlimited
     */
    public void setRequestedFrameMax(int requestedFrameMax) {
        _requestedFrameMax = requestedFrameMax;
    }

    /**
     * Retrieve the requested maximum frame size
     * @return the initially requested maximum frame size, in octets; zero for unlimited
     */
    public int getRequestedFrameMax() {
        return _requestedFrameMax;
    }

    /**
     * Retrieve the requested heartbeat interval.
     * @return the initially requested heartbeat interval, in seconds; zero for none
     */
    public int getRequestedHeartbeat() {
        return _requestedHeartbeat;
    }

    /**
     * Set the requested heartbeat.
     * @param requestedHeartbeat the initially requested heartbeat interval, in seconds; zero for none
     */
    public void setRequestedHeartbeat(int requestedHeartbeat) {
        _requestedHeartbeat = requestedHeartbeat;
    }

    /**
     * Set the requested maximum number of channels
     * @param requestedChannelMax initially requested maximum number of channels; zero for unlimited
     */
    public void setRequestedChannelMax(int requestedChannelMax) {
        _requestedChannelMax = requestedChannelMax;
    }

}
