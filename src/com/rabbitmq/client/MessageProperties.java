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

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.impl.AMQContentHeader;

/**
 * Constant holder class with useful static instances of {@link AMQContentHeader}.
 * These are intended for use with {@link Channel#basicPublish} and other Channel methods.
 */
public class MessageProperties {
    private static final Integer ZERO = 0;
    private static final Integer ONE = 1;
    private static final Integer TWO = 2;

    /** Empty basic properties, with no fields set */
    public static final BasicProperties MINIMAL_BASIC =
        new BasicProperties(null, null, null, null,
                            null, null, null, null,
                            null, null, null, null,
                            null, null);
    /** Empty basic properties, with only deliveryMode set to 2 (persistent) */
    public static final BasicProperties MINIMAL_PERSISTENT_BASIC =
        new BasicProperties(null, null, null, TWO,
                            null, null, null, null,
                            null, null, null, null,
                            null, null);

    /** Content-type "application/octet-stream", deliveryMode 1 (nonpersistent), priority zero */
    public static final BasicProperties BASIC =
        new BasicProperties("application/octet-stream",
                            null,
                            null,
                            ONE,
                            ZERO, null, null, null,
                            null, null, null, null,
                            null, null);

    /** Content-type "application/octet-stream", deliveryMode 2 (persistent), priority zero */
    public static final BasicProperties PERSISTENT_BASIC =
        new BasicProperties("application/octet-stream",
                            null,
                            null,
                            TWO,
                            ZERO, null, null, null,
                            null, null, null, null,
                            null, null);

    /** Content-type "text/plain", deliveryMode 1 (nonpersistent), priority zero */
    public static final BasicProperties TEXT_PLAIN =
        new BasicProperties("text/plain",
                            null,
                            null,
                            ONE,
                            ZERO, null, null, null,
                            null, null, null, null,
                            null, null);

    /** Content-type "text/plain", deliveryMode 2 (persistent), priority zero */
    public static final BasicProperties PERSISTENT_TEXT_PLAIN =
        new BasicProperties("text/plain",
                            null,
                            null,
                            TWO,
                            ZERO, null, null, null,
                            null, null, null, null,
                            null, null);
}
