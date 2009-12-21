//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//
package com.rabbitmq.client.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.client.impl.TruncatedInputStream;

/**
 * Some basic (retroactive) tests for TruncatedInputStream.
 */
public class TruncatedInputStreamTest extends TestCase {

    /** a sample truncated stream to run tests on */
    private TruncatedInputStream _truncStream;

    /** sample data to truncate */
    private static final byte[] TEST_BYTES = new byte[] { 5, 4, 3, 2, 1 };

    /** what length to truncate it to */
    private static final int TRUNCATED_LENGTH = 3;

    @Override protected void setUp() throws Exception {
        super.setUp();
        InputStream baseStream = new ByteArrayInputStream(TEST_BYTES);
        _truncStream = new TruncatedInputStream(baseStream, TRUNCATED_LENGTH);
    }

    @Override protected void tearDown() throws Exception {
        _truncStream = null;
        super.tearDown();
    }

    public static TestSuite suite() {
        TestSuite suite = new TestSuite("truncStreams");
        suite.addTestSuite(TruncatedInputStreamTest.class);
        return suite;
    }
    
    /**
     * Check the amount of data initially available is as it should be
     * @throws IOException if there is an I/O problem
     */
    public void testAmountInitiallyAvailable() throws IOException {
        assertEquals(TRUNCATED_LENGTH, _truncStream.available());
    }

    /**
     * Check the data read from the truncated stream is as it should be
     * @throws IOException if there is an I/O problem
     */
    public void testReadTruncatedBytes() throws IOException {
        byte[] readBytes = new byte[TEST_BYTES.length];
        int numRead = _truncStream.read(readBytes);
        assertEquals(TRUNCATED_LENGTH, numRead);
        for (int i = 0; i < TRUNCATED_LENGTH; i++) {
            assertEquals(TEST_BYTES[i], readBytes[i]);
        }
    }

    /**
     * Check single-byte reads behave as they should 
     * @throws IOException 
     *
     */
    public void testSingleByteReads() throws IOException {
        for (int i = 0; i < TRUNCATED_LENGTH; i++) {
            assertEquals(TEST_BYTES[i], _truncStream.read());
        }
        assertEquals(-1, _truncStream.read());
    }

    private static final int TEST_OFFSET = 4;

    private static final int TEST_LENGTH = 2;

    /**
     * Check reading a specified number of bytes at an offset  gives the right result
     */
    public void testOffsetMultipleByteReads() throws IOException {
        byte[] readBytes = new byte[TEST_OFFSET + TEST_LENGTH];
        _truncStream.read(readBytes, TEST_OFFSET, TEST_LENGTH);
        for (int i = 0; i < TEST_OFFSET; i++) { // check the array's initially blank...
            assertEquals(0, readBytes[i]);
        }
        for (int i = 0; i < TEST_LENGTH; i++) { // ... and the rest of it a copy of the input
            assertEquals(TEST_BYTES[i], readBytes[TEST_OFFSET + i]);
        }
    }
}

