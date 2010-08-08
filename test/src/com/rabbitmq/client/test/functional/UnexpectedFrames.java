package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.*;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Test that the server correctly handles us when we send it bad frames
 */
public class UnexpectedFrames extends BrokerTestCase {

    private interface Confuser {
        public Frame confuse(Frame frame) throws IOException;
    }

    private static class ConfusedFrameHandler extends SocketFrameHandler {
        public ConfusedFrameHandler(Socket socket) throws IOException {
            super(socket);
        }

        @Override
        public void writeFrame(Frame frame) throws IOException {
            Frame confusedFrame = new Frame();
            confusedFrame.accumulator = frame.accumulator;
            confusedFrame.channel = frame.channel;
            confusedFrame.type = frame.type;

            confusedFrame = confuser.confuse(confusedFrame);
            if (confusedFrame != null) {
                super.writeFrame(confusedFrame);
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
                    frame.type = AMQP.FRAME_HEADER;
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
            public Frame confuse(Frame frame) throws IOException {
                if (frame.type == AMQP.FRAME_HEADER) {
                    byte[] payload = frame.accumulator.toByteArray();
                    // First two bytes = class ID, must match class ID from
                    // method.
                    payload[0] = 12;
                    payload[1] = 34;
                    frame.accumulator = new ByteArrayOutputStream();
                    frame.accumulator.write(payload, 0, payload.length);
                }
                return frame;
            }
        });
    }

    private void expectUnexpectedFrameError(Confuser confuser)
        throws IOException {

        ((ConfusedFrameHandler)((AMQConnection)connection).getFrameHandler()).
            confuser = confuser;

        //NB: the frame confuser relies on the encoding of the
        //method field to be at least 8 bytes long
        channel.basicPublish("", "routing key", null, "Hello".getBytes());
        expectError(AMQP.UNEXPECTED_FRAME);
    }

}
