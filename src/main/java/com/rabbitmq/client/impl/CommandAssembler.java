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
import java.util.ArrayList;
import java.util.List;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.UnexpectedFrameError;

/**
 * Class responsible for piecing together a command from a series of {@link Frame}s.
 * <p/><b>Concurrency</b><br/>
 * This class is thread-safe, since all methods are synchronised. Callers should not
 * synchronise on objects of this class unless they are sole owners.
 * @see AMQCommand
 */
final class CommandAssembler {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /** Current state, used to decide how to handle each incoming frame. */
    private enum CAState {
        EXPECTING_METHOD, EXPECTING_CONTENT_HEADER, EXPECTING_CONTENT_BODY, COMPLETE
    }
    private CAState state;

    /** The method for this command */
    private Method method;
    
    /** The content header for this command */
    private AMQContentHeader contentHeader;

    /** The fragments of this command's content body - a list of byte[] */
    private final List<byte[]> bodyN;
    /** sum of the lengths of all fragments */
    private int bodyLength;

    /** No bytes of content body not yet accumulated */
    private long remainingBodyBytes;

    public CommandAssembler(Method method, AMQContentHeader contentHeader, byte[] body) {
        this.method = method;
        this.contentHeader = contentHeader;
        this.bodyN = new ArrayList<byte[]>(2);
        this.bodyLength = 0;
        this.remainingBodyBytes = 0;
        appendBodyFragment(body);
        if (method == null) {
            this.state = CAState.EXPECTING_METHOD;
        } else if (contentHeader == null) {
            this.state = method.hasContent() ? CAState.EXPECTING_CONTENT_HEADER : CAState.COMPLETE;
        } else {
            this.remainingBodyBytes = contentHeader.getBodySize() - this.bodyLength;
            updateContentBodyState();
        }
    }

    public synchronized Method getMethod() {
        return this.method;
    }

    public synchronized AMQContentHeader getContentHeader() {
        return this.contentHeader;
    }

    /** @return true if the command is complete */
    public synchronized boolean isComplete() {
        return (this.state == CAState.COMPLETE);
    }

    /** Decides whether more body frames are expected */
    private void updateContentBodyState() {
        this.state = (this.remainingBodyBytes > 0) ? CAState.EXPECTING_CONTENT_BODY : CAState.COMPLETE;
    }

    private void consumeMethodFrame(Frame f) throws IOException {
        if (f.getType() == AMQP.FRAME_METHOD) {
            this.method = AMQImpl.readMethodFrom(f.getInputStream());
            this.state = this.method.hasContent() ? CAState.EXPECTING_CONTENT_HEADER : CAState.COMPLETE;
        } else {
            throw new UnexpectedFrameError(f, AMQP.FRAME_METHOD);
        }
    }

    private void consumeHeaderFrame(Frame f) throws IOException {
        if (f.getType() == AMQP.FRAME_HEADER) {
            this.contentHeader = AMQImpl.readContentHeaderFrom(f.getInputStream());
            this.remainingBodyBytes = this.contentHeader.getBodySize();
            updateContentBodyState();
        } else {
            throw new UnexpectedFrameError(f, AMQP.FRAME_HEADER);
        }
    }

    private void consumeBodyFrame(Frame f) {
        if (f.getType() == AMQP.FRAME_BODY) {
            byte[] fragment = f.getPayload();
            this.remainingBodyBytes -= fragment.length;
            updateContentBodyState();
            if (this.remainingBodyBytes < 0) {
                throw new UnsupportedOperationException("%%%%%% FIXME unimplemented");
            }
            appendBodyFragment(fragment);
        } else {
            throw new UnexpectedFrameError(f, AMQP.FRAME_BODY);
        }
    }

    /** Stitches together a fragmented content body into a single byte array */
    private byte[] coalesceContentBody() {
        if (this.bodyLength == 0) return EMPTY_BYTE_ARRAY;
        if (this.bodyN.size() == 1) return this.bodyN.get(0);

        byte[] body = new byte[bodyLength];
        int offset = 0;
        for (byte[] fragment : this.bodyN) {
            System.arraycopy(fragment, 0, body, offset, fragment.length);
            offset += fragment.length;
        }
        this.bodyN.clear();
        this.bodyN.add(body);
        return body;
    }

    public synchronized byte[] getContentBody() {
        return coalesceContentBody();
    }

    private void appendBodyFragment(byte[] fragment) {
        if (fragment == null || fragment.length == 0) return;
        bodyN.add(fragment);
        bodyLength += fragment.length;
    }

    /**
     * @param f frame to be incorporated
     * @return true if command becomes complete
     * @throws IOException if error reading frame
     */
    public synchronized boolean handleFrame(Frame f) throws IOException
    {
        switch (this.state) {
          case EXPECTING_METHOD:          consumeMethodFrame(f); break;
          case EXPECTING_CONTENT_HEADER:  consumeHeaderFrame(f); break;
          case EXPECTING_CONTENT_BODY:    consumeBodyFrame(f);   break;

          default:
              throw new IllegalStateException("Bad Command State " + this.state);
        }
        return isComplete();
    }
}
