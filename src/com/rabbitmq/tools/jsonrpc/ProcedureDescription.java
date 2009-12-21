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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.rabbitmq.tools.json.JSONUtil;

/**
 * Description of a single JSON-RPC procedure.
 */
public class ProcedureDescription {
    /** Procedure name */
    public String name;
    /** Human-readable procedure summary */
    public String summary;
    /** Human-readable instructions for how to get information on the procedure's operation */
    public String help;
    /** True if this procedure is idempotent, that is, can be accessed via HTTP GET */
    public boolean idempotent;

    /** Descriptions of parameters for this procedure */
    private ParameterDescription[] params;
    /** Return type for this procedure */
    private String returnType;

    /** Reflected method object, used for service invocation */
    private Method method;

    public ProcedureDescription(Map<String, Object> pm) {
        JSONUtil.tryFill(this, pm);

        List<Map<String, Object>> p = (List) pm.get("params");
        params = new ParameterDescription[p.size()];
        int count = 0;
        for (Map<String, Object> param_map: p) {
            ParameterDescription param = new ParameterDescription(param_map);
            params[count++] = param;
        }
    }

    public ProcedureDescription(Method m) {
        this.method = m;
        this.name = m.getName();
        this.summary = "";
        this.help = "";
        this.idempotent = false;
        Class[] parameterTypes = m.getParameterTypes();
        this.params = new ParameterDescription[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            params[i] = new ParameterDescription(i, parameterTypes[i]);
        }
        this.returnType = ParameterDescription.lookup(m.getReturnType());
    }

    public ProcedureDescription() {
        // no work to do here
    }

    /** Getter for return type */
    public String getReturn() { return returnType; }
    /** Private API - used via reflection during parsing/loading */
    public void setReturn(String value) { returnType = value; }

    /** Private API - used to get the reflected method object, for servers */
    public Method internal_getMethod() { return method; }

    /** Gets an array of parameter descriptions for all this procedure's parameters */
    public ParameterDescription[] internal_getParams() {
        return params;
    }

    /** Retrieves the parameter count for this procedure */
    public int arity() {
        return (params == null) ? 0 : params.length;
    }

    public ParameterDescription[] getParams() {
        return params;
    }
}
