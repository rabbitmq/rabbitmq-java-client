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


package com.rabbitmq.client.impl;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import com.rabbitmq.client.LongString;

/**
 * Parses AMQP wire-protocol {@link Method} arguments from a
 * DataInputStream. Methods on this object are usually called from
 * generated code.
 */
public class MethodArgumentReader
{
    /** The stream we are reading from. */
    private final ValueReader in;
    /** If we are reading one or more bits, holds the current packed collection of bits */
    private int bits;
    /** If we are reading one or more bits, keeps track of which bit position we will read from next.
     * (reading least to most significant order) */
    private int nextBitMask;

    /**
     * Resets the bit group accumulator variables when
     * some non-bit argument value is to be read.
     */
    private void clearBits()
    {
        bits = 0;
        nextBitMask = 0x100; // triggers readOctet first time
    }

    /**
     * Construct a MethodArgumentReader from the given {@link ValueReader}.
     */
    public MethodArgumentReader(ValueReader in)
    {
        this.in = in;
        clearBits();
    }

    /** Public API - reads a short string argument. */
    public final String readShortstr()
        throws IOException
    {
        clearBits();
        return in.readShortstr();
    }

    /** Public API - reads a long string argument. */
    public final LongString readLongstr()
        throws IOException
    {
        clearBits();
        return in.readLongstr();
    }

    /** Public API - reads a short integer argument. */
    public final int readShort()
        throws IOException
    {
        clearBits();
        return in.readShort();
    }

    /** Public API - reads an integer argument. */
    public final int readLong()
        throws IOException
    {
        clearBits();
        return in.readLong();
    }

    /** Public API - reads a long integer argument. */
    public final long readLonglong()
        throws IOException
    {
        clearBits();
        return in.readLonglong();
    }

    /** Public API - reads a bit/boolean argument. */
    public final boolean readBit()
        throws IOException
    {
        if (nextBitMask > 0x80) {
            bits = in.readOctet();
            nextBitMask = 0x01;
        }

        boolean result = (bits&nextBitMask) != 0;
        nextBitMask = nextBitMask << 1;
        return result;
    }

    /** Public API - reads a table argument. */
    public final Map<String, Object> readTable()
        throws IOException
    {
        clearBits();
        return in.readTable();
    }

    /** Public API - reads an octet argument. */
    public final int readOctet()
        throws IOException
    {
        clearBits();
        return in.readOctet();
    }

    /** Public API - reads an timestamp argument. */
    public final Date readTimestamp()
        throws IOException
    {
        clearBits();
        return in.readTimestamp();
    }
}
