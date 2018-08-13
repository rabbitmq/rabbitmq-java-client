package com.rabbitmq.tools.jsonrpc;

import java.lang.reflect.Method;

/**
 *
 */
public interface JsonRpcMapper {

    JsonRpcRequest parse(String requestBody, ServiceDescription description);

    JsonRpcResponse parse(String responseBody);

    Object[] parameters(JsonRpcRequest request, Method method);

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
