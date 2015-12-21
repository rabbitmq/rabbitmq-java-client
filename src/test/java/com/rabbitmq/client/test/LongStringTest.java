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
