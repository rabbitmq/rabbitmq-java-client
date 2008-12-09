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

package com.rabbitmq.client.impl;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * Utility for working with {@link LongString}s.
 */
public class LongStringHelper
{
    /**
     * Private API - Implementation of {@link LongString}. When
     * interpreting bytes as a string, uses UTF-8 encoding.
     */
    public static class ByteArrayLongString
        implements LongString
    {
        byte [] bytes;

        public ByteArrayLongString(byte[] bytes)
        {
            this.bytes = bytes;
        }

        @Override public boolean equals(Object o)
        {
            if(o instanceof LongString) {
                LongString other = (LongString)o;
                return Arrays.equals(this.bytes, other.getBytes());
            }

            return false;
        }

        @Override public int hashCode()
        {
           return Arrays.hashCode(this.bytes);
        }

        public byte[] getBytes()
        {
            return bytes;
        }

        public DataInputStream getStream()
            throws IOException
        {
            return new DataInputStream(new ByteArrayInputStream(bytes));
        }

        public long length()
        {
            return bytes.length;
        }

        @Override public String toString()
        {
            try {
                return new String(bytes, "utf-8");
            }
            catch (UnsupportedEncodingException e) {
                throw new Error("utf-8 encoding support required");
            }
        }
    }

    /**
     * Converts a String to a LongString using UTF-8 encoding.
     * @param string the string to wrap
     * @return a LongString wrapping it
     */
    public static LongString asLongString(String string)
    {
        try {
            return new ByteArrayLongString(string.getBytes("utf-8"));
        }
        catch (UnsupportedEncodingException e) {
            throw new Error("utf-8 encoding support required");
        }
    }

    /**
     * Converts a binary block to a LongString.
     * @param bytes the data to wrap
     * @return a LongString wrapping it
     */
    public static LongString asLongString(byte [] bytes)
    {
        return new ByteArrayLongString(bytes);
    }
}
