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
    private String name;
    /** ID for the service */
    private String id;
    /** Version of the service */
    private String version;
    /** Human-readable summary for the service */
    private String summary;
    /** Human-readable instructions for how to get information on the service's operation */
    private String help;

    /** Map from procedure name to {@link ProcedureDescription} */
    private Map<String, ProcedureDescription> procedures;

    public ServiceDescription(Map<String, Object> rawServiceDescription) {
        JSONUtil.tryFill(this, rawServiceDescription);
    }

    public ServiceDescription(Class<?> klass) {
        this.procedures = new HashMap<>();
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
        procedures = new HashMap<>();
        for (Map<String, Object> pm: p) {
            ProcedureDescription proc = new ProcedureDescription(pm);
            addProcedure(proc);
        }
    }

    /** Private API - used during initialization */
    private void addProcedure(ProcedureDescription proc) {
        procedures.put(proc.getName() + "/" + proc.arity(), proc);
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

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getSummary() {
        return summary;
    }

    public String getHelp() {
        return help;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setHelp(String help) {
        this.help = help;
    }
}
