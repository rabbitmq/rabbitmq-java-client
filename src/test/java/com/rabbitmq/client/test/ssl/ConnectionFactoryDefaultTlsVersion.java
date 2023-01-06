// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

package com.rabbitmq.client.test.ssl;

import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConnectionFactoryDefaultTlsVersion {

    @Test public void defaultTlsVersionJdk16ShouldTakeFallback() {
        String [] supportedProtocols = {"SSLv2Hello", "SSLv3", "TLSv1"};
        String tlsProtocol = ConnectionFactory.computeDefaultTlsProtocol(supportedProtocols);
        Assertions.assertEquals("TLSv1",tlsProtocol);
    }

    @Test public void defaultTlsVersionJdk17ShouldTakePrefered() {
        String [] supportedProtocols = {"SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"};
        String tlsProtocol = ConnectionFactory.computeDefaultTlsProtocol(supportedProtocols);
        Assertions.assertEquals("TLSv1.2",tlsProtocol);
    }

    @Test public void defaultTlsVersionJdk18ShouldTakePrefered() {
        String [] supportedProtocols = {"SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"};
        String tlsProtocol = ConnectionFactory.computeDefaultTlsProtocol(supportedProtocols);
        Assertions.assertEquals("TLSv1.2",tlsProtocol);
    }

}
