// Copyright (c) 2007-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import java.util.concurrent.TimeoutException;

public class BlockingValueOrException<V, E extends Throwable & SensibleClone<E>>
    extends BlockingCell<ValueOrException<V, E>>
{
    public void setValue(V v) {
        super.set(ValueOrException.makeValue(v));
    }

    public void setException(E e) {
        super.set(ValueOrException.makeException(e));
    }

    public V uninterruptibleGetValue() throws E {
        return uninterruptibleGet().getValue();
    }

    public V uninterruptibleGetValue(int timeout) throws E, TimeoutException {
    	return uninterruptibleGet(timeout).getValue();
    }
}
