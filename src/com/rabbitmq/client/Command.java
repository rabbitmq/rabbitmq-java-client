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

import com.rabbitmq.client.impl.Method;


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
