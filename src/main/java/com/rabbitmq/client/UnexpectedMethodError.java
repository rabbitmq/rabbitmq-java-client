// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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


package com.rabbitmq.client;

/**
 * Indicates that a {@link Method} object was supplied that was not
 * expected. For instance, {@link Channel#basicGet} throws this if it
 * receives anything other than {@link AMQP.Basic.GetOk} or
 * {@link AMQP.Basic.GetEmpty}, and the
 * {@link com.rabbitmq.client.impl.AMQImpl.DefaultMethodVisitor DefaultMethodVisitor}
 * throws this as the action within each visitor case.
 */
public class UnexpectedMethodError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final Method _method;

    /**
     * Construct an UnexpectedMethodError with the given method parameter
     * @param method the unexpected method
     */
    public UnexpectedMethodError(Method method) {
        _method = method;
    }

    /**
     * Return a string representation of this error.
     * @return a string describing the error
     */
    @Override
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
