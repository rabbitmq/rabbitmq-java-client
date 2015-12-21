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


package com.rabbitmq.client.impl;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import com.rabbitmq.client.LongString;

/**
 * Utility for working with {@link LongString}s.
 */
public class LongStringHelper
{
    /**
     * Private API - Implementation of {@link LongString}. When
     * interpreting bytes as a string, uses UTF-8 encoding.
     */
    private static class ByteArrayLongString
        implements LongString
    {
        private final byte [] bytes;

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

        /** {@inheritDoc} */
        public byte[] getBytes()
        {
            return bytes;
        }

        /** {@inheritDoc} */
        public DataInputStream getStream()
            throws IOException
        {
            return new DataInputStream(new ByteArrayInputStream(bytes));
        }

        /** {@inheritDoc} */
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
        if (string==null) return null;
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
        if (bytes==null) return null;
        return new ByteArrayLongString(bytes);
    }
}
