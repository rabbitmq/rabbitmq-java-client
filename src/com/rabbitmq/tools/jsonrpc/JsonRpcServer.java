//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.tools.jsonrpc;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.StringRpcServer;
import com.rabbitmq.tools.json.JSONReader;
import com.rabbitmq.tools.json.JSONWriter;

/**
 * JSON-RPC Server class.
 *
 * Given a Java {@link Class}, representing an interface, and an
 * implementation of that interface, JsonRpcServer will reflect on the
 * class to construct the {@link ServiceDescription}, and will route
 * incoming requests for methods on the interface to the
 * implementation object while the mainloop() is running.
 *
 * @see com.rabbitmq.client.RpcServer
 * @see JsonRpcClient
 */
public class JsonRpcServer extends StringRpcServer {
    /** Holds the JSON-RPC service description for this client. */
    public ServiceDescription serviceDescription;
    /** The interface this server implements. */
    public Class interfaceClass;
    /** The instance backing this server. */
    public Object interfaceInstance;

    /**
     * Construct a server that talks to the outside world using the
     * given channel, and constructs a fresh temporary
     * queue. Use getQueueName() to discover the created queue name.
     * @param channel AMQP channel to use
     * @param interfaceClass Java interface that this server is exposing to the world
     * @param interfaceInstance Java instance (of interfaceClass) that is being exposed
     * @throws IOException if something goes wrong during an AMQP operation
     */
    public JsonRpcServer(Channel channel,
                         Class interfaceClass,
                         Object interfaceInstance)
        throws IOException
    {
	super(channel);
        init(interfaceClass, interfaceInstance);
    }

    private void init(Class interfaceClass, Object interfaceInstance)
    {
        this.interfaceClass = interfaceClass;
        this.interfaceInstance = interfaceInstance;
        this.serviceDescription = new ServiceDescription(interfaceClass);
    }

    /**
     * Construct a server that talks to the outside world using the
     * given channel and queue name. Our superclass,
     * RpcServer, expects the queue to exist at the time of
     * construction.
     * @param channel AMQP channel to use
     * @param queueName AMQP queue name to listen for requests on
     * @param interfaceClass Java interface that this server is exposing to the world
     * @param interfaceInstance Java instance (of interfaceClass) that is being exposed
     * @throws IOException if something goes wrong during an AMQP operation
     */
    public JsonRpcServer(Channel channel,
                         String queueName,
                         Class interfaceClass,
                         Object interfaceInstance)
        throws IOException
    {
	super(channel, queueName);
        init(interfaceClass, interfaceInstance);
    }

    /**
     * Override our superclass' method, dispatching to doCall.
     */
    public String handleStringCall(String requestBody, AMQP.BasicProperties replyProperties)
    {
        replyProperties.contentType = "application/json";
        String replyBody = doCall(requestBody);
        //System.err.println(requestBody + " --> " + replyBody);
        return replyBody;
    }

    /**
     * Runs a single JSON-RPC request.
     * @param requestBody the JSON-RPC request string (a JSON encoded value)
     * @return a JSON-RPC response string (a JSON encoded value)
     */
    public String doCall(String requestBody)
    {
        Map<String, Object> request;
        Object id;
        String method;
        Object[] params;
        try {
            request = (Map) new JSONReader().read(requestBody);
            if (request == null) {
                return errorResponse(null, 400, "Bad Request", null);
            }
            if (!ServiceDescription.JSON_RPC_VERSION.equals(request.get("version"))) {
                return errorResponse(null, 505, "JSONRPC version not supported", null);
            }

            id = request.get("id");
            method = (String) request.get("method");
            params = ((List) request.get("params")).toArray();
        } catch (ClassCastException cce) {
            // Bogus request!
            return errorResponse(null, 400, "Bad Request", null);
        }

        if (method.equals("system.describe")) {
            return resultResponse(id, serviceDescription);
        } else if (method.startsWith("system.")) {
            return errorResponse(id, 403, "System methods forbidden", null);
        } else {
            Object result;
            try {
                result = matchingMethod(method, params).invoke(interfaceInstance, params);
            } catch (Throwable t) {
                return errorResponse(id, 500, "Internal Server Error", t);
            }
            return resultResponse(id, result);
        }
    }

    /**
     * Retrieves the best matching method for the given method name and parameters.
     *
     * Subclasses may override this if they have specialised
     * dispatching requirements, so long as they continue to honour
     * their ServiceDescription.
     */
    public Method matchingMethod(String methodName, Object[] params)
    {
        ProcedureDescription proc = serviceDescription.getProcedure(methodName, params.length);
        return proc.internal_getMethod();
    }

    /**
     * Construct and encode a JSON-RPC error response for the request
     * ID given, using the code, message, and possible
     * (JSON-encodable) argument passed in.
     */
    public static String errorResponse(Object id, int code, String message, Object errorArg) {
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
    public static String resultResponse(Object id, Object result) {
        return response(id, "result", result);
    }

    /**
     * Private API - used by errorResponse and resultResponse.
     */
    public static String response(Object id, String label, Object value) {
        Map<String, Object> resp = new HashMap<String, Object>();
        resp.put("version", ServiceDescription.JSON_RPC_VERSION);
        if (id != null) {
            resp.put("id", id);
        }
        resp.put(label, value);
        String respStr = new JSONWriter().write(resp);
        //System.err.println(respStr);
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
