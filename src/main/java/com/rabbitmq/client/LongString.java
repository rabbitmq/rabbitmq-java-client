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
     * @return the length of the string in bytes between 0 and MAX_LENGTH (inclusive)
     */
    public long length();

    /**
     * Get the content stream.
     * Repeated calls to this function return the same stream,
     * which may not support rewind.
     * @return An input stream that reads the content of the string
     * @throws IOException if an error is encountered
     */
    public DataInputStream getStream() throws IOException;

    /**
     * Get the content as a byte array.  This need not be a copy. Updates to the
     * returned array may change the value of the string.
     * Repeated calls to this function may return the same array.
     * This function will fail if this string's length is greater than {@link Integer#MAX_VALUE},
     * throwing an {@link IllegalStateException}.
     * @return the array of bytes containing the content of the {@link LongString}
     */
    public byte [] getBytes();

    /**
     * Get the content as a String. Uses UTF-8 as encoding.
     * @return he content of the {@link LongString} as a string
     */
    @Override
    public String toString();
}
