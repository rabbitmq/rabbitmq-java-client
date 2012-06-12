//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.tools.jsonrpc;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.tools.json.JSONReader;
import com.rabbitmq.tools.json.JSONWriter;

/**
	  <a
	  href="http://json-rpc.org/wd/JSON-RPC-1-1-WD-20060807.html">JSON-RPC</a>
	  is a lightweight RPC mechanism using <a
	  href="http://www.json.org/">JSON</a> as a data language for
	  request and reply messages. It is rapidly becoming a
	  standard in web development, where it is used to make RPC
	  requests over HTTP. RabbitMQ provides an AMQP transport
	  binding for JSON-RPC in the form of
	  the <code>JsonRpcClient</code> class.
    <p/>
	  JSON-RPC services are self-describing - each service is able
	  to list its supported procedures, and each procedure
	  describes its parameters and types. An instance of
	  JsonRpcClient retrieves its service description using the
	  standard <code>system.describe</code> procedure when it is
	  constructed, and uses the information to coerce parameter
	  types appropriately. A JSON service description is parsed
	  into instances of <code>ServiceDescription</code>. Client
	  code can access the service description by reading the
	  <code>serviceDescription</code> field of
	  <code>JsonRpcClient</code> instances.

	  @see #call(String, Object[])
	  @see #call(String[])
 */
public class JsonRpcClient extends RpcClient implements InvocationHandler {
    /** Holds the JSON-RPC service description for this client. */
    private ServiceDescription serviceDescription;

    /**
     * Construct a new JsonRpcClient, passing the parameters through
     * to RpcClient's constructor. The service description record is
     * retrieved from the server during construction.
     * @param channel to use to access broker
     * @param exchange the exchange to connect to
     * @param routingKey the routing key
     * @param timeout milliseconds before timing out on waited response
     * @throws IOException communication error
     * @throws JsonRpcException JSON service description error
     * @throws TimeoutException if a response is not received within the timeout specified
     */
    public JsonRpcClient(Channel channel, String exchange, String routingKey, int timeout)
        throws IOException, JsonRpcException, TimeoutException
    {
	super(channel, exchange, routingKey, timeout);
	retrieveServiceDescription();
    }

    /**
     * Construct a new JsonRpcClient, passing the parameters through
     * to RpcClient's constructor. The service description record is
     * retrieved from the server during construction; waits indefinitely for response.
     * @param channel to use to access broker
     * @param exchange the exchange to connect to
     * @param routingKey the routing key
     * @throws IOException communication error
     * @throws JsonRpcException JSON service description error
     * @throws TimeoutException (should not be thrown)
     */
    public JsonRpcClient(Channel channel, String exchange, String routingKey)
    throws IOException, JsonRpcException, TimeoutException
    {
        this(channel, exchange, routingKey, RpcClient.NO_TIMEOUT);
    }

    /**
     * Private API - parses a JSON-RPC reply object, checking it for exceptions.
     * @param reply object to parse
     * @return the result contained within the reply, if no exception is found
     * @throws JsonRpcException if the reply object contained an exception
     */
    public static Object checkReply(Map<String, Object> reply)
        throws JsonRpcException
    {
	if (reply.containsKey("error")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) reply.get("error");
            // actually a Map<String, Object>
            throw new JsonRpcException(map);
        }

