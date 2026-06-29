// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

package com.rabbitmq.tools.jsonrpc;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.RpcClientParams;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.tools.json.JSONReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * <a href="https://www.jsonrpc.org/">JSON-RPC</a> is a lightweight
 * RPC mechanism using <a href="https://www.json.org/">JSON</a>
 * as a data language for request and reply messages. It is
 * rapidly becoming a standard in web development, where it is
 * used to make RPC requests over HTTP. RabbitMQ provides an
 * AMQP transport binding for JSON-RPC in the form of the
 * <code>JsonRpcClient</code> class.
 * <p>
 * JSON-RPC services are self-describing - each service is able
 * to list its supported procedures, and each procedure
 * describes its parameters and types. An instance of
 * JsonRpcClient retrieves its service description using the
 * standard <code>system.describe</code> procedure when it is
 * constructed, and uses the information to coerce parameter
 * types appropriately. A JSON service description is parsed
 * into instances of <code>ServiceDescription</code>. Client
 * code can access the service description by reading the
 * <code>serviceDescription</code> field of
 * <code>JsonRpcClient</code> instances.
 * <p>
 * {@link JsonRpcClient} delegates JSON parsing and generating to
 * a {@link JsonRpcMapper}.
 * <p>
 * By default only a safe set of return types is accepted from the remote
 * service description (primitives, {@link String}, {@link Map}, {@link List},
 * and common boxed types). Services that return custom types must pass an
 * explicit allowlist via the constructors that accept {@code allowedReturnTypes}.
 *
 * @see #call(String, Object[])
 * @see JsonRpcMapper
 * @see JacksonJsonRpcMapper
 */
