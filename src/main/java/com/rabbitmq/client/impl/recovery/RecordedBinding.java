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
public abstract class RecordedBinding extends RecordedEntity {
    protected String source;
    protected String destination;
    protected String routingKey;
    protected Map<String, Object> arguments;

    public RecordedBinding(AutorecoveringChannel channel) {
        super(channel);
    }

    public RecordedBinding source(String value) {
        this.source = value;
        return this;
    }

    public RecordedBinding destination(String value) {
        this.destination = value;
        return this;
    }

    public RecordedBinding routingKey(String value) {
        this.routingKey = value;
        return this;
    }

    public RecordedBinding arguments(Map<String, Object> value) {
        this.arguments = value;
        return this;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public abstract void recover() throws IOException;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RecordedBinding that = (RecordedBinding) o;

        if (arguments != null ? !arguments.equals(that.arguments) : that.arguments != null) return false;
        if (!destination.equals(that.destination)) return false;
        if (!routingKey.equals(that.routingKey)) return false;
        if (!source.equals(that.source)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = source.hashCode();
        result = 31 * result + destination.hashCode();
        result = 31 * result + routingKey.hashCode();
        result = 31 * result + (arguments != null ? arguments.hashCode() : 0);
        return result;
    }
}