        Object result = reply.get("result");
        //System.out.println(new JSONWriter().write(result));
        return result;
    }

    /**
     * Public API - builds, encodes and sends a JSON-RPC request, and
     * waits for the response.
     * @param method name of procedure to call
     * @param params array of object parameters for call
     * @return the result contained within the reply, if no exception is found
     * @throws IOException if communication fails
     * @throws JsonRpcException if the reply object contained an exception
     * @throws TimeoutException if a response is not received within the timeout specified
     */
    public Object call(String method, Object[] params) throws IOException, JsonRpcException, TimeoutException
    {
        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("id", null);
        request.put("method", method);
        request.put("version", ServiceDescription.JSON_RPC_VERSION);
        request.put("params", (params == null) ? new Object[0] : params);
        String requestStr = new JSONWriter().write(request);
        try {
            String replyStr = this.stringCall(requestStr);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) (new JSONReader().read(replyStr));
            return checkReply(map);
        } catch(ShutdownSignalException ex) {
            throw new IOException(ex.getMessage()); // wrap, re-throw
        }

    }

    /**
     * Public API - implements InvocationHandler.invoke. This is
     * useful for constructing dynamic proxies for JSON-RPC
     * interfaces.
     */
    public Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable
    {
        return call(method.getName(), args);
    }

    /**
     * Public API - gets a dynamic proxy for a particular interface class.
     * @param klass interface class
     * @return proxy instance
     * @throws IllegalArgumentException if a proxy cannot be created
     */
    public Object createProxy(Class<?> klass)
        throws IllegalArgumentException
    {
        return Proxy.newProxyInstance(klass.getClassLoader(),
                                      new Class[] { klass },
                                      this);
    }

    /**
     * Private API - used by {@link #call(String[])} to convert
     * strings into the required data types for a call.
     * @param val string representation of value
     * @param type type of value represented
     * @return object of correct type and value
     * @throws NumberFormatException if integers or floating point numbers aren't in the correct format
     * @throws IllegalArgumentException if type is not one of <code>bit</code>, <code>num</code>,
     * <code>str</code>, <code>arr</code>, <code>obj</code>, <code>any</code>, or <code>nil</code>.
     */
    public static Object coerce(String val, String type)
	throws NumberFormatException
    {
	if ("bit".equals(type)) {
	    return Boolean.getBoolean(val) ? Boolean.TRUE : Boolean.FALSE;
	} else if ("num".equals(type)) {
	    try {
		return new Integer(val);
	    } catch (NumberFormatException nfe) {
		return new Double(val);
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
     * Public API - as {@link #call(String,Object[])}, but takes the
     * method name from the first entry in <code>args</code>, and the
     * parameters from subsequent entries. All parameter values are
     * passed through coerce() to attempt to make them the types the
     * server is expecting.
     * @param args method name and arguments to call as array of strings
     * @return the result contained within the reply, if no exception is found
     * @throws JsonRpcException if the reply object contained an exception
     * @throws NumberFormatException if a coercion failed
     * @throws IOException if communication error
     * @throws TimeoutException if a response is not received within the timeout specified, if any
     * @see #coerce
     */
    public Object call(String[] args)
	throws NumberFormatException, IOException, JsonRpcException, TimeoutException
    {
	if (args.length == 0) {
	    throw new IllegalArgumentException("First string argument must be method name");
	}

	String method = args[0];
        int arity = args.length - 1;
	ProcedureDescription proc = serviceDescription.getProcedure(method, arity);
	ParameterDescription[] params = proc.getParams();

	Object[] actuals = new Object[arity];
	for (int count = 0; count < params.length; count++) {
	    actuals[count] = coerce(args[count + 1], params[count].type);
	}

	return call(method, actuals);
    }

    /**
     * Public API - gets the service description record that this
     * service loaded from the server itself at construction time.
     * @return service description of this service
     */
    public ServiceDescription getServiceDescription() {
	return serviceDescription;
    }

    /**
     * Private API - invokes the "system.describe" method on the
     * server, and parses and stores the resulting service description
     * in this object.
     * TODO: Avoid calling this from the constructor.
     * @throws TimeoutException if a response is not received within the timeout specified, if any
     */
    private void retrieveServiceDescription() throws IOException, JsonRpcException, TimeoutException
    {
        @SuppressWarnings("unchecked")
        Map<String, Object> rawServiceDescription = (Map<String, Object>) call("system.describe", null);
        serviceDescription = new ServiceDescription(rawServiceDescription);
    }
}
