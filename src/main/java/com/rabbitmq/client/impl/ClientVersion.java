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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Publicly available Client Version information
 */
public class ClientVersion {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientVersion.class);

    // We store the version property in an unusual way because relocating the package can rewrite the key in the property
    // file, which results in spurious warnings being emitted at start-up.
    // see https://github.com/rabbitmq/rabbitmq-java-client/issues/436
    private static final char[] VERSION_PROPERTY = new char[] {'c', 'o', 'm', '.', 'r', 'a', 'b', 'b', 'i', 't', 'm', 'q', '.',
            'c', 'l', 'i', 'e', 'n', 't', '.', 'v', 'e', 'r', 's', 'i', 'o', 'n'};

    public static final String VERSION;

    static {
        String version;
        try {
            version = getVersionFromPropertyFile();
        } catch (Exception e1) {
            LOGGER.warn("Couldn't get version from property file", e1);
            try {
                version = getVersionFromPackage();
            } catch (Exception e2) {
                LOGGER.warn("Couldn't get version with Package#getImplementationVersion", e1);
                version = getDefaultVersion();
            }
        }
        VERSION = version;
    }

    private static String getVersionFromPropertyFile() throws Exception {
        InputStream inputStream = ClientVersion.class.getClassLoader().getResourceAsStream("rabbitmq-amqp-client.properties");
        Properties version = new Properties();
        try {
            version.load(inputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        String propertyName = new String(VERSION_PROPERTY);
        String versionProperty = version.getProperty(propertyName);
        if (versionProperty == null) {
            throw new IllegalStateException("Couldn't find version property in property file");
        }
        return versionProperty;
    }

    private static String getVersionFromPackage() {
        if (ClientVersion.class.getPackage().getImplementationVersion() == null) {
            throw new IllegalStateException("Couldn't get version with Package#getImplementationVersion");
        }
        return ClientVersion.class.getPackage().getImplementationVersion();
    }

    private static String getDefaultVersion() {
        return "0.0.0";
    }
}
