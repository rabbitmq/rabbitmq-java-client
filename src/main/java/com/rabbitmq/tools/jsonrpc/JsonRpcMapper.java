// Copyright (c) 2018 Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.tools.jsonrpc;

/**
 *
 */
public interface JsonRpcMapper {

    JsonRpcRequest parse(String requestBody, ServiceDescription description);

    JsonRpcResponse parse(String responseBody, Class<?> expectedType);

    String write(Object input);

    class JsonRpcRequest {

        private final Object id;
        private final String version;
        private final String method;
        private final Object[] parameters;

        public JsonRpcRequest(Object id, String version, String method, Object[] parameters) {
            this.id = id;
            this.version = version;
            this.method = method;
            this.parameters = parameters;
        }

        public Object getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }

        public String getMethod() {
            return method;
        }

        public Object[] getParameters() {
            return parameters;
        }

        public boolean isSystem() {
            return method.startsWith("system.");
        }

        public boolean isSystemDescribe() {
            return "system.describe".equals(method);
        }
    }

    class JsonRpcResponse {

        private final Object reply;
        private final Object result;
        private final Object error;
        private final JsonRpcException exception;

        public JsonRpcResponse(Object reply, Object result, Object error, JsonRpcException exception) {
            this.reply = reply;
            this.result = result;
            this.error = error;
            this.exception = exception;
        }

        public Object getReply() {
            return reply;
        }

        public Object getError() {
            return error;
        }

        public Object getResult() {
            return result;
        }

        public JsonRpcException getException() {
            return exception;
        }
    }
}
