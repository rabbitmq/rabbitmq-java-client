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


package com.rabbitmq.client;

import java.io.IOException;

/**
 * Encapsulates a frame format error at the wire level.
 */
public class MalformedFrameException extends IOException {
    /** Standard serialization ID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiate a MalformedFrameException.
     * @param reason a string describing the exception
     */
    public MalformedFrameException(String reason) {
        super(reason);
    }
}
