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

import java.io.DataInputStream;
import java.io.IOException;

/**
 * An object providing access to a LongString.
 * This might be implemented to read directly from connection
 * socket, depending on the size of the content to be read -
 * long strings may contain up to 4Gb of content.
 */
public interface LongString
{
    public static final long MAX_LENGTH = 0xffffffffL;

    /**
     * @return the length of the {@link LongString} in bytes >= 0 <= MAX_LENGTH
     */
    public long length();

    /**
     * Get the content stream.
     * Repeated calls to this function return the same stream,
     * which may not support rewind.
     * @return An input stream that reads the content of the {@link LongString}
     * @throws IOException if an error is encountered
     */
    public DataInputStream getStream() throws IOException;

    /**
     * Get the content as a byte array.  This need not be a copy. Updates to the
     * returned array may change the value of the {@link LongString}.
     * Repeated calls to this function may return the same array.
     * This function will fail if <code><b>this</b>.length() > Integer.MAX_VALUE</code>,
     * throwing an {@link IllegalStateException}.
     * @return the array of bytes containing the content of the {@link LongString}
     */
    public byte [] getBytes();
}
