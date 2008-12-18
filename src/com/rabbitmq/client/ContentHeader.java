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
 * Public API for abstract AMQP content header objects.
 */

public interface ContentHeader {
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
    public void appendPropertyDebugStringTo(StringBuffer buffer);
}
