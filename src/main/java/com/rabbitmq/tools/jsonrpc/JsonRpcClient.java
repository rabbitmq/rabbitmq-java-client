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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.RpcClientParams;
import com.rabbitmq.client.ShutdownSignalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
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
 *
 * @see #call(String, Object[])
 * @see JsonRpcMapper
 * @see JacksonJsonRpcMapper
 */
public class JsonRpcClient extends RpcClient implements InvocationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonRpcClient.class);
    private final JsonRpcMapper mapper;
    /**
     * Holds the JSON-RPC service description for this client.
     */
    private ServiceDescription serviceDescription;

    /**
     * Construct a new {@link JsonRpcClient}, passing the {@link RpcClientParams} through {@link RpcClient}'s constructor.
     * <p>
     * The service description record is
     * retrieved from the server during construction.
     *
     * @param rpcClientParams
     * @param mapper
     * @throws IOException
     * @throws JsonRpcException
     * @throws TimeoutException
     */
    public JsonRpcClient(RpcClientParams rpcClientParams, JsonRpcMapper mapper)
            throws IOException, JsonRpcException, TimeoutException {
        super(rpcClientParams);
        this.mapper = mapper;
        retrieveServiceDescription();
    }

    /**
     * Construct a new JsonRpcClient, passing the parameters through
     * to RpcClient's constructor. The service description record is
     * retrieved from the server during construction.
     *
     * @throws TimeoutException if a response is not received within the timeout specified, if any
     */
    public JsonRpcClient(Channel channel, String exchange, String routingKey, int timeout, JsonRpcMapper mapper)
        throws IOException, JsonRpcException, TimeoutException {
        super(new RpcClientParams()
                .channel(channel)
                .exchange(exchange)
                .routingKey(routingKey)
                .timeout(timeout)
        );
        this.mapper = mapper;
        retrieveServiceDescription();
    }

    /**
     * Construct a new JsonRpcClient, passing the parameters through
     * to RpcClient's constructor. The service description record is
     * retrieved from the server during construction.
     *
     * @throws TimeoutException if a response is not received within the timeout specified, if any
     */
    public JsonRpcClient(Channel channel, String exchange, String routingKey, int timeout)
        throws IOException, JsonRpcException, TimeoutException {
        this(channel, exchange, routingKey, timeout, new JacksonJsonRpcMapper());
    }

    public JsonRpcClient(Channel channel, String exchange, String routingKey)
        throws IOException, JsonRpcException, TimeoutException {
        this(channel, exchange, routingKey, RpcClient.NO_TIMEOUT);
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
