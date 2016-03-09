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


package com.rabbitmq.client.impl;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.Map;
import java.util.List;

import com.rabbitmq.client.LongString;

/**
 * Helper class to generate AMQP wire-protocol encoded values.
 */
public class ValueWriter
{
    private final DataOutputStream out;

    public ValueWriter(DataOutputStream out)
    {
        this.out = out;
    }

    /** Public API - encodes a short string. */
    public final void writeShortstr(String str)
        throws IOException
    {
        byte [] bytes = str.getBytes("utf-8");
        int length = bytes.length;
        if (length > 255) {
            throw new IllegalArgumentException(
                    "Short string too long; utf-8 encoded length = " + length +
                    ", max = 255.");
        }
        out.writeByte(bytes.length);
        out.write(bytes);
    }

    /** Public API - encodes a long string from a LongString. */
    public final void writeLongstr(LongString str)
        throws IOException
    {
        writeLong((int)str.length());
        copy(str.getStream(), out);
    }

    private static final int COPY_BUFFER_SIZE = 4096;

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int biteSize = input.read(buffer);
        while (-1 != biteSize) {
            output.write(buffer, 0, biteSize);
            biteSize = input.read(buffer);
        }
    }

    /** Public API - encodes a long string from a String. */
    public final void writeLongstr(String str)
        throws IOException
    {
        byte [] bytes = str.getBytes("utf-8");
        writeLong(bytes.length);
        out.write(bytes);
    }

    /** Public API - encodes a short integer. */
    public final void writeShort(int s)
        throws IOException
    {
        out.writeShort(s);
    }

    /** Public API - encodes an integer. */
    public final void writeLong(int l)
        throws IOException
    {
        // java's arithmetic on this type is signed, however it's
        // reasonable to use ints to represent the unsigned long
        // type - for values < Integer.MAX_VALUE everything works
        // as expected
        out.writeInt(l);
    }

    /** Public API - encodes a long integer. */
    public final void writeLonglong(long ll)
        throws IOException
    {
        out.writeLong(ll);
    }

    /** Public API - encodes a table. */
    public final void writeTable(Map<String, Object> table)
        throws IOException
    {
        if (table == null) {
            // Convenience.
            out.writeInt(0);
        } else {
            out.writeInt((int)Frame.tableSize(table));
            for(Map.Entry<String,Object> entry: table.entrySet()) {
                writeShortstr(entry.getKey());
                Object value = entry.getValue();
                writeFieldValue(value);
            }
        }
    }

    public final void writeFieldValue(Object value)
        throws IOException
    {
        if(value instanceof String) {
            writeOctet('S');
            writeLongstr((String)value);
        }
        else if(value instanceof LongString) {
            writeOctet('S');
            writeLongstr((LongString)value);
        }
        else if(value instanceof Integer) {
            writeOctet('I');
            writeLong((Integer) value);
        }
        else if(value instanceof BigDecimal) {
            writeOctet('D');
            BigDecimal decimal = (BigDecimal)value;
            writeOctet(decimal.scale());
            BigInteger unscaled = decimal.unscaledValue();
            if(unscaled.bitLength() > 32) /*Integer.SIZE in Java 1.5*/
                throw new IllegalArgumentException
                    ("BigDecimal too large to be encoded");
            writeLong(decimal.unscaledValue().intValue());
        }
        else if(value instanceof Date) {
            writeOctet('T');
            writeTimestamp((Date)value);
        }
        else if(value instanceof Map) {
            writeOctet('F');
            // Ignore the warnings here.  We hate erasure
            // (not even a little respect)
            // (We even have trouble recognising it.)
            @SuppressWarnings("unchecked")
            Map<String,Object> map = (Map<String,Object>) value;
            writeTable(map);
        }
        else if (value instanceof Byte) {
            writeOctet('b');
            out.writeByte((Byte)value);
        }
        else if(value instanceof Double) {
            writeOctet('d');
            out.writeDouble((Double)value);
        }
        else if(value instanceof Float) {
            writeOctet('f');
            out.writeFloat((Float)value);
        }
        else if(value instanceof Long) {
            writeOctet('l');
            out.writeLong((Long)value);
        }
        else if(value instanceof Short) {
            writeOctet('s');
            out.writeShort((Short)value);
        }
        else if(value instanceof Boolean) {
            writeOctet('t');
            out.writeBoolean((Boolean)value);
        }
        else if(value instanceof byte[]) {
            writeOctet('x');
            writeLong(((byte[])value).length);
            out.write((byte[])value);
        }
        else if(value == null) {
            writeOctet('V');
        }
        else if(value instanceof List) {
            writeOctet('A');
            writeArray((List<?>)value);
        }
        else if(value instanceof Object[]) {
            writeOctet('A');
            writeArray((Object[])value);
        }
        else {
            throw new IllegalArgumentException
                ("Invalid value type: " + value.getClass().getName());
        }
    }

    public final void writeArray(List<?> value)
        throws IOException
    {
        if (value==null) {
            out.write(0);
        }
        else {
            out.writeInt((int)Frame.arraySize(value));
            for (Object item : value) {
                writeFieldValue(item);
            }
        }
    }

    public final void writeArray(Object[] value)
        throws IOException
    {
        if (value==null) {
            out.write(0);
        }
        else {
            out.writeInt((int)Frame.arraySize(value));
            for (Object item : value) {
                writeFieldValue(item);
            }
        }
    }

    /** Public API - encodes an octet from an int. */
    public final void writeOctet(int octet)
        throws IOException
    {
        out.writeByte(octet);
    }

    /** Public API - encodes an octet from a byte. */
    public final void writeOctet(byte octet)
        throws IOException
    {
        out.writeByte(octet);
    }

    /** Public API - encodes a timestamp. */
    public final void writeTimestamp(Date timestamp)
        throws IOException
    {
        // AMQP uses POSIX time_t which is in seconds since the epoch began
        writeLonglong(timestamp.getTime()/1000);
    }

    /**
     * Public API - call this to ensure all accumulated
     * values are correctly written to the output stream.
     */
    public void flush()
        throws IOException
    {
        out.flush();
    }
}
