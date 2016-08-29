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
     * AMQP version format of major-minor.
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
