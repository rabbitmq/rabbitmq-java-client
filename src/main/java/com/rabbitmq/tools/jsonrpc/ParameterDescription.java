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

import java.util.Collection;
import java.util.Map;

import com.rabbitmq.tools.json.JSONUtil;

/**
 * Description of a single JSON-RPC procedure parameter.
 */
public class ParameterDescription {
    /** The parameter name. */
    private String name;
    /**
     * The parameter type - one of "bit", "num", "str", "arr",
     * "obj", "any" or "nil".
     */
    private String type;

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

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }
}
