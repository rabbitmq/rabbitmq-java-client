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

package com.rabbitmq.client.test;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;

public class TestUtils {

    public static final boolean USE_NIO = System.getProperty("use.nio") == null ? false : true;

    public static ConnectionFactory connectionFactory() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        if(USE_NIO) {
            connectionFactory.useNio();
        } else {
            connectionFactory.useBlockingIo();
        }
        return connectionFactory;
    }

    public static void close(Connection connection) {
        if(connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static boolean isVersion37orLater(Connection connection) {
        String currentVersion = connection.getServerProperties().get("version").toString();
        // versions built from source: 3.7.0+rc.1.4.gedc5d96
        if (currentVersion.contains("+")) {
            currentVersion = currentVersion.substring(0, currentVersion.indexOf("+"));
        }
        // alpha (snapshot) versions: 3.7.0~alpha.449-1
        if (currentVersion.contains("~")) {
            currentVersion = currentVersion.substring(0, currentVersion.indexOf("~"));
        }
        return "0.0.0".equals(currentVersion) ? true : versionCompare(currentVersion, "3.7.0") >= 0;
    }

    /**
     * http://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
     *
     */
    static int versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        return Integer.signum(vals1.length - vals2.length);
    }

}
