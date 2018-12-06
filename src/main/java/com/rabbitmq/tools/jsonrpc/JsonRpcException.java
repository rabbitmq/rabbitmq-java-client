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

/**
 * Thrown when a JSON-RPC service indicates an error occurred during a call.
 */
public class JsonRpcException extends Exception {

    /**
     * Default serialized version ID
     */
    private static final long serialVersionUID = 1L;
    /**
     * Usually the constant string, "JSONRPCError"
     */
    private final String name;
    /**
     * Error code
     */
    private final int code;
    /**
     * Error message
     */
    private final String message;
    /**
     * Error detail object - may not always be present or meaningful
     */
    private final Object error;

    public JsonRpcException() {
        this.name = null;
        this.code = -1;
        this.message = null;
        this.error = null;
    }

    public JsonRpcException(String detailMessage, String name, int code, String message, Object error) {
        super(detailMessage);
        this.name = name;
        this.code = code;
        this.message = message;
        this.error = error;
    }

    public String getName() {
        return name;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public Object getError() {
        return error;
    }
}
