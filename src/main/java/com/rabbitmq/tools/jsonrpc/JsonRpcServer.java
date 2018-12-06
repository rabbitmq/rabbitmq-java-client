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

package com.rabbitmq.tools.jsonrpc;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.StringRpcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON-RPC Server class.
 * <p>
 * Given a Java {@link Class}, representing an interface, and an
 * implementation of that interface, JsonRpcServer will reflect on the
 * class to construct the {@link ServiceDescription}, and will route
 * incoming requests for methods on the interface to the
 * implementation object while the mainloop() is running.
 * <p>
 * {@link JsonRpcServer} delegates JSON parsing and generating to
 * a {@link JsonRpcMapper}.
 *
 * @see com.rabbitmq.client.RpcServer
 * @see JsonRpcClient
 * @see JsonRpcMapper
 * @see JacksonJsonRpcMapper
 */
public class JsonRpcServer extends StringRpcServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonRpcServer.class);
    private final JsonRpcMapper mapper;
    /**
     * Holds the JSON-RPC service description for this client.
     */
    private ServiceDescription serviceDescription;
    /**
     * The instance backing this server.
     */
    private Object interfaceInstance;

    public JsonRpcServer(Channel channel,
        Class<?> interfaceClass,
        Object interfaceInstance, JsonRpcMapper mapper)
        throws IOException {
        super(channel);
        this.mapper = mapper;
        init(interfaceClass, interfaceInstance);
    }

    /**
     * Construct a server that talks to the outside world using the
     * given channel, and constructs a fresh temporary
     * queue. Use getQueueName() to discover the created queue name.
     *
     * @param channel           AMQP channel to use
     * @param interfaceClass    Java interface that this server is exposing to the world
     * @param interfaceInstance Java instance (of interfaceClass) that is being exposed
     * @throws IOException if something goes wrong during an AMQP operation
     */
    public JsonRpcServer(Channel channel,
        Class<?> interfaceClass,
        Object interfaceInstance)
        throws IOException {
        this(channel, interfaceClass, interfaceInstance, new JacksonJsonRpcMapper());
    }

    public JsonRpcServer(Channel channel,
        String queueName,
        Class<?> interfaceClass,
        Object interfaceInstance, JsonRpcMapper mapper)
        throws IOException {
        super(channel, queueName);
        this.mapper = mapper;
        init(interfaceClass, interfaceInstance);
    }

    /**
     * Construct a server that talks to the outside world using the
     * given channel and queue name. Our superclass,
     * RpcServer, expects the queue to exist at the time of
     * construction.
     *
     * @param channel           AMQP channel to use
     * @param queueName         AMQP queue name to listen for requests on
     * @param interfaceClass    Java interface that this server is exposing to the world
     * @param interfaceInstance Java instance (of interfaceClass) that is being exposed
     * @throws IOException if something goes wrong during an AMQP operation
     */
    public JsonRpcServer(Channel channel,
        String queueName,
        Class<?> interfaceClass,
        Object interfaceInstance)
        throws IOException {
        this(channel, queueName, interfaceClass, interfaceInstance, new JacksonJsonRpcMapper());
    }

    private void init(Class<?> interfaceClass, Object interfaceInstance) {
        /**
         * The interface this server implements.
         */
        this.interfaceInstance = interfaceInstance;
        this.serviceDescription = new ServiceDescription(interfaceClass);
    }

    /**
     * Override our superclass' method, dispatching to doCall.
     */
    @Override
    public String handleStringCall(String requestBody, AMQP.BasicProperties replyProperties) {
        String replyBody = doCall(requestBody);
        return replyBody;
    }

    /**
     * Runs a single JSON-RPC request.
     *
     * @param requestBody the JSON-RPC request string (a JSON encoded value)
     * @return a JSON-RPC response string (a JSON encoded value)
     */
    public String doCall(String requestBody) {
        Object id;
        String method;
        Object[] params;
        String response;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Request: {}", requestBody);
        }
        try {
            JsonRpcMapper.JsonRpcRequest request = mapper.parse(requestBody, serviceDescription);
            if (request == null) {
                response = errorResponse(null, 400, "Bad Request", null);
            } else if (!ServiceDescription.JSON_RPC_VERSION.equals(request.getVersion())) {
                response = errorResponse(null, 505, "JSONRPC version not supported", null);
            } else {
                id = request.getId();
                method = request.getMethod();
                params = request.getParameters();
                if (request.isSystemDescribe()) {
                    response = resultResponse(id, serviceDescription);
                } else if (request.isSystem()) {
                    response = errorResponse(id, 403, "System methods forbidden", null);
                } else {
                    Object result;
                    try {
                        Method matchingMethod = matchingMethod(method, params);
                        if (LOGGER.isDebugEnabled()) {
                            Collection<String> parametersValuesAndTypes = new ArrayList<String>();
                            if (params != null) {
                                for (Object param : params) {
                                    parametersValuesAndTypes.add(
                                        String.format("%s (%s)", param, param == null ? "?" : param.getClass())
                                    );
                                }
                            }
                            LOGGER.debug("About to invoke {} method with parameters {}", matchingMethod, parametersValuesAndTypes);
                        }
                        result = matchingMethod.invoke(interfaceInstance, params);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Invocation returned {} ({})", result, result == null ? "?" : result.getClass());
                        }
                        response = resultResponse(id, result);
                    } catch (Throwable t) {
                        LOGGER.info("Error while processing JSON RPC request", t);
                        response = errorResponse(id, 500, "Internal Server Error", t);
                    }
                }
            }
        } catch (ClassCastException cce) {
            // Bogus request!
            response = errorResponse(null, 400, "Bad Request", null);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Response: {}", response);
        }

        return response;
    }

    /**
     * Retrieves the best matching method for the given method name and parameters.
     * <p>
     * Subclasses may override this if they have specialised
     * dispatching requirements, so long as they continue to honour
     * their ServiceDescription.
     */
    public Method matchingMethod(String methodName, Object[] params) {
        ProcedureDescription proc = serviceDescription.getProcedure(methodName, params.length);
        return proc.internal_getMethod();
    }

    /**
     * Construct and encode a JSON-RPC error response for the request
     * ID given, using the code, message, and possible
     * (JSON-encodable) argument passed in.
     */
    private String errorResponse(Object id, int code, String message, Object errorArg) {
        Map<String, Object> err = new HashMap<String, Object>();
        err.put("name", "JSONRPCError");
        err.put("code", code);
        err.put("message", message);
        err.put("error", errorArg);
        return response(id, "error", err);
    }

    /**
     * Construct and encode a JSON-RPC success response for the
     * request ID given, using the result value passed in.
     */
    private String resultResponse(Object id, Object result) {
        return response(id, "result", result);
    }

    /**
     * Private API - used by errorResponse and resultResponse.
     */
    private String response(Object id, String label, Object value) {
        Map<String, Object> resp = new HashMap<String, Object>();
        resp.put("version", ServiceDescription.JSON_RPC_VERSION);
        if (id != null) {
            resp.put("id", id);
        }
        resp.put(label, value);
        String respStr = mapper.write(resp);
        return respStr;
    }

    /**
     * Public API - gets the service description record that this
     * service built from interfaceClass at construction time.
     */
    public ServiceDescription getServiceDescription() {
        return serviceDescription;
    }
}
