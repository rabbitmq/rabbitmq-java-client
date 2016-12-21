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

package com.rabbitmq.client.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Publicly available Client Version information
 */
public class ClientVersion {
    /** Full version string */
    private static final Properties version;
    public static final String VERSION;

    static {
        version = new Properties();
        InputStream inputStream = ClientVersion.class.getClassLoader()
                .getResourceAsStream("version.properties");
        try {
            version.load(inputStream);
        } catch (IOException e) {
        } finally {
            try {
                if(inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
            }
        }

        VERSION = version.getProperty("com.rabbitmq.client.version",
          ClientVersion.class.getPackage().getImplementationVersion());
    }
}
