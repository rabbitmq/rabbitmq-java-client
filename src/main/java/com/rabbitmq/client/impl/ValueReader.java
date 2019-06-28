// Copyright (c) 2007-2019 Pivotal Software, Inc.  All rights reserved.
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

import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.rabbitmq.client.LongString;
import com.rabbitmq.client.MalformedFrameException;

/**
 * Helper class to read AMQP wire-protocol encoded values.
 */
public class ValueReader
{
    private static final long INT_MASK = 0xffffffffL;

    /**
     * Protected API - Cast an int to a long without extending the
     * sign bit of the int out into the high half of the long.
     */
    private static long unsignedExtend(int value)
    {
        long extended = value;
        return extended & INT_MASK;
    }

    /** The stream we are reading from. */
    private final DataInputStream in;

    /**
     * Construct a MethodArgumentReader streaming over the given DataInputStream.
     */
    public ValueReader(DataInputStream in)
    {
        this.in = in;
    }

    /** Convenience method - reads a short string from a DataInput
     * Stream.
     */
    private static String readShortstr(DataInputStream in)
        throws IOException
    {
        byte [] b = new byte[in.readUnsignedByte()];
        in.readFully(b);
        return new String(b, "utf-8");
    }

    /** Public API - reads a short string. */
    public final String readShortstr()
        throws IOException
    {
        return readShortstr(this.in);
    }

    /** Convenience method - reads a 32-bit-length-prefix
     * byte vector from a DataInputStream.
     */
    private static byte[] readBytes(final DataInputStream in)
        throws IOException
    {
        final long contentLength = unsignedExtend(in.readInt());
        if(contentLength < Integer.MAX_VALUE) {
            final byte [] buffer = new byte[(int)contentLength];
            in.readFully(buffer);
            return buffer;
        } else {
            throw new UnsupportedOperationException
                ("Very long byte vectors and strings not currently supported");
        }
    }

    /** Convenience method - reads a long string argument
     * from a DataInputStream.
     */
    private static LongString readLongstr(final DataInputStream in)
        throws IOException
    {
        return LongStringHelper.asLongString(readBytes(in));
    }


    /** Public API - reads a long string. */
    public final LongString readLongstr()
        throws IOException
    {
        return readLongstr(this.in);
    }

    /** Public API - reads a short integer. */
    public final int readShort()
        throws IOException
    {
        return in.readUnsignedShort();
    }

    /** Public API - reads an integer. */
    public final int readLong()
        throws IOException
    {
        return in.readInt();
    }

    /** Public API - reads a long integer. */
    public final long readLonglong()
        throws IOException
    {
        return in.readLong();
    }

    /**
     * Reads a table argument from a given stream. Also
     * called by {@link ContentHeaderPropertyReader}.
     */
    private static Map<String, Object> readTable(DataInputStream in)
        throws IOException
    {
        long tableLength = unsignedExtend(in.readInt());
        if (tableLength == 0) return Collections.emptyMap();
        
        Map<String, Object> table = new HashMap<String, Object>();
        DataInputStream tableIn = new DataInputStream
            (new TruncatedInputStream(in, tableLength));
        while(tableIn.available() > 0) {
            String name = readShortstr(tableIn);
            Object value = readFieldValue(tableIn);
            if(!table.containsKey(name))
                table.put(name, value);
        }
        return table;
    }

    // package protected for testing
    static Object readFieldValue(DataInputStream in)
        throws IOException {
        Object value = null;
        switch(in.readUnsignedByte()) {
          case 'S':
              value = readLongstr(in);
              break;
          case 'I':
              value = in.readInt();
              break;
          case 'D':
              int scale = in.readUnsignedByte();
              byte [] unscaled = new byte[4];
              in.readFully(unscaled);
              value = new BigDecimal(new BigInteger(unscaled), scale);
              break;
          case 'T':
              value = readTimestamp(in);
              break;
          case 'F':
              value = readTable(in);
              break;
          case 'A':
              value = readArray(in);
              break;
          case 'b':
              value = in.readByte();
              break;
          case 'd':
              value = in.readDouble();
              break;
          case 'f':
              value = in.readFloat();
              break;
          case 'l':
              value = in.readLong();
              break;
          case 's':
              value = in.readShort();
              break;
          case 't':
              value = in.readBoolean();
              break;
          case 'x':
              value = readBytes(in);
              break;
          case 'V':
              value = null;
              break;
          default:
              throw new MalformedFrameException
                  ("Unrecognised type in table");
        }
        return value;
    }

    /** Read a field-array */
    private static List<Object> readArray(DataInputStream in)
        throws IOException
    {
        long length = unsignedExtend(in.readInt());
        DataInputStream arrayIn = new DataInputStream
            (new TruncatedInputStream(in, length));
        List<Object> array = new ArrayList<Object>();
        while(arrayIn.available() > 0) {
            Object value = readFieldValue(arrayIn);
            array.add(value);
        }
        return array;
    }

    /** Public API - reads a table. */
    public final Map<String, Object> readTable()
        throws IOException
    {
        return readTable(this.in);
    }

    /** Public API - reads an octet. */
    public final int readOctet()
        throws IOException
    {
        return in.readUnsignedByte();
    }

    /** Convenience method - reads a timestamp argument from the DataInputStream. */
    private static Date readTimestamp(DataInputStream in)
        throws IOException
    {
        return new Date(in.readLong()*1000);
    }


    /** Public API - reads an timestamp. */
    public final Date readTimestamp()
        throws IOException
    {
        return readTimestamp(this.in);
    }

}
