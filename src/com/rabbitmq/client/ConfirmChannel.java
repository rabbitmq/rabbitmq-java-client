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
//  Copyright (c) 2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client;

/**
 * Public API: Interface to an AMQ channel enhanced with confirms. See the {@link Channel} and {@link ConfirmListener} documentation for details.
 *
 */

public interface ConfirmChannel extends Channel {
    /**
     * Wait until all messages published since the last call have been
     * either ack'd or nack'd by the broker.
     * @return whether all the messages were ack'd (and none were nack'd)
     */
    boolean waitForConfirms() throws InterruptedException;
}
