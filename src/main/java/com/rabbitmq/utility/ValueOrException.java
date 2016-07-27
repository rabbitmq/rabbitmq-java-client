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
