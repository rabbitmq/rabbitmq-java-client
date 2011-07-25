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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.impl;

import java.io.ByteArrayOutputStream;
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

    /** EMPTY_CONTENT_BODY_FRAME_SIZE = 8 = 1 + 2 + 4 + 1
     * <ul><li>1 byte of frame type</li>
     * <li>2 bytes of channel number</li>
     * <li>4 bytes of frame payload length</li>
     * <li>1 byte of payload trailer FRAME_END byte</li></ul>
     * See {@link #checkEmptyContentBodyFrameSize}, an assertion
     * called at startup.
     */
    private static final int EMPTY_CONTENT_BODY_FRAME_SIZE = 8;

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
        return new AMQCommand(null, null, null).new Assembler();
    }

    /**
     * Construct a command with just a method, and without header or body.
     * @param method the wrapped method
     */
    public AMQCommand(com.rabbitmq.client.Method method) {
        this(method, null, null);
    }

    /**
     * Construct a command with a specified method, header and body.
     * @param method the wrapped method
     * @param contentHeader the wrapped content header
     * @param body the message body data
     */
    public AMQCommand(com.rabbitmq.client.Method method, AMQContentHeader contentHeader, byte[] body) {
        _method = (Method) method;
        _contentHeader = contentHeader;
        setContentBody(body);
    }

    /** Public API - {@inheritDoc} */
    public Method getMethod() {
        return _method;
    }

    /** Public API - {@inheritDoc} */
    public ContentHeader getContentHeader() {
        return _contentHeader;
    }

    /** Public API - {@inheritDoc} */
    public byte[] getContentBody() {
        if (_bodyN != null) {
            coalesceContentBody();
        }
        return _body0 == null ? EMPTY_BYTE_ARRAY : _body0;
    }

    /**
     * Private API - Set the Command's content body.
     * @param body the body data
     */
    private void setContentBody(byte[] body) {
        _body0 = body;
        _bodyN = null;
    }

    private void appendBodyFragment(byte[] fragment) {
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
    private void coalesceContentBody() {
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
     * Sends this command down the named channel on the channel's
     * connection, possibly in multiple frames.
     * @param channel the channel on which to transmit the command
     * @throws IOException if an error is encountered
     */
    public void transmit(AMQChannel channel) throws IOException {
        int channelNumber = channel.getChannelNumber();
        AMQConnection connection = channel.getConnection();

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
        return toString(false);
    }

    public String toString(boolean suppressBody){
        byte[] body = getContentBody();
        String contentStr;
        try {
            contentStr = suppressBody ? (body.length + " bytes of payload") :
                ("\"" + new String(body) + "\"");
        } catch (Exception e) {
            contentStr = "|" + body.length + "|";
        }
        return "{" + _method + "," + _contentHeader + "," + contentStr + "}";
    }

    /** Called to check internal code assumptions. */
    public static void checkPreconditions() {
        checkEmptyContentBodyFrameSize();
    }

    /**
     * Since we're using a pre-computed value for
     * EMPTY_CONTENT_BODY_FRAME_SIZE we check this is
     * actually correct when run against the framing code in Frame.
     */
    private static void checkEmptyContentBodyFrameSize() {
        Frame f = new Frame(AMQP.FRAME_BODY, 0, new byte[0]);
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        try {
            f.writeTo(new DataOutputStream(s));
        } catch (IOException ioe) {
            throw new AssertionError("IOException while checking EMPTY_CONTENT_BODY_FRAME_SIZE");
        }
        int actualLength = s.toByteArray().length;
        if (EMPTY_CONTENT_BODY_FRAME_SIZE != actualLength) {
            throw new AssertionError("Internal error: expected EMPTY_CONTENT_BODY_FRAME_SIZE("
                    + EMPTY_CONTENT_BODY_FRAME_SIZE
                    + ") is not equal to computed value: " + actualLength);
        }
    }

    public class Assembler {
        private static final int STATE_EXPECTING_METHOD = 0;
        private static final int STATE_EXPECTING_CONTENT_HEADER = 1;
        private static final int STATE_EXPECTING_CONTENT_BODY = 2;
        private static final int STATE_COMPLETE = 3;

        /** Current state, used to decide how to handle each incoming frame. */
        private int state;

        /**
         * How many more bytes of content body are expected to arrive
         * from the broker.
         */
        private long remainingBodyBytes;

        private Assembler() {
            this.state = STATE_EXPECTING_METHOD;
            this.remainingBodyBytes = 0;
        }

        /**
         * Used to decide when an incoming command is ready for processing
         * @return the completed command, if appropriate
         */
        private AMQCommand completedCommand() {
            return (this.state == STATE_COMPLETE) ? AMQCommand.this : null;
        }

        /** Decides whether more body frames are expected */
        private void updateContentBodyState() {
            this.state = (this.remainingBodyBytes > 0) ? STATE_EXPECTING_CONTENT_BODY : STATE_COMPLETE;
        }

        private AMQCommand consumeMethodFrame(Frame f) throws IOException {
            if (f.type == AMQP.FRAME_METHOD) {
                _method = AMQImpl.readMethodFrom(f.getInputStream());
                state = _method.hasContent() ? STATE_EXPECTING_CONTENT_HEADER : STATE_COMPLETE;
                return completedCommand();
            } else {
                throw new UnexpectedFrameError(f, AMQP.FRAME_METHOD);
            }
        }

        private AMQCommand consumeHeaderFrame(Frame f) throws IOException {
            if (f.type == AMQP.FRAME_HEADER) {
                _contentHeader = AMQImpl.readContentHeaderFrom(f.getInputStream());
                this.remainingBodyBytes = _contentHeader.getBodySize();
                updateContentBodyState();
                return completedCommand();
            } else {
                throw new UnexpectedFrameError(f, AMQP.FRAME_HEADER);
            }
        }

        private AMQCommand consumeBodyFrame(Frame f) {
            if (f.type == AMQP.FRAME_BODY) {
                byte[] fragment = f.getPayload();
                this.remainingBodyBytes -= fragment.length;
                updateContentBodyState();
                if (this.remainingBodyBytes < 0) {
                    throw new UnsupportedOperationException("%%%%%% FIXME unimplemented");
                }
                appendBodyFragment(fragment);
                return completedCommand();
            } else {
                throw new UnexpectedFrameError(f, AMQP.FRAME_BODY);
            }
        }

        public AMQCommand handleFrame(Frame f) throws IOException
        {
            switch (this.state) {
              case STATE_EXPECTING_METHOD:
                  return consumeMethodFrame(f);

              case STATE_EXPECTING_CONTENT_HEADER:
                  return consumeHeaderFrame(f);

              case STATE_EXPECTING_CONTENT_BODY:
                  return consumeBodyFrame(f);

              default:
                  throw new AssertionError("Bad Command State " + this.state);
            }
        }
    }
}
