//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//


package com.rabbitmq.client.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.rabbitmq.tools.json.JSONWriter;
import com.rabbitmq.tools.json.JSONReader;

import junit.framework.TestCase;

public class JSONReadWriteTest extends TestCase {

    public void testReadWriteSimple() throws Exception {

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

    public void testMoreComplicated() throws Exception {

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

    public void testBadJSON() throws Exception {

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