public class JsonRpcClient extends RpcClient implements InvocationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonRpcClient.class);

    // Types that are safe to deserialize into without an explicit allowlist.
    private static final Set<String> SAFE_RETURN_TYPES;

    static {
        Set<String> types = new HashSet<>();
        types.add("void");
        types.add("boolean");
        types.add("int");
        types.add("long");
        types.add("double");
        types.add("float");
        types.add("short");
        types.add("byte");
        types.add("char");
        types.add(String.class.getName());
        types.add(Boolean.class.getName());
        types.add(Integer.class.getName());
        types.add(Long.class.getName());
        types.add(Double.class.getName());
        types.add(Float.class.getName());
        types.add(Short.class.getName());
        types.add(Byte.class.getName());
        types.add(Number.class.getName());
        types.add(Map.class.getName());
        types.add(List.class.getName());
        types.add(Object.class.getName());
        SAFE_RETURN_TYPES = Collections.unmodifiableSet(types);
    }

    private final JsonRpcMapper mapper;
    private final Set<String> allowedReturnTypes;
    /**
     * Holds the JSON-RPC service description for this client.
     */
    private ServiceDescription serviceDescription;

    /**
     * Construct a new {@link JsonRpcClient} that only accepts return types from the built-in
     * safe set (primitives, {@link String}, {@link Map}, {@link List}, common boxed types).
     * <p>
     * Services that declare custom return types will cause construction to fail with an
     * {@link IllegalStateException}. Use
     * {@link #JsonRpcClient(RpcClientParams, JsonRpcMapper, Set)} to allow additional types.
     *
     * @throws IllegalStateException if the service description contains a return type not in
     *         the allowlist
     */
    public JsonRpcClient(RpcClientParams rpcClientParams, JsonRpcMapper mapper)
            throws IOException, JsonRpcException, TimeoutException {
        this(rpcClientParams, mapper, Collections.emptySet());
    }

    /**
     * Construct a new {@link JsonRpcClient} with an explicit allowlist of permitted return types.
     * <p>
     * The provided types are merged with the built-in safe set, so common types ({@link String},
     * {@link Map}, primitives, etc.) do not need to be listed explicitly.
     *
     * @param allowedReturnTypes additional return types to accept from the remote service
     *        description, on top of the built-in safe set
     * @throws IllegalStateException if the service description contains a return type not in
     *         the allowlist
     */
    public JsonRpcClient(RpcClientParams rpcClientParams, JsonRpcMapper mapper, Set<Class<?>> allowedReturnTypes)
            throws IOException, JsonRpcException, TimeoutException {
        super(rpcClientParams);
        this.mapper = mapper;
        this.allowedReturnTypes = buildAllowedReturnTypes(allowedReturnTypes);
        retrieveServiceDescription();
        validateReturnTypes();
    }

    /**
     * Construct a new JsonRpcClient using the safe default return-type allowlist.
     *
     * @throws TimeoutException if a response is not received within the timeout specified, if any
     * @throws IllegalStateException if the service description contains a return type not in
     *         the allowlist
     */
    public JsonRpcClient(Channel channel, String exchange, String routingKey, int timeout, JsonRpcMapper mapper)
        throws IOException, JsonRpcException, TimeoutException {
        this(
            new RpcClientParams().channel(channel).exchange(exchange).routingKey(routingKey).timeout(timeout),
            mapper);
    }

    /**
     * Construct a new JsonRpcClient with an explicit allowlist of permitted return types.
     *
     * @param allowedReturnTypes additional return types to accept from the remote service
     *        description, on top of the built-in safe set
     * @throws TimeoutException if a response is not received within the timeout specified, if any
     * @throws IllegalStateException if the service description contains a return type not in
     *         the allowlist
     */
    public JsonRpcClient(Channel channel, String exchange, String routingKey, int timeout, JsonRpcMapper mapper,
        Set<Class<?>> allowedReturnTypes)
        throws IOException, JsonRpcException, TimeoutException {
        this(
            new RpcClientParams().channel(channel).exchange(exchange).routingKey(routingKey).timeout(timeout),
            mapper,
            allowedReturnTypes);
    }

    /**
     * Construct a new JsonRpcClient using the safe default return-type allowlist.
     *
     * @throws TimeoutException if a response is not received within the timeout specified, if any
     */
    public JsonRpcClient(Channel channel, String exchange, String routingKey, int timeout)
        throws IOException, JsonRpcException, TimeoutException {
        this(channel, exchange, routingKey, timeout, new DefaultJsonRpcMapper());
    }

    public JsonRpcClient(Channel channel, String exchange, String routingKey)
        throws IOException, JsonRpcException, TimeoutException {
        this(channel, exchange, routingKey, RpcClient.NO_TIMEOUT);
    }

    private static Set<String> buildAllowedReturnTypes(Set<Class<?>> extraTypes) {
        if (extraTypes == null || extraTypes.isEmpty()) {
            return SAFE_RETURN_TYPES;
        }
        Set<String> types = new HashSet<>(SAFE_RETURN_TYPES);
        for (Class<?> c : extraTypes) {
            types.add(c.getName());
        }
        return Collections.unmodifiableSet(types);
    }

    private void validateReturnTypes() {
        for (ProcedureDescription proc : serviceDescription.getProcs()) {
            String javaReturnType = proc.getJavaReturnType();
            if (javaReturnType != null && !allowedReturnTypes.contains(javaReturnType)) {
                throw new IllegalStateException(
                    "Return type not in allowlist: '"
                        + javaReturnType
                        + "'. Pass it via the allowedReturnTypes constructor parameter.");
            }
        }
    }

    /**
     * Private API - used by {@link #call(String[])} to ad-hoc convert
     * strings into the required data types for a call.
     *
     * This method is deprecated because it uses homegrown JSON utilities
     * that don't deal correctly with complex types. The {@link JacksonJsonRpcMapper}
     * has been introduced to handle primitive and complex types, as well
     * as primitive wrappers correctly.
     *
     * @deprecated This method will be removed in the next major version
     */
    @Deprecated
    public static Object coerce(String val, String type)
        throws NumberFormatException {
        if ("bit".equals(type)) {
            return Boolean.getBoolean(val) ? Boolean.TRUE : Boolean.FALSE;
        } else if ("num".equals(type)) {
            try {
                return Integer.valueOf(val);
            } catch (NumberFormatException nfe) {
                return Double.valueOf(val);
            }
        } else if ("str".equals(type)) {
            return val;
        } else if ("arr".equals(type) || "obj".equals(type) || "any".equals(type)) {
            return new JSONReader().read(val);
        } else if ("nil".equals(type)) {
            return null;
        } else {
            throw new IllegalArgumentException("Bad type: " + type);
        }
    }

    /**
     * Private API - parses a JSON-RPC reply object, checking it for exceptions.
     *
     * @return the result contained within the reply, if no exception is found
     * Throws JsonRpcException if the reply object contained an exception
     */
    private Object checkReply(JsonRpcMapper.JsonRpcResponse reply)
        throws JsonRpcException {
        if (reply.getError() != null) {
            throw reply.getException();
        }

        return reply.getResult();
    }

    /**
     * Public API - builds, encodes and sends a JSON-RPC request, and
     * waits for the response.
     *
     * @return the result contained within the reply, if no exception is found
     * @throws JsonRpcException if the reply object contained an exception
     * @throws TimeoutException if a response is not received within the timeout specified, if any
     */
    public Object call(String method, Object[] params) throws IOException, JsonRpcException, TimeoutException {
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("id", null);
        request.put("method", method);
        request.put("version", ServiceDescription.JSON_RPC_VERSION);
        params = (params == null) ? new Object[0] : params;
        request.put("params", params);
        String requestStr = mapper.write(request);
        try {
            String replyStr = this.stringCall(requestStr);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Reply string: {}", replyStr);
            }
            Class<?> expectedType;
            if ("system.describe".equals(method) && params.length == 0) {
                expectedType = Map.class;
            } else {
                ProcedureDescription proc = serviceDescription.getProcedure(method, params.length);
                expectedType = proc.getReturnType();
            }
            JsonRpcMapper.JsonRpcResponse reply = mapper.parse(replyStr, expectedType);

            return checkReply(reply);
        } catch (ShutdownSignalException ex) {
            throw new IOException(ex.getMessage()); // wrap, re-throw
        }
    }

    /**
     * Public API - implements InvocationHandler.invoke. This is
     * useful for constructing dynamic proxies for JSON-RPC
     * interfaces.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable {
        return call(method.getName(), args);
    }

    /**
     * Public API - gets a dynamic proxy for a particular interface class.
     */
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> klass)
        throws IllegalArgumentException {
        return (T) Proxy.newProxyInstance(klass.getClassLoader(),
            new Class[] { klass },
            this);
    }

