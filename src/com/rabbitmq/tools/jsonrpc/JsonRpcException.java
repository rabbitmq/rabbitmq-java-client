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
        // no work needed in default no-argconstructor
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
