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
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MalformedFrameException;

/**
 * Represents an AMQP wire-protocol frame, with frame type, channel number, and payload bytes.
 */
public class Frame {
    /**
     * Frame type code
     */
    public int type;

    /** Frame channel number, 0-65535 */
    public int channel;

    /** Frame payload bytes (for inbound frames) */
    public byte[] payload;

    /** Frame payload (for outbound frames) */
    public ByteArrayOutputStream accumulator;

    /**
     * Constructs an uninitialized frame.
     */
    public Frame() {
        // No work to do
    }

    /**
     * Constructs a frame for output with a type and a channel number and a
     * fresh accumulator waiting for payload.
     */
    public Frame(int type, int channel) {
        this.type = type;
        this.channel = channel;
        this.payload = null;
        this.accumulator = new ByteArrayOutputStream();
    }

    /**
     * Constructs a frame for input with a type, a channel number and a
     * payload byte array.
     */
    public Frame(int type, int channel, byte[] payload) {
        this.type = type;
        this.channel = channel;
        this.payload = payload;
        this.accumulator = null;
    }

    public static Frame fromBodyFragment(int channelNumber, byte[] body, int offset, int length)
        throws IOException
    {
        Frame frame = new Frame(AMQP.FRAME_BODY, channelNumber);
        DataOutputStream bodyOut = frame.getOutputStream();
        bodyOut.write(body, offset, length);
        return frame;
    }

    /**
     * Protected API - Factory method to instantiate a Frame by reading an
     * AMQP-wire-protocol frame from the given input stream.
     *
     * @return a new Frame if we read a frame successfully, otherwise null
     */
    public static Frame readFrom(DataInputStream is) throws IOException {
        int type;
        int channel;

        try {
            type = is.readUnsignedByte();
        } catch (SocketTimeoutException ste) {
            // System.err.println("Timed out waiting for a frame.");
            return null; // failed
        }

        if (type == 'A') {
            /*
             * Probably an AMQP.... header indicating a version
             * mismatch.
             */
            /*
             * Otherwise meaningless, so try to read the version,
             * and throw an exception, whether we read the version
             * okay or not.
             */
            protocolVersionMismatch(is);
        }

        channel = is.readUnsignedShort();
        int payloadSize = is.readInt();
        byte[] payload = new byte[payloadSize];
        is.readFully(payload);

        int frameEndMarker = is.readUnsignedByte();
        if (frameEndMarker != AMQP.FRAME_END) {
            throw new MalformedFrameException("Bad frame end marker: " + frameEndMarker);
        }

        return new Frame(type, channel, payload);
    }

    /**
     * Private API - A protocol version mismatch is detected by checking the
     * three next bytes if a frame type of (int)'A' is read from an input
     * stream. If the next three bytes are 'M', 'Q' and 'P', then it's
     * likely the broker is trying to tell us we are speaking the wrong AMQP
     * protocol version.
     *
     * @throws MalformedFrameException
     *                 if an AMQP protocol version mismatch is detected
     * @throws MalformedFrameException
     *                 if a corrupt AMQP protocol identifier is read
     */
    public static void protocolVersionMismatch(DataInputStream is) throws IOException {
        MalformedFrameException x;

        // We expect the letters M, Q, P in that order: generate an informative error if they're not found
        byte[] expectedBytes = new byte[] { 'M', 'Q', 'P' };
        for (byte expectedByte : expectedBytes) {
            int nextByte = is.readUnsignedByte();
            if (nextByte != expectedByte) {
                throw new MalformedFrameException("Invalid AMQP protocol header from server: expected character " +
                    expectedByte + ", got " + nextByte);
            }
        }

        try {
            int transportHigh = is.readUnsignedByte();
            int transportLow = is.readUnsignedByte();
            int serverMajor = is.readUnsignedByte();
            int serverMinor = is.readUnsignedByte();
            x = new MalformedFrameException("AMQP protocol version mismatch; we are version " + AMQP.PROTOCOL.MAJOR + "-" + AMQP.PROTOCOL.MINOR
                    + ", server is " + serverMajor + "-" + serverMinor + " with transport " + transportHigh + "." + transportLow);
        } catch (IOException ex) {
            x = new MalformedFrameException("Invalid AMQP protocol header from server");
        }
        throw x;
    }

    /**
     * Public API - writes this Frame to the given DataOutputStream
     */
    public void writeTo(DataOutputStream os) throws IOException {
        os.writeByte(type);
        os.writeShort(channel);
        if (accumulator != null) {
            os.writeInt(accumulator.size());
            accumulator.writeTo(os);
        } else {
            os.writeInt(payload.length);
            os.write(payload);
        }
        os.write(AMQP.FRAME_END);
    }

    /**
     * Public API - retrieves the frame payload
     */
    public byte[] getPayload() {
        byte[] bytes;

        if (payload == null) {
            // This is a Frame we've constructed ourselves. For some reason (e.g.
            // testing), we're acting as if we received it even though it
            // didn't come in off the wire.
            bytes = accumulator.toByteArray();
        } else {
            bytes = payload;
        }

        return bytes;
    }

    /**
     * Public API - retrieves a new DataInputStream streaming over the payload
     */
    public DataInputStream getInputStream() {
        return new DataInputStream(new ByteArrayInputStream(getPayload()));
    }

    /**
     * Public API - retrieves a fresh DataOutputStream streaming into the accumulator
     */
    public DataOutputStream getOutputStream() {
        return new DataOutputStream(accumulator);
    }

    @Override public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Frame(" + type + ", " + channel + ", ");
        if (accumulator == null) {
            sb.append(payload.length + " bytes of payload");
        } else {
            sb.append(accumulator.size() + " bytes of accumulator");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Utility for constructing a java.util.Map instance from an
     * even-length array containing alternating String keys (on the
     * even elements, starting at zero) and values (on the odd
     * elements, starting at one).
     */
    public static Map<String, Object> buildTable(Object[] keysValues) {
        Map<String, Object> result = new HashMap<String, Object>();
        for (int index = 0; index < keysValues.length; index += 2) {
            String key = (String) keysValues[index];
            Object value = keysValues[index + 1];
            result.put(key, value);
        }
        return result;
    }

    /** Computes the AMQP wire-protocol length of a protocol-encoded table. */
    public static long tableSize(Map<String, Object> table)
        throws UnsupportedEncodingException
    {
        long acc = 0;
        for(Map.Entry<String, Object> entry: table.entrySet()) {
            acc += shortStrSize(entry.getKey());
            acc += 1;
            Object value = entry.getValue();
            if(value instanceof String) {
                acc += longStrSize((String)entry.getValue());
            }
            else if(value instanceof LongString) {
                acc += 4;
                acc += ((LongString)value).length();
            }
            else if(value instanceof Integer) {
                acc += 4;
            }
            else if(value instanceof BigDecimal) {
                acc += 5;
            }
            else if(value instanceof Date || value instanceof Timestamp) {
                acc += 8;
            }
            else if(value instanceof Map) {
                acc += 4;
                acc += tableSize((Map<String, Object>) value);
            }
            else {
                throw new IllegalArgumentException("invalid value in table");
            }
        }

        return acc;
    }

    /** Computes the AMQP wire-protocol length of a protocol-encoded long string. */
    public static int longStrSize(String str)
        throws UnsupportedEncodingException
    {
        return str.getBytes("utf-8").length + 4;
    }

    /** Computes the AMQP wire-protocol length of a protocol-encoded short string. */
    public static int shortStrSize(String str)
        throws UnsupportedEncodingException
    {
        return str.getBytes("utf-8").length + 1;
    }
}
