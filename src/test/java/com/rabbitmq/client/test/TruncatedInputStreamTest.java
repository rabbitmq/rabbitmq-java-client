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

import com.rabbitmq.client.impl.TruncatedInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

/**
 * Some basic (retroactive) tests for TruncatedInputStream.
 */
public class TruncatedInputStreamTest {

    /** a sample truncated stream to run tests on */
    private TruncatedInputStream _truncStream;

    /** sample data to truncate */
    private static final byte[] TEST_BYTES = new byte[] { 5, 4, 3, 2, 1 };

    /** what length to truncate it to */
    private static final int TRUNCATED_LENGTH = 3;

    @Before public void setUp() throws Exception {
        InputStream baseStream = new ByteArrayInputStream(TEST_BYTES);
        _truncStream = new TruncatedInputStream(baseStream, TRUNCATED_LENGTH);
    }

    @After public void tearDown() throws Exception {
        _truncStream = null;
    }
    
    /**
     * Check the amount of data initially available is as it should be
     * @throws IOException if there is an I/O problem
     */
    @Test public void amountInitiallyAvailable() throws IOException {
        assertEquals(TRUNCATED_LENGTH, _truncStream.available());
    }

    /**
     * Check the data read from the truncated stream is as it should be
     * @throws IOException if there is an I/O problem
     */
    @Test public void readTruncatedBytes() throws IOException {
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
    @Test public void singleByteReads() throws IOException {
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
    @Test public void offsetMultipleByteReads() throws IOException {
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

