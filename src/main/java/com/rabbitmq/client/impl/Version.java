//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2016 Pivotal Software, Inc.  All rights reserved.
//


package com.rabbitmq.client.impl;

/**
 * Encapsulation of AMQP protocol version
 */
public class Version {

    private final int _major;
    private final int _minor;

    /**
     * Creates a new <code>Version</code> instance.
     *
     * @param major the AMQP major version number
     * @param minor the AMQP minor version number
     */
    public Version(int major, int minor) {
        _major = major;
        _minor = minor;
    }

    /**
     * Retrieve the major version number.
     * @return the major version number
     */
    public int getMajor() {
        return _major;
    }

    /**
     * Retrieve the minor version number.
     * @return the minor version number
     */
    public int getMinor() {
        return _minor;
    }

    /**
     * Retrieve a String representation of the version in the standard
     * AMQP version format of <major>-<minor>
     *
     * @return a <code>String</code> representation of the version
     * @see Object#toString()
     */
    @Override public String toString() {
        return "" + getMajor() + "-" + getMinor();
    }

    @Override public boolean equals(Object o) {
        if(o instanceof Version) {
            Version other = (Version)o;
            return
                this.getMajor() == other.getMajor() &&
                this.getMinor() == other.getMinor();
        } else {
            return false;
        }
    }

    @Override public int hashCode() {
        return 31 * getMajor()+ getMinor();
    }

    /**
     * Adjust a version for spec weirdness.
     *
     * The AMQP 0-8 spec confusingly defines the version as 8-0. This
     * method maps the latter to the former.
     *
     * @return the adjusted <code>Version</code>
     */
    public Version adjust() {
        return (getMajor() == 8 && getMinor() == 0) ?
            new Version(0, 8) : this;
    }

    /**
     * Check compatibility of a client and server version, from the
     * client's perspective.
     *
     * @param clientVersion the client <code>Version</code>
     * @param serverVersion the server <code>Version</code>
     * @return a <code>boolean</code> value
     */
    public static boolean checkVersion(Version clientVersion,
                                       Version serverVersion) {

        return clientVersion.adjust().equals(serverVersion.adjust());
    }

}
