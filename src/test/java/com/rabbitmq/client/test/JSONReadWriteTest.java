// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

import com.rabbitmq.tools.json.JSONReader;
import com.rabbitmq.tools.json.JSONWriter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JSONReadWriteTest {

    @Test public void readWriteSimple() throws Exception {

        Object myRet;
        String myJson;

        // simple string
        myRet = new JSONReader().read(myJson = new JSONWriter().write("blah"));
        assertEquals("blah", myRet);

        // simple int
        myRet = new JSONReader().read(myJson = new JSONWriter().write(1));
        assertEquals(1, myRet);

        // string with double quotes
        myRet = new JSONReader().read(myJson = new JSONWriter().write("t1-blah\"blah"));
        assertEquals("t1-blah\"blah", myRet);
        // string with single quotes
        myRet = new JSONReader().read(myJson = new JSONWriter().write("t2-blah'blah"));
        assertEquals("t2-blah'blah", myRet);
        // string with two double quotes
        myRet = new JSONReader().read(myJson = new JSONWriter().write("t3-blah\"n\"blah"));
        assertEquals("t3-blah\"n\"blah", myRet);
        // string with two single quotes
        myRet = new JSONReader().read(myJson = new JSONWriter().write("t4-blah'n'blah"));
        assertEquals("t4-blah'n'blah", myRet);
        // string with a single and a double quote
        myRet = new JSONReader().read(myJson = new JSONWriter().write("t4-blah'n\"blah"));
        assertEquals("t4-blah'n\"blah", myRet);

        // UTF-8 character
        myRet = new JSONReader().read(myJson = new JSONWriter().write("smile \u9786"));
        assertEquals("smile \u9786", myRet);

        // null byte
        myRet = new JSONReader().read(myJson = new JSONWriter().write("smile \u0000"));
        assertEquals("smile \u0000", myRet);

    }

    @Test public void moreComplicated() throws Exception {

        String v, s;
        Object t;

        s = "[\"foo\",{\"bar\":[\"baz\",null,1.0,2]}]";
        v = new JSONWriter().write(new JSONReader().read(s));
        assertEquals(s, v);

        s = "[\"foo\",{\"bar\":[\"b\\\"az\",null,1.0,2]}]";
        t = new JSONReader().read(s);
        v = new JSONWriter().write(t);
        assertEquals(s, v);

        s = "[\"foo\",{\"bar\":[\"b'az\",null,1.0,2]}]";
        v = new JSONWriter().write(new JSONReader().read(s));
        assertEquals(s, v);

        s = "[\"foo\",{\"bar\":[\"b'a'z\",null,1.0,2]}]";
        v = new JSONWriter().write(new JSONReader().read(s));
        assertEquals(s, v);

        s = "[\"foo\",{\"bar\":[\"b\\\"a\\\"z\",null,1.0,2]}]";
        v = new JSONWriter().write(new JSONReader().read(s));
        assertEquals(s, v);

    }

    @Test public void badJSON() throws Exception {

        try {
            new JSONReader().read("[\"foo\",{\"bar\":[\"b\"az\",null,1.0,2]}]");
            fail("Should not have parsed");
        }
        catch (IllegalStateException e) {}

        try {
            new JSONReader().read("[\"foo\",{\"bar\":[\"b\"a\"z\",null,1.0,2]}]");
            fail("Should not have parsed");
        }
        catch (IllegalStateException e) {}

    }

}
