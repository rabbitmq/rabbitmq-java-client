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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;

public class UsernamePasswordCallbackHandler implements CallbackHandler {
    private ConnectionFactory factory;
    public UsernamePasswordCallbackHandler(ConnectionFactory factory) {
        this.factory = factory;
    }

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback: callbacks) {
            if (callback instanceof NameCallback) {
                NameCallback nc = (NameCallback)callback;
                nc.setName(factory.getUsername());

            } else if (callback instanceof PasswordCallback) {
                PasswordCallback pc = (PasswordCallback)callback;
                pc.setPassword(factory.getPassword().toCharArray());

            } else {
                throw new UnsupportedCallbackException
                        (callback, "Unrecognized Callback");
            }
        }
    }
}
