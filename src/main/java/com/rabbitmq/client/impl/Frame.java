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
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import java.util.List;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.LongString;
import com.rabbitmq.client.MalformedFrameException;

/**
 * Represents an AMQP wire-protocol frame, with frame type, channel number, and payload bytes.
 * TODO: make state private
 */
public class Frame {
    /** Frame type code */
    public final int type;

    /** Frame channel number, 0-65535 */
    public final int channel;

    /** Frame payload bytes (for inbound frames) */
    private final byte[] payload;

    /** Frame payload (for outbound frames) */
    private final ByteArrayOutputStream accumulator;

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
            int[] signature = new int[4];

            for (int i = 0; i < 4; i++) {
                signature[i] = is.readUnsignedByte();
            }

            if (signature[0] == 1 &&
                signature[1] == 1 &&
                signature[2] == 8 &&
                signature[3] == 0) {
                x = new MalformedFrameException("AMQP protocol version mismatch; we are version " +
                        AMQP.PROTOCOL.MAJOR + "-" + AMQP.PROTOCOL.MINOR + "-" + AMQP.PROTOCOL.REVISION +
                        ", server is 0-8");
            }
            else {
                String sig = "";
                for (int i = 0; i < 4; i++) {
                    if (i != 0) sig += ",";
                    sig += signature[i];
                }

                x = new MalformedFrameException("AMQP protocol version mismatch; we are version " +
                        AMQP.PROTOCOL.MAJOR + "-" + AMQP.PROTOCOL.MINOR + "-" + AMQP.PROTOCOL.REVISION +
                        ", server sent signature " + sig);
            }

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
        if (payload != null) return payload;

        // This is a Frame we've constructed ourselves. For some reason (e.g.
        // testing), we're acting as if we received it even though it
        // didn't come in off the wire.
        return accumulator.toByteArray();
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
        StringBuilder sb = new StringBuilder();
        sb.append("Frame(type=").append(type).append(", channel=").append(channel).append(", ");
        if (accumulator == null) {
            sb.append(payload.length).append(" bytes of payload)");
        } else {
            sb.append(accumulator.size()).append(" bytes of accumulator)");
        }
        return sb.toString();
    }

    /** Computes the AMQP wire-protocol length of protocol-encoded table entries.
     */
    public static long tableSize(Map<String, Object> table)
        throws UnsupportedEncodingException
    {
        long acc = 0;
        for(Map.Entry<String, Object> entry: table.entrySet()) {
            acc += shortStrSize(entry.getKey());
            acc += fieldValueSize(entry.getValue());
        }
        return acc;
    }

    /** Computes the AMQP wire-protocol length of a protocol-encoded field-value. */
    private static long fieldValueSize(Object value)
        throws UnsupportedEncodingException
    {
        long acc = 1; // for the type tag
        if(value instanceof String) {
            acc += longStrSize((String)value);
        }
        else if(value instanceof LongString) {
            acc += 4 + ((LongString)value).length();
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
            @SuppressWarnings("unchecked")
            Map<String,Object> map = (Map<String,Object>) value;
            acc += 4 + tableSize(map);
        }
        else if (value instanceof Byte) {
            acc += 1;
        }
        else if(value instanceof Double) {
            acc += 8;
        }
        else if(value instanceof Float) {
            acc += 4;
        }
        else if(value instanceof Long) {
            acc += 8;
        }
        else if(value instanceof Short) {
            acc += 2;
        }
        else if(value instanceof Boolean) {
            acc += 1;
        }
        else if(value instanceof byte[]) {
            acc += 4 + ((byte[])value).length;
        }
        else if(value instanceof List) {
            acc += 4 + arraySize((List<?>)value);
        }
        else if(value instanceof Object[]) {
            acc += 4 + arraySize((Object[])value);
        }
        else if(value == null) {
        }
        else {
            throw new IllegalArgumentException("invalid value in table");
        }
        return acc;
    }

    /** Computes the AMQP wire-protocol length of an encoded field-array of type List<?> */
    public static long arraySize(List<?> values)
        throws UnsupportedEncodingException
    {
        long acc = 0;
        for (Object value : values) {
            acc += fieldValueSize(value);
        }
        return acc;
    }

    /** Computes the AMQP wire-protocol length of an encoded field-array of type Object[] */
    public static long arraySize(Object[] values) throws UnsupportedEncodingException {
        long acc = 0;
        for (Object value : values) {
            acc += fieldValueSize(value);
        }
        return acc;
    }

    /** Computes the AMQP wire-protocol length of a protocol-encoded long string. */
    private static int longStrSize(String str)
        throws UnsupportedEncodingException
    {
        return str.getBytes("utf-8").length + 4;
    }

    /** Computes the AMQP wire-protocol length of a protocol-encoded short string. */
    private static int shortStrSize(String str)
        throws UnsupportedEncodingException
    {
        return str.getBytes("utf-8").length + 1;
    }
}
