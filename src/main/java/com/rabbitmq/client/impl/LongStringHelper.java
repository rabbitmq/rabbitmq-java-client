// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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
        @Override
        public byte[] getBytes()
        {
            return bytes;
        }

        /** {@inheritDoc} */
        @Override
        public DataInputStream getStream()
            throws IOException
        {
            return new DataInputStream(new ByteArrayInputStream(bytes));
        }

        /** {@inheritDoc} */
        @Override
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
