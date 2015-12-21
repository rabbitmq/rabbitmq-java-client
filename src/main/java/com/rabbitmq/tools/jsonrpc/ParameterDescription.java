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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//


package com.rabbitmq.tools.jsonrpc;

import java.util.Collection;
import java.util.Map;

import com.rabbitmq.tools.json.JSONUtil;

/**
 * Description of a single JSON-RPC procedure parameter.
 */
public class ParameterDescription {
    /** The parameter name. */
    public String name;
    /**
     * The parameter type - one of "bit", "num", "str", "arr",
     * "obj", "any" or "nil".
     */
    public String type;

    public ParameterDescription() {
        // Nothing to do here.
    }

    public ParameterDescription(Map<String, Object> pm) {
        JSONUtil.tryFill(this, pm);
    }

    public ParameterDescription(int index, Class<?> c) {
        name = "param" + index;
        type = lookup(c);
    }

    public static String lookup(Class<?> c) {
        if (c == Void.class) return "nil";
        if (c == Boolean.class) return "bit";
        if (c == Integer.class) return "num";
        if (c == Double.class) return "num";
        if (c == String.class) return "str";
        if (c.isArray()) return "arr";
        if (Map.class.isAssignableFrom(c)) return "obj";
        if (Collection.class.isAssignableFrom(c)) return "arr";
        return "any";
    }
}
