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
 * This {@link RuntimeException} indicates errors that occur in the Rpc system code (either
 * server-side or client-side). This is public for clients to catch, but the constructor is private
 * and there is a package-private factory method for Rpc code use.
 */
public class RpcException extends RuntimeException {

    private static final long serialVersionUID = 4702867111442500900L;

    /** Header used in reply message properties to identify exception responses */
    public static final String RPC_EXCEPTION_HEADER = "RPC-EXCEPTION";
    /** Value used in RPC_EXCEPTION_HEADER to identify {@link RpcException}s */
    public static final String RPC_EXCEPTION_HEADER_RPC_EXCEPTION = "RpcException";
    /** Value used in RPC_EXCEPTION_HEADER to identify {@link ServiceException}s */
    public static final String RPC_EXCEPTION_HEADER_SERVICE_EXCEPTION = "ServiceException";

    private RpcException(String reason) {
        super(reason);
    }

    static RpcException newRpcException(String reason, Throwable cause) {
        return (RpcException) newRpcException(reason).initCause(cause);
    }

    static RpcException newRpcException(String reason) {
        return new RpcException(reason);
    }
}
