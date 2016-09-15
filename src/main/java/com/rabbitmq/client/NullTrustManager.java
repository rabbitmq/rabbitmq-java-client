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
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        // Do nothing.
    }

    /**
     * Doesn't even bother looking at its arguments, simply returns,
     * which makes the check succeed.
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        // Do nothing.
    }

    /**
     * Always returns an empty array of X509Certificates.
     */
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
