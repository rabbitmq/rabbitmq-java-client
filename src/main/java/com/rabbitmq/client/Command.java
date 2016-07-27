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
 * Interface to a container for an AMQP method-and-arguments, with optional content header and body.
 */
public interface Command {
    /**
     * Retrieves the {@link Method} held within this Command. Downcast to
     * concrete (implementation-specific!) subclasses as necessary.
     *
     * @return the command's method.
     */
    Method getMethod();

    /**
     * Retrieves the ContentHeader subclass instance held as part of this Command, if any.
     *
     * Downcast to one of the inner classes of AMQP,
     * for instance {@link AMQP.BasicProperties}, as appropriate.
     *
     * @return the Command's {@link ContentHeader}, or null if none
     */
    ContentHeader getContentHeader();

    /**
     * Retrieves the body byte array that travelled as part of this
     * Command, if any.
     *
     * @return the Command's content body, or null if none
     */
    byte[] getContentBody();
}
