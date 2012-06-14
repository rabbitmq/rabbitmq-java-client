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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.examples.perf;

/**
 * Named "runnable" that generates {@link ScenarioStats}
 */
public interface Scenario {
    /**
     * @return name of scenario
     */
    public String getName();
    /**
     * Execute scenario
     * @throws Exception possible failure outcome
     */
    public void run() throws Exception;
    /**
     * @return statistics from the scenario run
     */
    public ScenarioStats getStats();
}
