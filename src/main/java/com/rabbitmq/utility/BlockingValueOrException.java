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

import java.util.concurrent.TimeoutException;

public class BlockingValueOrException<V, E extends Throwable & SensibleClone<E>>
    extends BlockingCell<ValueOrException<V, E>>
{
    public void setValue(V v) {
        super.set(ValueOrException.<V, E>makeValue(v));
    }

    public void setException(E e) {
        super.set(ValueOrException.<V, E>makeException(e));
    }

    public V uninterruptibleGetValue() throws E {
        return uninterruptibleGet().getValue();
    }

    public V uninterruptibleGetValue(int timeout) throws E, TimeoutException {
    	return uninterruptibleGet(timeout).getValue();
    }
}
