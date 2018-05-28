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
public class RecordedExchange extends RecordedNamedEntity {
    private boolean durable;
    private boolean autoDelete;
    private Map<String, Object> arguments;
    private String type;

    public RecordedExchange(AutorecoveringChannel channel, String name) {
        super(channel, name);
    }

    public void recover() throws IOException {
        this.channel.getDelegate().exchangeDeclare(this.name, this.type, this.durable, this.autoDelete, this.arguments);
    }

    public RecordedExchange durable(boolean value) {
        this.durable = value;
        return this;
    }

    public RecordedExchange autoDelete(boolean value) {
        this.autoDelete = value;
        return this;
    }

    public RecordedExchange type(String value) {
        this.type = value;
        return this;
    }

    public RecordedExchange arguments(Map<String, Object> value) {
        this.arguments = value;
        return this;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }
    
    @Override
    public String toString() {
        return "RecordedExchange[name=" + name + ", type=" + type + ", durable=" + durable + ", autoDelete=" + autoDelete + ", arguments=" + arguments + ", channel=" + channel + "]";
    }
}
