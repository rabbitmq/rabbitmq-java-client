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


package com.rabbitmq.client;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * Convenience class providing a default implementation of javax.net.ssl.X509TrustManager.
 * Trusts every single certificate presented to it.
 */
public class NullTrustManager implements X509TrustManager {
    /**
     * Doesn't even bother looking at its arguments, simply returns,
     * which makes the check succeed.
     */
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        // Do nothing.
    }

    /**
     * Doesn't even bother looking at its arguments, simply returns,
     * which makes the check succeed.
     */
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        // Do nothing.
    }

    /**
     * Always returns an empty array of X509Certificates.
     */
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
