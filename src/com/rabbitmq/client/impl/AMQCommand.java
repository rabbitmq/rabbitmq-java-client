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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.ContentHeader;
import com.rabbitmq.client.UnexpectedFrameError;

public class AMQCommand implements Command {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    // EMPTY_CONTENT_BODY_FRAME_SIZE, 8 = 1 + 2 + 4 + 1
    // - 1 byte of frame type
    // - 2 bytes of channel number
    // - 4 bytes of frame payload length
    // - 1 byte of payload trailer FRAME_END byte
    // See definition of checkEmptyContentBodyFrameSize(), an assertion
    // called at startup.
    /** Safety definition - see also {@link #checkEmptyContentBodyFrameSize} */
    public static final int EMPTY_CONTENT_BODY_FRAME_SIZE = 8;

    /** The method for this command */
    private Method _method;

    /** The content header for this command */
    private AMQContentHeader _contentHeader;

    /** The first (and usually only) fragment of the content body */
    private byte[] _body0;

    /** The remaining fragments of this command's content body - a list of byte[] */
    private List<byte[]> _bodyN;

    /**
     * Retrieve an Assembler assembling into a fresh AMQCommand object.
     * @return the new Assembler
     */
    public static Assembler newAssembler() {
        return new AMQCommand().getFreshAssembler();
    }

    /** Construct a command without any method, header or body. */
    public AMQCommand() {
        this(null, null, null);
    }

    /**
     * Construct a command with just a method, and without header or body.
     * @param method the wrapped method
     */
    public AMQCommand(Method method) {
        this(method, null, null);
    }

    /**
     * Construct a command with a specified method, header and body.
     * @param method the wrapped method
     * @param contentHeader the wrapped content header
     * @param body the message body data
     */
    public AMQCommand(Method method, AMQContentHeader contentHeader, byte[] body) {
        _method = method;
        _contentHeader = contentHeader;
        setContentBody(body);
    }

    /** Public API - Retrieve the Command's method. */
    public Method getMethod() {
        return _method;
    }

    /** Public API - Retrieve the Command's ContentHeader. */
    public ContentHeader getContentHeader() {
        return _contentHeader;
    }

    /** Public API - Retrieve the Command's content body. */
    public byte[] getContentBody() {
        if (_bodyN != null) {
            coalesceContentBody();
        }
        return _body0 == null ? EMPTY_BYTE_ARRAY : _body0;
    }

    /**
     * Public API - Set the Command's content body.
     * @param body the body data
     */
    public void setContentBody(byte[] body) {
        _body0 = body;
        _bodyN = null;
    }

    public void appendBodyFragment(byte[] fragment) {
        if (_body0 == null) {
            _body0 = fragment;
        } else {
            if (_bodyN == null) {
                _bodyN = new ArrayList<byte[]>();
            }
            _bodyN.add(fragment);
        }
    }

    /**
     * Private API - Stitches together a fragmented content body into a single byte array.
     */
    public void coalesceContentBody() {
        List<byte[]> oldFragments = _bodyN;
        byte[] firstFragment = _body0 == null ? EMPTY_BYTE_ARRAY : _body0;

        int totalSize = firstFragment.length;
        for (byte[] fragment : oldFragments) {
            totalSize += fragment.length;
        }

        byte[] body = new byte[totalSize];
        System.arraycopy(firstFragment, 0, body, 0, firstFragment.length);
        int offset = firstFragment.length;
        for (byte[] fragment : oldFragments) {
            System.arraycopy(fragment, 0, body, offset, fragment.length);
            offset += fragment.length;
        }

        setContentBody(body);
    }

    /**
     * Private API - retrieves a fresh Assembler that writes into this AMQCommand.
     * @return the new Assembler
     */
    public Assembler getFreshAssembler() {
        return new Assembler();
    }

    /**
     * Sends this command down the named channel on the channel's
     * connection, possibly in multiple frames.
     * @param channel the channel on which to transmit the command
     * @throws IOException if an error is encountered
     */
    public void transmit(AMQChannel channel) throws IOException {
        int channelNumber = channel.getChannelNumber();
        AMQConnection connection = channel.getAMQConnection();

        connection.writeFrame(_method.toFrame(channelNumber));

        if (this._method.hasContent()) {
            byte[] body = getContentBody();

            connection.writeFrame(_contentHeader.toFrame(channelNumber, body.length));

            int frameMax = connection.getFrameMax();
            int bodyPayloadMax = (frameMax == 0) ? body.length : frameMax - EMPTY_CONTENT_BODY_FRAME_SIZE;

            for (int offset = 0; offset < body.length; offset += bodyPayloadMax) {
                int remaining = body.length - offset;

                int fragmentLength = (remaining < bodyPayloadMax) ? remaining : bodyPayloadMax;
                Frame frame = Frame.fromBodyFragment(channelNumber, body, offset, fragmentLength);
                connection.writeFrame(frame);
            }
        }
    }

