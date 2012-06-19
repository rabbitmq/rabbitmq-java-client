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


package com.rabbitmq.examples;

/**
 * Interface for service offered by {@link HelloJsonServer} and used by {@link HelloJsonClient}.
 * @see HelloJsonServer
 * @see HelloJsonClient
 */
public interface HelloJsonService {
    /**
     * @param name of greeter
     * @return greeting to greeter
     */
    String greeting(String name);

    /**
     * @param values list of {@link Integer}s to sum
     * @return sum of integers in list
     */
    int sum(java.util.List<Integer> values);
}
