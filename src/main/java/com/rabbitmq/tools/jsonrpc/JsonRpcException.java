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

import java.util.Map;

import com.rabbitmq.tools.json.JSONWriter;

/**
 * Thrown when a JSON-RPC service indicates an error occurred during a call.
 */
public class JsonRpcException extends Exception {
    /**
     * Default serialized version ID
     */
    private static final long serialVersionUID = 1L;
    /** Usually the constant string, "JSONRPCError" */
    public String name;
    /** Error code */
    public int code;
    /** Error message */
    public String message;
    /** Error detail object - may not always be present or meaningful */
    public Object error;

    public JsonRpcException() {
        // no work needed in default no-arg constructor
    }

    public JsonRpcException(Map<String, Object> errorMap) {
	super(new JSONWriter().write(errorMap));
	name = (String) errorMap.get("name");
	code = 0;
	if (errorMap.get("code") != null) { code = ((Integer) errorMap.get("code")); }
	message = (String) errorMap.get("message");
	error = errorMap.get("error");
    }
}
