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


package com.rabbitmq.client.test.ssl;

import junit.framework.TestSuite;

import com.rabbitmq.client.test.AbstractRMQTestSuite;

public class SSLTests extends AbstractRMQTestSuite {
    public static TestSuite suite() {
        TestSuite suite = new TestSuite("ssl");
        suite.addTestSuite(ConnectionFactoryDefaultTlsVersion.class);
        // Skip the tests if not under umbrella
        if (!requiredProperties()) return suite;
        if (!isSSLAvailable()) return suite;
        suite.addTestSuite(UnverifiedConnection.class);
        suite.addTestSuite(VerifiedConnection.class);
        suite.addTestSuite(BadVerifiedConnection.class);
        return suite;
    }
}
