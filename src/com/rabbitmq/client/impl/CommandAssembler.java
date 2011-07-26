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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.UnexpectedFrameError;

/**
 * Class responsible for piecing together a command from a series of {@link Frame}s.
 * <p/><b>Concurrency</b><br/>
 * This class is <i>not</i> thread-safe.
 * @see AMQCommand
 */
final class CommandAssembler {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /** Current state, used to decide how to handle each incoming frame. */
    private int state;
    
    private static final int STATE_EXPECTING_METHOD = 0;
    private static final int STATE_EXPECTING_CONTENT_HEADER = 1;
    private static final int STATE_EXPECTING_CONTENT_BODY = 2;
    private static final int STATE_COMPLETE = 3;

    /** The method for this command */
    private Method _method;
    
    /** The content header for this command */
    private AMQContentHeader _contentHeader;

    /** The first (and usually only) fragment of the content body */
    private byte[] _body0;
    /** The remaining fragments of this command's content body - a list of byte[] */
    private List<byte[]> _bodyN;
    /** sum of the lengths of all fragments */
    private int bodyLength;

    /**
     * How many more bytes of content body are expected to arrive
     * from the broker.
     */
    private long remainingBodyBytes;

    public CommandAssembler() {
        this(null, null, null);
    }

    public CommandAssembler(Method method, AMQContentHeader contentHeader, byte[] body) {
        this._method = method;
        this._contentHeader = contentHeader;
        setContentBody(body);
        resetState();
    }

    private void resetState() {
        this.remainingBodyBytes = 0;
        if (this._method == null) {
            this.state = STATE_EXPECTING_METHOD;
        } else if (this._contentHeader == null) {
            this.state = this._method.hasContent() ? STATE_EXPECTING_CONTENT_HEADER : STATE_COMPLETE;
        } else {
            this.remainingBodyBytes = this._contentHeader.getBodySize() - contentBodySize();
        }
    }

    private int contentBodySize() {
        return this.bodyLength;
    }

    public Method getMethod() {
        return _method;
    }

    public AMQContentHeader getContentHeader() {
        return _contentHeader;
    }

    /** @return true if the command is complete */
    public boolean isComplete() {
        return (this.state == STATE_COMPLETE);
    }

    /** Decides whether more body frames are expected */
    private void updateContentBodyState() {
        this.state = (this.remainingBodyBytes > 0) ? STATE_EXPECTING_CONTENT_BODY : STATE_COMPLETE;
    }

    private boolean consumeMethodFrame(Frame f) throws IOException {
        if (f.type == AMQP.FRAME_METHOD) {
            this._method = AMQImpl.readMethodFrom(f.getInputStream());
            state = this._method.hasContent() ? STATE_EXPECTING_CONTENT_HEADER : STATE_COMPLETE;
            return isComplete();
        } else {
            throw new UnexpectedFrameError(f, AMQP.FRAME_METHOD);
        }
    }

    private boolean consumeHeaderFrame(Frame f) throws IOException {
        if (f.type == AMQP.FRAME_HEADER) {
            this._contentHeader = AMQImpl.readContentHeaderFrom(f.getInputStream());
            this.remainingBodyBytes = this._contentHeader.getBodySize();
            updateContentBodyState();
            return isComplete();
        } else {
            throw new UnexpectedFrameError(f, AMQP.FRAME_HEADER);
        }
    }

    private boolean consumeBodyFrame(Frame f) {
        if (f.type == AMQP.FRAME_BODY) {
            byte[] fragment = f.getPayload();
            this.remainingBodyBytes -= fragment.length;
            updateContentBodyState();
            if (this.remainingBodyBytes < 0) {
                throw new UnsupportedOperationException("%%%%%% FIXME unimplemented");
            }
            appendBodyFragment(fragment);
            return isComplete();
        } else {
            throw new UnexpectedFrameError(f, AMQP.FRAME_BODY);
        }
    }

    /** Stitches together a fragmented content body into a single byte array */
    private void coalesceContentBody() {
        List<byte[]> oldFragments = _bodyN;
        byte[] firstFragment = _body0 == null ? EMPTY_BYTE_ARRAY : _body0;

        byte[] body = new byte[bodyLength];
        System.arraycopy(firstFragment, 0, body, 0, firstFragment.length);
        int offset = firstFragment.length;
        for (byte[] fragment : oldFragments) {
            System.arraycopy(fragment, 0, body, offset, fragment.length);
            offset += fragment.length;
        }

        setContentBody(body);
    }

    public byte[] getContentBody() {
        if (_bodyN != null) {
            coalesceContentBody();
        }
        return _body0 == null ? EMPTY_BYTE_ARRAY : _body0;
    }

    /**
     * Set the Command's content body.
     * @param body the body data
     */
    private void setContentBody(byte[] body) {
        _body0 = body;
        _bodyN = null;
        bodyLength = byteArrayLength(_body0);
    }

    private static int byteArrayLength(byte[] ba) {
        return (ba == null) ? 0 : ba.length;
    }

    private void appendBodyFragment(byte[] fragment) {
        if (_body0 == null) {
            _body0 = fragment;
            bodyLength = byteArrayLength(fragment);
        } else {
            if (_bodyN == null) {
                _bodyN = new ArrayList<byte[]>();
            }
            _bodyN.add(fragment);
            bodyLength += byteArrayLength(fragment);
        }
    }

    /**
     * @param f frame to be incorporated
     * @return true if command is complete
     * @throws IOException if error reading in frame
     */
    public boolean handleFrame(Frame f) throws IOException
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