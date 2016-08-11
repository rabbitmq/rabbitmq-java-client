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

import com.rabbitmq.client.LongString;
import com.rabbitmq.client.impl.LongStringHelper;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.UnsupportedEncodingException;

public class LongStringTest extends TestCase {
public static TestSuite suite()
    {
        TestSuite suite = new TestSuite("longString");
        suite.addTestSuite(LongStringTest.class);
        return suite;
    }

    public void testToString() throws UnsupportedEncodingException {
        String s = "abcdef";
        LongString ls = LongStringHelper.asLongString(s);

        assertTrue(ls.toString().equals(s));
        assertTrue(ls.toString().equals(new String(ls.getBytes(), "UTF-8")));
    }
}
