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
//  Copyright (c) 2012 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.rpc;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Envelope;

import junit.framework.TestCase;

/**
 * Test {@link AbstractRpcHandler}.
 */
public class AbstractRpcHandlerTest extends TestCase {

    private class ClassA extends Object{};
    private class ClassB extends Object{};

    private ClassA passedParm = new ClassA();
    private ClassB fixedResult = new ClassB();

    private volatile boolean callHandled = false;
    private volatile boolean castHandled = false;

    private class DummyARH extends AbstractRpcHandler<ClassA, ClassB> {
        public ClassB handleCall(ClassA parm) {
            assertEquals("parm not preserved", passedParm, parm);
            callHandled = true;
            return fixedResult;
        }
        public void handleCast(ClassA parm) {
            assertEquals("parm not preserved", passedParm, parm);
            castHandled = true;
        }
    }

    private AbstractRpcHandler<ClassA, ClassB> tstARH = new DummyARH();

    /**
     * Tests that hierarchy drops through to basic interface preserving all parameters
     * @throws Exception test
     */
    public void testAbstractRpcHandlerPassThrough() throws Exception {
        BasicProperties.Builder propsBuilder = new BasicProperties.Builder();
        BasicProperties msgProps = propsBuilder.build();
        BasicProperties replyProps = propsBuilder.correlationId("correlId").build();

        ClassB result = tstARH.handleCall(new Envelope(0L, false, "exchange", "routingKey"), msgProps, passedParm, replyProps);
        assertTrue("not called or casted", callHandled & (!castHandled));
        assertEquals("Unexpected result", fixedResult, result);

        callHandled = false;
        castHandled = false;
        tstARH.handleCast(new Envelope(0L, false, "exchange", "routingKey"), msgProps, passedParm);
        assertTrue("called or not casted", (!callHandled) & castHandled);
    }
}
