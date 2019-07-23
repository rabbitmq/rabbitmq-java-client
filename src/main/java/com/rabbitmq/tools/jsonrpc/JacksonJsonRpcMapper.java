// Copyright (c) 2018-2019 Pivotal Software, Inc.  All rights reserved.
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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ValueNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link JsonRpcMapper} based on Jackson.
 * <p>
 * Uses the streaming and databind modules. You need to add the appropriate dependency
 * to the classpath if you want to use this class, as the RabbitMQ Java client
 * library does not pull Jackson automatically when using a dependency management
 * tool like Maven or Gradle.
 * <p>
 * Make sure to use the latest version of the Jackson library, as the version used in the
 * RabbitMQ Java client can be a little bit behind.
 *
 * @see JsonRpcMapper
 * @since 5.4.0
 */
public class JacksonJsonRpcMapper implements JsonRpcMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonJsonRpcMapper.class);

    private final ObjectMapper mapper;

    public JacksonJsonRpcMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JacksonJsonRpcMapper() {
        this(new ObjectMapper());
    }

    @Override
    public JsonRpcRequest parse(String requestBody, ServiceDescription description) {
        JsonFactory jsonFactory = new MappingJsonFactory();
        String method = null, version = null;
        final List<TreeNode> parameters = new ArrayList<>();
        Object id = null;
        try (JsonParser parser = jsonFactory.createParser(requestBody)) {
            while (parser.nextToken() != null) {
                JsonToken token = parser.currentToken();
                if (token == JsonToken.FIELD_NAME) {
                    String name = parser.currentName();
                    token = parser.nextToken();
                    if ("method".equals(name)) {
                        method = parser.getValueAsString();
                    } else if ("id".equals(name)) {
                        TreeNode node = parser.readValueAsTree();
                        if (node instanceof ValueNode) {
                            ValueNode idNode = (ValueNode) node;
                            if (idNode.isNull()) {
                                id = null;
                            } else if (idNode.isTextual()) {
                                id = idNode.asText();
                            } else if (idNode.isNumber()) {
                                id = Long.valueOf(idNode.asLong());
                            } else {
                                LOGGER.warn("ID type not null, text, or number {}, ignoring", idNode);
                            }
                        } else {
                            LOGGER.warn("ID not a scalar value {}, ignoring", node);
                        }
                    } else if ("version".equals(name)) {
                        version = parser.getValueAsString();
                    } else if ("params".equals(name)) {
                        if (token == JsonToken.START_ARRAY) {
                            while (parser.nextToken() != JsonToken.END_ARRAY) {
                                parameters.add(parser.readValueAsTree());
                            }
                        } else {
                            throw new IllegalStateException("Field params must be an array");
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new JsonRpcMappingException("Error during JSON parsing", e);
        }

        if (method == null) {
            throw new IllegalArgumentException("Could not find method to invoke in request");
        }

        List<Object> convertedParameters = new ArrayList<>(parameters.size());
        if (!parameters.isEmpty()) {
            ProcedureDescription proc = description.getProcedure(method, parameters.size());
            Method internalMethod = proc.internal_getMethod();
            for (int i = 0; i < internalMethod.getParameterCount(); i++) {
                TreeNode parameterNode = parameters.get(i);
                try {
                    Class<?> parameterType = internalMethod.getParameterTypes()[i];
                    Object value = convert(parameterNode, parameterType);
                    convertedParameters.add(value);
                } catch (IOException e) {
                    throw new JsonRpcMappingException("Error during parameter conversion", e);
                }
            }
        }

        return new JsonRpcRequest(
            id, version, method,
            convertedParameters.toArray()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public JsonRpcResponse parse(String responseBody, Class<?> expectedReturnType) {
        JsonFactory jsonFactory = new MappingJsonFactory();
        Object result = null;
        JsonRpcException exception = null;
        Map<String, Object> errorMap = null;
        try (JsonParser parser = jsonFactory.createParser(responseBody)) {
            while (parser.nextToken() != null) {
                JsonToken token = parser.currentToken();
                if (token == JsonToken.FIELD_NAME) {
                    String name = parser.currentName();
                    if ("result".equals(name)) {
                        parser.nextToken();
                        if (expectedReturnType == Void.TYPE) {
                            result = null;
                        } else {
                            result = convert(parser.readValueAsTree(), expectedReturnType);
                        }
                    } else if ("error".equals(name)) {
                        errorMap = (Map<String, Object>) convert(parser.readValueAsTree(), Map.class);
                        exception = new JsonRpcException(
                            errorMap.toString(),
                            (String) errorMap.get("name"),
                            errorMap.get("code") == null ? 0 : (Integer) errorMap.get("code"),
                            (String) errorMap.get("message"),
                            errorMap
                        );
                    }
                }
            }
        } catch (IOException e) {
            throw new JsonRpcMappingException("Error during JSON parsing", e);
        }
        return new JsonRpcResponse(result, errorMap, exception);
    }

    @Override
    public String write(Object input) {
        try {
            return mapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new JsonRpcMappingException("Error during JSON serialization", e);
        }
    }

    protected Object convert(TreeNode node, Class<?> expectedType) throws IOException {
        Object value;
        if (expectedType.isPrimitive()) {
            ValueNode valueNode = (ValueNode) node;
            if (expectedType == Boolean.TYPE) {
                value = valueNode.booleanValue();
            } else if (expectedType == Character.TYPE) {
                value = valueNode.textValue().charAt(0);
            } else if (expectedType == Short.TYPE) {
                value = valueNode.shortValue();
            } else if (expectedType == Integer.TYPE) {
                value = valueNode.intValue();
            } else if (expectedType == Long.TYPE) {
                value = valueNode.longValue();
            } else if (expectedType == Float.TYPE) {
                value = valueNode.floatValue();
            } else if (expectedType == Double.TYPE) {
                value = valueNode.doubleValue();
            } else {
                throw new IllegalArgumentException("Primitive type not supported: " + expectedType);
            }
        } else {
            value = mapper.readValue(node.traverse(), expectedType);
        }
        return value;
    }
}
