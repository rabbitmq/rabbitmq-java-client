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


package com.rabbitmq.client;

/**
 * Indicates that a {@link Method} object was supplied that was not
 * expected. For instance, {@link Channel#basicGet} throws this if it
 * receives anything other than {@link AMQP.Basic.GetOk} or
 * {@link AMQP.Basic.GetEmpty}, and the
 * {@link com.rabbitmq.client.impl.AMQImpl.DefaultMethodVisitor DefaultMethodVisitor}
 * throws this as the action within each visitor case.
 */
public class UnexpectedMethodError extends Error {
    private static final long serialVersionUID = 1L;
    private final Method _method;

    /**
     * Construct an UnexpecteMethodError with the given method parameter
     * @param method the unexpected method
     */
    public UnexpectedMethodError(Method method) {
        _method = method;
    }

    /**
     * Return a string representation of this error.
     * @return a string describing the error
     */
    public String toString() {
        return super.toString() + ": " + _method;
    }

    /**
     * Return the wrapped method.
     * @return the method whose appearance was "unexpected" and was deemed an error
     */
    public Method getMethod() {
        return _method;
    }
}
