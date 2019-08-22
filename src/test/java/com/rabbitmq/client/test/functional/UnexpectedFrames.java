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

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.SocketConfigurators;
import com.rabbitmq.client.impl.*;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import org.junit.Test;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.Socket;

/**
 * Test that the server correctly handles us when we send it bad frames
 */
public class UnexpectedFrames extends BrokerTestCase {

    private interface Confuser {
        Frame confuse(Frame frame) throws IOException;
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

        public ConfusedConnectionFactory() {
            super();
            if(TestUtils.USE_NIO) {
                useNio();
            } else {
                useBlockingIo();
            }
        }

        @Override protected FrameHandlerFactory createFrameHandlerFactory() {
            return new ConfusedFrameHandlerFactory();
        }
    }

    private static class ConfusedFrameHandlerFactory extends SocketFrameHandlerFactory {
        private ConfusedFrameHandlerFactory() {
            super(1000, SocketFactory.getDefault(), SocketConfigurators.defaultConfigurator(), false);
        }

        @Override public FrameHandler create(Socket sock) throws IOException {
            return new ConfusedFrameHandler(sock);
        }
    }

    public UnexpectedFrames() {
        super();
        connectionFactory = new ConfusedConnectionFactory();
    }

    @Test public void missingHeader() throws IOException {
        expectUnexpectedFrameError(new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.getType() == AMQP.FRAME_HEADER) {
                    return null;
                }
                return frame;
            }
        });
    }

    @Test public void missingMethod() throws IOException {
        expectUnexpectedFrameError(new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.getType() == AMQP.FRAME_METHOD) {
                    // We can't just skip the method as that will lead us to
                    // send 0 bytes and hang waiting for a response.
                    return new Frame(AMQP.FRAME_HEADER,
                                     frame.getChannel(), frame.getPayload());
                }
                return frame;
            }
        });
    }

    @Test public void missingBody() throws IOException {
        expectUnexpectedFrameError(new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.getType() == AMQP.FRAME_BODY) {
                    return null;
                }
                return frame;
            }
        });
    }

    @Test public void wrongClassInHeader() throws IOException {
        expectUnexpectedFrameError(new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.getType() == AMQP.FRAME_HEADER) {
                    byte[] payload = frame.getPayload();
                    Frame confusedFrame = new Frame(AMQP.FRAME_HEADER,
                                                    frame.getChannel(), payload);
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

    @Test public void heartbeatOnChannel() throws IOException {
        expectUnexpectedFrameError(new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.getType() == AMQP.FRAME_METHOD) {
                    return new Frame(AMQP.FRAME_HEARTBEAT, frame.getChannel());
                }
                return frame;
            }
        });
    }

    @Test public void unknownFrameType() throws IOException {
        expectError(AMQP.FRAME_ERROR, new Confuser() {
            public Frame confuse(Frame frame) {
                if (frame.getType() == AMQP.FRAME_METHOD) {
                    return new Frame(0, frame.getChannel(),
                                     "1234567890\0001234567890".getBytes());
                }
                return frame;
            }
        });
    }

    private void expectError(int error, Confuser confuser) throws IOException {
        ((ConfusedFrameHandler)((AutorecoveringConnection)connection).getDelegate().getFrameHandler()).
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