    @Override public String toString() {
        byte[] body = getContentBody();
        String contentStr;
        try {
            contentStr = "\"" + new String(body) + "\"";
        } catch (Exception e) {
            contentStr = "|" + body.length + "|";
        }
        return "{" + _method + "," + _contentHeader + "," + contentStr + "}";
    }

    /**
     * Private API - Called to check internal consistency of the
     * code. Since we're using a precomputed value for
     * EMPTY_CONTENT_BODY_FRAME_SIZE, we check here once, at
     * connection startup, that EMPTY_CONTENT_BODY_FRAME_SIZE is
     * actually correct when run against the framing code in Frame.
     */
    public static void checkEmptyContentBodyFrameSize() {
        Frame f = new Frame(AMQP.FRAME_BODY, 0, new byte[0]);
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        try {
            f.writeTo(new DataOutputStream(s));
        } catch (IOException ioe) {
            throw new AssertionError("IOException while checking EMPTY_CONTENT_BODY_FRAME_SIZE");
        }
        int actualLength = s.toByteArray().length;
        if (EMPTY_CONTENT_BODY_FRAME_SIZE != actualLength) {
            throw new AssertionError("Internal error: EMPTY_CONTENT_BODY_FRAME_SIZE is " + "incorrect - defined as " + EMPTY_CONTENT_BODY_FRAME_SIZE
                    + ", where the computed value is in fact " + actualLength);
        }
    }

    public class Assembler {
        public static final int STATE_EXPECTING_METHOD = 0;
        public static final int STATE_EXPECTING_CONTENT_HEADER = 1;
        public static final int STATE_EXPECTING_CONTENT_BODY = 2;
        public static final int STATE_COMPLETE = 3;

        /** Current state, used to decide how to handle each incoming frame. */
        public int state;

        /**
         * How many more bytes of content body are expected to arrive
         * from the broker.
         */
        public long remainingBodyBytes;

        public Assembler() {
            this.state = STATE_EXPECTING_METHOD;
            this.remainingBodyBytes = 0;
        }

        /**
         * Used to decide when an incoming command is ready for processing
         * @return the completed command, if appropriate
         */
        public AMQCommand completedCommand() {
            return (this.state == STATE_COMPLETE) ? AMQCommand.this : null;
        }

        /** Decides whether more body frames are expected */
        public void updateContentBodyState() {
            this.state = (this.remainingBodyBytes > 0) ? STATE_EXPECTING_CONTENT_BODY : STATE_COMPLETE;
        }

        public AMQCommand handleFrame(Frame f)
            throws IOException
        {
            switch (this.state) {
              case STATE_EXPECTING_METHOD:
                  switch (f.type) {
                    case AMQP.FRAME_METHOD: {
                        _method = AMQImpl.readMethodFrom(f.getInputStream());
                        state = _method.hasContent() ? STATE_EXPECTING_CONTENT_HEADER : STATE_COMPLETE;
                        return completedCommand();
                    }
                    default:
                        throw new UnexpectedFrameError(f, AMQP.FRAME_METHOD);
                  }

              case STATE_EXPECTING_CONTENT_HEADER:
                  switch (f.type) {
                    case AMQP.FRAME_HEADER: {
                        DataInputStream in = f.getInputStream();
                        _contentHeader = AMQImpl.readContentHeaderFrom(in);
                        this.remainingBodyBytes = _contentHeader.readFrom(in);
                        updateContentBodyState();
                        return completedCommand();
                    }
                    default:
                        throw new UnexpectedFrameError(f, AMQP.FRAME_HEADER);
                  }

              case STATE_EXPECTING_CONTENT_BODY:
                  switch (f.type) {
                    case AMQP.FRAME_BODY: {
                        byte[] fragment = f.getPayload();
                        this.remainingBodyBytes -= fragment.length;
                        updateContentBodyState();
                        if (this.remainingBodyBytes < 0) {
                            throw new UnsupportedOperationException("%%%%%% FIXME unimplemented");
                        }
                        appendBodyFragment(fragment);
                        return completedCommand();
                    }
                    default:
                        throw new UnexpectedFrameError(f, AMQP.FRAME_BODY);
                  }

              default:
                  throw new AssertionError("Bad Command State " + this.state);
            }
        }
    }
}
