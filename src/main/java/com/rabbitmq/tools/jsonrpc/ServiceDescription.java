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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.tools.json.JSONUtil;

/**
 * Description of a JSON-RPC service.
 */
public class ServiceDescription {
    public static final String JSON_RPC_VERSION = "1.1";

    /** The service name */
    public String name;
    /** ID for the service */
    public String id;
    /** Version of the service */
    public String version;
    /** Human-readable summary for the service */
    public String summary;
    /** Human-readable instructions for how to get information on the service's operation */
    public String help;

    /** Map from procedure name to {@link ProcedureDescription} */
    private Map<String, ProcedureDescription> procedures;

    public ServiceDescription(Map<String, Object> rawServiceDescription) {
        JSONUtil.tryFill(this, rawServiceDescription);
    }

    public ServiceDescription(Class<?> klass) {
        this.procedures = new HashMap<String, ProcedureDescription>();
        for (Method m: klass.getMethods()) {
            ProcedureDescription proc = new ProcedureDescription(m);
            addProcedure(proc);
        }
    }

    public ServiceDescription() {
        // No work to do here
    }

    /** Gets a collection of all {@link ProcedureDescription} for this service */
    public Collection<ProcedureDescription> getProcs() {
        return procedures.values();
    }

    /** Private API - used via reflection during parsing/loading */
    public void setProcs(Collection<Map<String, Object>> p) {
        procedures = new HashMap<String, ProcedureDescription>();
        for (Map<String, Object> pm: p) {
            ProcedureDescription proc = new ProcedureDescription(pm);
            addProcedure(proc);
        }
    }

    /** Private API - used during initialization */
    private void addProcedure(ProcedureDescription proc) {
        procedures.put(proc.name + "/" + proc.arity(), proc);
    }

    /**
     * Looks up a single ProcedureDescription by name and arity.
     * @return non-null ProcedureDescription if a match is found
     * @throws IllegalArgumentException if no match is found
     */
    public ProcedureDescription getProcedure(String newname, int arity) {
        ProcedureDescription proc = procedures.get(newname + "/" + arity);
        if (proc == null) {
            throw new IllegalArgumentException("Procedure not found: " + newname +
                                               ", arity " + arity);
        }
        return proc;
    }
}
