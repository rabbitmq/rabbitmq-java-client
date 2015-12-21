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
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//


package com.rabbitmq.client;

/**
 * Thrown when application tries to perform an action on connection/channel
 * which was already closed
 */
public class AlreadyClosedException extends ShutdownSignalException {
    /** Default for suppressing warnings without version check. */
    private static final long serialVersionUID = 1L;

    public AlreadyClosedException(ShutdownSignalException sse) {
        this(sse, null);
    }

    public AlreadyClosedException(ShutdownSignalException sse, Throwable cause) {
        super(sse.isHardError(),
              sse.isInitiatedByApplication(),
              sse.getReason(),
              sse.getReference(),
              composeMessagePrefix(sse),
              ((cause == null) ? sse.getCause() : cause));
    }

    private static String composeMessagePrefix(ShutdownSignalException sse) {
        String connectionOrChannel = sse.isHardError() ? "connection " : "channel ";
        return connectionOrChannel + "is already closed due to ";
    }
}
