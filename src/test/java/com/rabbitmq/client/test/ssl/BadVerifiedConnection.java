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

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for bug 19356 - SSL Support in rabbitmq
 *
 */
@EnabledForJreRange(min = JRE.JAVA_11)
public class BadVerifiedConnection extends UnverifiedConnection {

    public void openConnection()
            throws IOException, TimeoutException {
        try {
            SSLContext c = TlsTestUtils.badVerifiedSslContext();
            connectionFactory.useSslProtocol(c);
        } catch (Exception ex) {
            throw new IOException(ex);
        }

        try {
            connection = connectionFactory.newConnection();
            fail();
        } catch (SSLHandshakeException ignored) {
        } catch (IOException e) {
            fail();
        }
    }

    public void openChannel() {}
    @Test public void sSL() {}
}
