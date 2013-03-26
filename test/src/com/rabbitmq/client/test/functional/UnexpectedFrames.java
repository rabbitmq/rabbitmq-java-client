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
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

import java.io.IOException;
import java.net.Socket;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.SocketFrameHandler;
import com.rabbitmq.client.test.BrokerTestCase;

/**
 * Test that the server correctly handles us when we send it bad frames
 */
public class UnexpectedFrames extends BrokerTestCase {

    private interface Confuser {
        public Frame confuse(Frame frame) throws IOException;
    }

    private static class ConfusedFrameHandler extends SocketFrameHandler {

        private boolean confusedOnce = false;

        public ConfusedFrameHandler(Socket socket) throws IOException {
            super(socket);
        }

        @Override
        public void writeFrame(Frame frame) throws IOException {
            if (confusedOnce) {
                super.writeFrame(frame);
            } else {
                Frame confusedFrame = confuser.confuse(frame);
                if (confusedFrame != frame) confusedOnce = true;
                if (confusedFrame != null) {
                    super.writeFrame(confusedFrame);
                }
            }
        }

        public Confuser confuser = new Confuser() {
            public Frame confuse(Frame frame) {
                // Do nothing to start with, we need to negotiate before the
                // server will send us unexpected_frame errors
                return frame;
            }
        };
    }

    private static class ConfusedConnectionFactory extends ConnectionFactory {

        @Override protected FrameHandler createFrameHandler(Socket sock)
            throws IOException {
            return new ConfusedFrameHandler(sock);
        }
    }

    public UnexpectedFrames() {
        super();
        connectionFactory = new ConfusedConnectionFactory();
    }

    public void testMissingHeader() throws IOException {
        expectUnexpectedFrameError(new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.type == AMQP.FRAME_HEADER) {
                    return null;
                }
                return frame;
            }
        });
    }

    public void testMissingMethod() throws IOException {
        expectUnexpectedFrameError(new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.type == AMQP.FRAME_METHOD) {
                    // We can't just skip the method as that will lead us to
                    // send 0 bytes and hang waiting for a response.
                    return new Frame(AMQP.FRAME_HEADER,
                                     frame.channel, frame.getPayload());
                }
                return frame;
            }
        });
    }

    public void testMissingBody() throws IOException {
        expectUnexpectedFrameError(new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.type == AMQP.FRAME_BODY) {
                    return null;
                }
                return frame;
            }
        });
    }

    public void testWrongClassInHeader() throws IOException {
        expectUnexpectedFrameError(new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.type == AMQP.FRAME_HEADER) {
                    byte[] payload = frame.getPayload();
                    Frame confusedFrame = new Frame(AMQP.FRAME_HEADER,
                                                    frame.channel, payload);
                    // First two bytes = class ID, must match class ID from
                    // method.
                    payload[0] = 12;
                    payload[1] = 34;
                    return confusedFrame;
                }
                return frame;
            }
        });
    }

    public void testHeartbeatOnChannel() throws IOException {
        expectUnexpectedFrameError(new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.type == AMQP.FRAME_METHOD) {
                    return new Frame(AMQP.FRAME_HEARTBEAT, frame.channel);
                }
                return frame;
            }
        });
    }

    public void testUnknownFrameType() throws IOException {
        expectError(AMQP.FRAME_ERROR, new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.type == AMQP.FRAME_METHOD) {
                    return new Frame(0, frame.channel,
                                     "1234567890\0001234567890".getBytes());
                }
                return frame;
            }
        });
    }

    private void expectError(int error, Confuser confuser) throws IOException {
        ((ConfusedFrameHandler)((AMQConnection)connection).getFrameHandler()).
            confuser = confuser;

        //NB: the frame confuser relies on the encoding of the
        //method field to be at least 8 bytes long
        channel.basicPublish("", "routing key", null, "Hello".getBytes());
        expectError(error);
    }

    private void expectUnexpectedFrameError(Confuser confuser)
        throws IOException {
        expectError(AMQP.UNEXPECTED_FRAME, confuser);
    }

}
