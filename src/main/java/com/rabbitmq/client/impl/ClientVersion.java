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

    private static final String getVersionFromPropertyFile() throws Exception {
        InputStream inputStream = ClientVersion.class.getClassLoader().getResourceAsStream("rabbitmq-amqp-client.properties");
        Properties version = new Properties();
        try {
            version.load(inputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        if (version.getProperty("com.rabbitmq.client.version") == null) {
            throw new IllegalStateException("Coulnd't find version property in property file");
        }
        return version.getProperty("com.rabbitmq.client.version");
    }

    private static final String getVersionFromPackage() {
        if (ClientVersion.class.getPackage().getImplementationVersion() == null) {
            throw new IllegalStateException("Couldn't get version with Package#getImplementationVersion");
        }
        return ClientVersion.class.getPackage().getImplementationVersion();
    }

    private static final String getDefaultVersion() {
        return "0.0.0";
    }
}
