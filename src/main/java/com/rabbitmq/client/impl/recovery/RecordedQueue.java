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

package com.rabbitmq.client.impl.recovery;

import java.io.IOException;
import java.util.Map;

/**
 * @since 3.3.0
 */
public class RecordedQueue extends RecordedNamedEntity {
    public static final String EMPTY_STRING = "";
    private boolean durable;
    private boolean autoDelete;
    private Map<String, Object> arguments;
    private boolean exclusive;
    private boolean serverNamed;

    public RecordedQueue(AutorecoveringChannel channel, String name) {
        super(channel, name);
    }

    public RecordedQueue exclusive(boolean value) {
        this.exclusive = value;
        return this;
    }

    public RecordedQueue serverNamed(boolean value) {
        this.serverNamed = value;
        return this;
    }

    public boolean isServerNamed() {
        return this.serverNamed;
    }

    public boolean isAutoDelete() { return this.autoDelete; }

    public void recover() throws IOException {
        this.name = this.channel.getDelegate().queueDeclare(this.getNameToUseForRecovery(),
                                                     this.durable,
                                                     this.exclusive,
                                                     this.autoDelete,
                                                     this.arguments).getQueue();
    }

    public String getNameToUseForRecovery() {
        if(isServerNamed()) {
            return EMPTY_STRING;
        } else {
            return this.name;
        }
    }

    public RecordedQueue durable(boolean value) {
        this.durable = value;
        return this;
    }

    public RecordedQueue autoDelete(boolean value) {
        this.autoDelete = value;
        return this;
    }

    public RecordedQueue arguments(Map<String, Object> value) {
        this.arguments = value;
        return this;
    }
    
    @Override
    public String toString() {
        return "RecordedQueue[name=" + name + ", durable=" + durable + ", autoDelete=" + autoDelete + ", exclusive=" + exclusive + ", arguments=" + arguments + "serverNamed=" + serverNamed + ", channel=" + channel + "]";
    }
}
