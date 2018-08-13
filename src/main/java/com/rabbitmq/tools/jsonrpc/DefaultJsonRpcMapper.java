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

import com.rabbitmq.tools.json.JSONReader;
import com.rabbitmq.tools.json.JSONWriter;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class DefaultJsonRpcMapper implements JsonRpcMapper {

    @Override
    public JsonRpcRequest parse(String requestBody, ServiceDescription description) {
        Map<String, Object> request = (Map<String,Object>) new JSONReader().read(requestBody);

        return new JsonRpcRequest(
            request.get("id"), request.get("version").toString(), request.get("method").toString(),
            ((List<?>) request.get("params")).toArray()
        );
    }

    @Override
    public JsonRpcResponse parse(String responseBody, Class<?> expectedType) {
        Map<String, Object> map = (Map<String, Object>) (new JSONReader().read(responseBody));
        Map<String, Object> error;
        JsonRpcException exception = null;
        if (map.containsKey("error")) {
            error = (Map<String, Object>) map.get("error");
            exception = new JsonRpcException(
                new JSONWriter().write(error),
                (String) error.get("name"),
                error.get("code") == null ? 0 : (Integer) error.get("code"),
                (String) error.get("message"),
                error
            );
        }
        return new JsonRpcResponse(map, map.get("result"), map.get("error"), exception);
    }

    @Override
    public String write(Object input) {
        return new JSONWriter().write(input);
    }

    /*
    @Override
    public Object[] parameters(JsonRpcRequest request, Method method) {
        Object[] parameters = request.getParameters();
        Object[] convertedParameters = new Object[parameters.length];
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            convertedParameters[i] = convert(parameters[i], parameterTypes[i]);
        }
        return convertedParameters;
    }



    protected Object convert(Object input, Class<?> expectedClass) {
        return input;
//        if (input == null || input.getClass().equals(expectedClass)) {
//            return input;
//        }
//        System.err.println(input.getClass() + " " + expectedClass);
//        if (Long.class.equals(expectedClass) && input instanceof Integer) {
//            return Long.valueOf(((Integer) input).longValue());
//        } else if (long.class.equals(expectedClass) && input instanceof Integer) {
//            return
//        }
//        return input;
    }
    */
}
