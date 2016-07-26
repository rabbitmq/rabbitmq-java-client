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

/**
 * Public API for abstract AMQP content header objects.
 */

public interface ContentHeader extends Cloneable {
    /**
     * Retrieve the class ID (see the spec for a list of allowable IDs).
     * @return class ID of this ContentHeader. Properly an unsigned short, i.e. only the lowest 16 bits are significant
     */
    public abstract int getClassId();

    /**
     * Retrieve the class name, eg "basic" (see the spec for a list of these).
     * @return class name of this ContentHeader
     */
    public abstract String getClassName();

    /**
     * A debugging utility - enable properties to be appended to a string buffer for use as trace messages.
     * @param buffer a place to append the properties as a string
     */
    public void appendPropertyDebugStringTo(StringBuilder buffer);
}
