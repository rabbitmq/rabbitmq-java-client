// The contents of this file are subject to the Mozilla Public License
// Version 1.1 (the "License"); you may not use this file except in
// compliance with the License. You may obtain a copy of the License
// at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS"
// basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
// the License for the specific language governing rights and
// limitations under the License.
//
// The Original Code is RabbitMQ.
//
// The Initial Developer of the Original Code is VMware, Inc.
// Copyright (c) 2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.rpc;

/**
 * This {@link RuntimeException} indicates errors that occur in the handling code on the
 * server-side. This is public for clients to catch, but the constructor is private and there is a
 * package-private factory method for Rpc code use.
 */
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 2710614347027245189L;

    private ServiceException(String reason) {
        super(reason);
    }

    static ServiceException newServiceException(String reason, Throwable cause) {
        return (ServiceException) newServiceException(reason).initCause(cause);
    }

    static ServiceException newServiceException(String reason) {
        return new ServiceException(reason);
    }
}
