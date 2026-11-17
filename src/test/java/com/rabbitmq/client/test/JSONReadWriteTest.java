// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.rabbitmq.client.Channel;
import com.rabbitmq.tools.json.JSONReader;
import com.rabbitmq.tools.json.JSONWriter;
import com.rabbitmq.tools.jsonrpc.JsonRpcServer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class JSONReadWriteTest {

    @Test
    public void readWriteSimple() throws Exception {

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

    @Test
    @Timeout(10)
    public void truncatedInputInsideStringShouldThrow() {
        for (String json : new String[] {
            "{\"method\":\"x", "\"", "'abc", "{\"a\":\"b\\\\", "[\"x" }) {
            assertThrows(IllegalStateException.class, () -> new JSONReader().read(json), json);
        }
    }

    @Test
    @Timeout(10)
    public void truncatedLineCommentShouldThrow() {
        assertThrows(IllegalStateException.class, () -> new JSONReader().read("//"));
        assertThrows(IllegalStateException.class, () -> new JSONReader().read("// no newline"));
        // a terminated comment is still skipped
        assertEquals(1, ((java.util.Map<?, ?>) new JSONReader().read("// c\n{\"a\":1}")).get("a"));
    }

    @Test
    public void deeplyNestedInputShouldThrow() {
        assertThrows(IllegalStateException.class, () -> new JSONReader().read(nestedArrays(101)));
        assertThrows(IllegalStateException.class, () -> new JSONReader().read(nestedObjects(101)));
        assertThrows(IllegalStateException.class, () -> new JSONReader().read(nestedArrays(100_000)));
        assertThrows(IllegalStateException.class, () -> new JSONReader().read(nestedObjects(100_000)));
        // arrays and objects count towards the same limit
        assertEquals(100, depth(new JSONReader().read(mixed(50))));
        assertThrows(IllegalStateException.class, () -> new JSONReader().read("[" + mixed(50) + "]"));
    }

    @Test
    public void nestingUpToLimitShouldBeAccepted() {
        assertEquals(100, depth(new JSONReader().read(nestedArrays(100))));
        assertEquals(100, depth(new JSONReader().read(nestedObjects(100))));
        // depth is about nesting, not about the number of sibling structures
        StringBuilder siblings = new StringBuilder("[");
        for (int i = 0; i < 1000; i++) {
            siblings.append(i == 0 ? "" : ",").append(nestedArrays(50));
        }
        siblings.append("]");
        assertEquals(1000, ((List<?>) new JSONReader().read(siblings.toString())).size());
        // the reader can be reused after a failure
        JSONReader reader = new JSONReader();
        assertThrows(IllegalStateException.class, () -> reader.read(nestedArrays(101)));
        assertEquals(100, depth(reader.read(nestedArrays(100))));
    }

    @Test
    public void deeplyNestedJsonRpcRequestShouldGetBadRequestResponse() throws Exception {
        JsonRpcServer server = new JsonRpcServer(mock(Channel.class), "q", EchoService.class,
            (EchoService) input -> input);

        String response = server.doCall(
            "{\"version\":\"1.1\",\"method\":\"echo\",\"params\":[" + nestedArrays(100_000) + "]}");
        Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) new JSONReader().read(response)).get("error");
        assertEquals(400, error.get("code"));

        response = server.doCall("{\"version\":\"1.1\",\"method\":\"echo\",\"params\":[\"hello\"]}");
        assertEquals("hello", ((Map<?, ?>) new JSONReader().read(response)).get("result"));
    }

    public interface EchoService {
        String echo(String input);
    }

    private static String nestedArrays(int depth) {
        StringBuilder sb = new StringBuilder(depth * 2);
        for (int i = 0; i < depth; i++) {
            sb.append('[');
        }
        for (int i = 0; i < depth; i++) {
            sb.append(']');
        }
        return sb.toString();
    }

    private static String nestedObjects(int depth) {
        StringBuilder sb = new StringBuilder(depth * 6);
        for (int i = 0; i < depth; i++) {
            sb.append("{\"a\":");
        }
        sb.append('1');
        for (int i = 0; i < depth; i++) {
            sb.append('}');
        }
        return sb.toString();
    }

    private static String mixed(int pairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs; i++) {
            sb.append("[{\"a\":");
        }
        sb.append('1');
        for (int i = 0; i < pairs; i++) {
            sb.append("}]");
        }
        return sb.toString();
    }

    private static int depth(Object value) {
        int depth = 0;
        while (value instanceof List || value instanceof Map) {
            depth++;
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                value = list.isEmpty() ? null : list.get(0);
            } else {
                value = ((Map<?, ?>) value).get("a");
            }
        }
        return depth;
    }

}
