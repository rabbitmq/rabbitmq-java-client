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

package com.rabbitmq.utility;

public class ValueOrException<V, E extends Throwable & SensibleClone<E>> {
    private final boolean _useValue;
    private final V _value;
    private final E _exception;

    /**
     * dual-purpose private constructor: one will be null, and the flag tells which to use
     * @param value the value to wrap, if applicable
     * @param exception the exception to wrap, if applicable
     * @param useValue true if we should use the value, rather than the exception
     */
    private ValueOrException(V value, E exception, boolean useValue) {
        _useValue = useValue;
        if (useValue) {
            _value = value;
            _exception = null;
        } else {
            _value = null;
            _exception = exception;
        }
    }

    /**
     * Factory method for values
     * @param value the value to wrap as a ValueOrException
     * @return the wrapped value
     */
    public static <V, E extends Throwable & SensibleClone<E>> ValueOrException<V, E> makeValue(V value) {
        return new ValueOrException<V, E>(value, null, true);
    }

    /**
     * Factory method for exceptions
     * @param exception the exception to wrap as a ValueOrException
     * @return the wrapped exception
     */
    public static <V, E extends Throwable & SensibleClone<E>> ValueOrException<V, E> makeException(E exception) {
        return new ValueOrException<V, E>(null, exception, false);
    }

    /** Retrieve value or throw exception
     * @return the wrapped value, if it's a value
     * @throws E the wrapped exception, if it's an exception
     */
    public V getValue() throws E {
        if (_useValue) {
            return _value;
        } else {
            throw Utility.fixStackTrace(_exception);
        }
    }
}
