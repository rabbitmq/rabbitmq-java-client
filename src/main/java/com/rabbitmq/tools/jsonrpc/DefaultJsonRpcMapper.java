// Copyright (c) 2018-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

import java.util.List;
import java.util.Map;

/**
 * Simple {@link JsonRpcMapper} based on homegrown JSON utilities.
 * Handles integers, doubles, strings, booleans, and arrays of those types.
 * <p>
 * For a more comprehensive set of features, use {@link JacksonJsonRpcMapper}.
 * <p>
 * Will be removed in 6.0
 *
 * @see JsonRpcMapper
 * @see JacksonJsonRpcMapper
 * @since 5.4.0
 * @deprecated use {@link JacksonJsonRpcMapper} instead
 */
public class DefaultJsonRpcMapper implements JsonRpcMapper {

    @Override
    public JsonRpcRequest parse(String requestBody, ServiceDescription description) {
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) new JSONReader().read(requestBody);
        return new JsonRpcRequest(
            request.get("id"), request.get("version").toString(), request.get("method").toString(),
            ((List<?>) request.get("params")).toArray()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
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
        return new JsonRpcResponse(map.get("result"), map.get("error"), exception);
    }

    @Override
    public String write(Object input) {
        return new JSONWriter().write(input);
    }
}