<<<<<<< HEAD
    /**
     * Public API - as {@link #call(String, Object[])}, but takes the
     * method name from the first entry in <code>args</code>, and the
     * parameters from subsequent entries. All parameter values are
     * passed through coerce() to attempt to make them the types the
     * server is expecting.
     *
     * This method is deprecated because it uses homegrown JSON utilities
     * that don't deal correctly with complex types. The {@link JacksonJsonRpcMapper}
     * has been introduced to handle primitive and complex types, as well
     * as primitive wrappers correctly.
     *
     * @return the result contained within the reply, if no exception is found
     * @throws JsonRpcException      if the reply object contained an exception
     * @throws NumberFormatException if a coercion failed
     * @throws TimeoutException      if a response is not received within the timeout specified, if any
     * @see #coerce
     * @deprecated This method will be removed in the next major version
     */
    @Deprecated
    public Object call(String[] args)
        throws NumberFormatException, IOException, JsonRpcException, TimeoutException {
        if (args.length == 0) {
            throw new IllegalArgumentException("First string argument must be method name");
        }

        String method = args[0];
        int arity = args.length - 1;
        ProcedureDescription proc = serviceDescription.getProcedure(method, arity);
        ParameterDescription[] params = proc.getParams();

        Object[] actuals = new Object[arity];
        for (int count = 0; count < params.length; count++) {
            actuals[count] = coerce(args[count + 1], params[count].getType());
        }

        return call(method, actuals);
    }

=======
>>>>>>> f85d038d (Add allowlist in JsonRpcClient)
    /**
     * Public API - gets the service description record that this
     * service loaded from the server itself at construction time.
     */
    public ServiceDescription getServiceDescription() {
        return serviceDescription;
    }

    /**
     * Private API - invokes the "system.describe" method on the
     * server, and parses and stores the resulting service description
     * in this object.
     * TODO: Avoid calling this from the constructor.
     *
     * @throws TimeoutException if a response is not received within the timeout specified, if any
     */
    private void retrieveServiceDescription() throws IOException, JsonRpcException, TimeoutException {
        @SuppressWarnings("unchecked")
        Map<String, Object> rawServiceDescription = (Map<String, Object>) call("system.describe", null);
        serviceDescription = new ServiceDescription(rawServiceDescription);
    }
}
