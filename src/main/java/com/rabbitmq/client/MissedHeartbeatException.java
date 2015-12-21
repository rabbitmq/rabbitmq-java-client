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

import java.net.SocketTimeoutException;

/**
 * Encapsulates an exception indicating that the connection has missed too many heartbeats
 * and is being shut down.
 */

public class MissedHeartbeatException extends SocketTimeoutException {
    private static final long serialVersionUID = 1L;

    public MissedHeartbeatException(String reason) {
        super(reason);
    }
}
