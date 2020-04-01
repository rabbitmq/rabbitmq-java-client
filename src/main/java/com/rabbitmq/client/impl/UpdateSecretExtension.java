// Copyright (c) 2019-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.LongString;

import java.io.IOException;
import java.util.Objects;

/**
 * Helper for <code>update-secret</code> extension {@link com.rabbitmq.client.Method}.
 * <p>
 * {@link com.rabbitmq.client.Method} classes are usually automatically
 * generated, but providing the class directly is necessary in this case
 * for some internal CI testing jobs running against RabbitMQ 3.7.
 *
 * @since 5.8.0
 */
abstract class UpdateSecretExtension {

    static class UpdateSecret extends Method {

        private final LongString newSecret;
        private final String reason;

        public UpdateSecret(LongString newSecret, String reason) {
            if (newSecret == null)
                throw new IllegalStateException("Invalid configuration: 'newSecret' must be non-null.");
            if (reason == null)
                throw new IllegalStateException("Invalid configuration: 'reason' must be non-null.");
            this.newSecret = newSecret;
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }

        public int protocolClassId() {
            return 10;
        }

        public int protocolMethodId() {
            return 70;
        }

        public String protocolMethodName() {
            return "connection.update-secret";
        }

        public boolean hasContent() {
            return false;
        }

        public Object visit(AMQImpl.MethodVisitor visitor) throws IOException {
            return null;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            UpdateSecret that = (UpdateSecret) o;
            if (!Objects.equals(newSecret, that.newSecret))
                return false;
            return Objects.equals(reason, that.reason);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + (newSecret != null ? newSecret.hashCode() : 0);
            result = 31 * result + (reason != null ? reason.hashCode() : 0);
            return result;
        }

        public void appendArgumentDebugStringTo(StringBuilder acc) {
            acc.append("(new-secret=")
                    .append(this.newSecret)
                    .append(", reason=")
                    .append(this.reason)
                    .append(")");
        }

        public void writeArgumentsTo(MethodArgumentWriter writer)
                throws IOException {
            writer.writeLongstr(this.newSecret);
            writer.writeShortstr(this.reason);
        }
    }
}

