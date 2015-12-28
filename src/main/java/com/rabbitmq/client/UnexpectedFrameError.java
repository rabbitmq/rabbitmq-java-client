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

import com.rabbitmq.client.impl.Frame;

/**
 * Thrown when the command parser hits an unexpected frame type.
 */
public class UnexpectedFrameError extends Error {
    private static final long serialVersionUID = 1L;
    private final Frame _frame;
    private final int _expectedFrameType;

    public UnexpectedFrameError(Frame frame, int expectedFrameType) {
        super("Received frame: " + frame + ", expected type " + expectedFrameType);
        _frame = frame;
        _expectedFrameType = expectedFrameType;
    }

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public Frame getReceivedFrame() {
        return _frame;
    }

    public int getExpectedFrameType() {
        return _expectedFrameType;
    }
}
