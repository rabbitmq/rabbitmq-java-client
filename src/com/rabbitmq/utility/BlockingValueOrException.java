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
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.utility;

import java.util.concurrent.TimeoutException;

/**
 * A single value blocking placeholder that also is satisfied by an exception.
 * @param <V> type of value accepted
 * @param <E> type of exception accepted
 */
public class BlockingValueOrException<V, E extends Throwable & SensibleClone<E>>
    extends BlockingCell<ValueOrException<V, E>>
{
    /**
     * @param v value to place in cell
     */
    public void setValue(V v) {
        super.set(ValueOrException.<V, E>makeValue(v));
    }

    /**
     * @param e exception to place in cell
     */
    public void setException(E e) {
        super.set(ValueOrException.<V, E>makeException(e));
    }

    /**
     * Wait until cell satisfied
     * @return value if satisfied with value
     * @throws E if satisfied with exception
     */
    public V uninterruptibleGetValue() throws E {
        return uninterruptibleGet().getValue();
    }

    /**
     * Wait until cell satisfied, or until timeout
     * @param timeout max time to wait
     * @return value if satisfied with value
     * @throws E if satisfied with exception
     * @throws TimeoutException if no satisfaction within timeout
     */
    public V uninterruptibleGetValue(int timeout) throws E, TimeoutException {
    	return uninterruptibleGet(timeout).getValue();
    }
}
