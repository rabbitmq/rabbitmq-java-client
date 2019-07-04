// Copyright (c) 2019 Pivotal Software, Inc.  All rights reserved.
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

import org.junit.Test;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class ValueWriterTest {

    @Test(expected = IllegalArgumentException.class)
    public void writingOverlyLargeBigDecimalShouldFail()
            throws IOException {

        OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) {
            }
        };

        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

        ValueWriter valueWriter = new ValueWriter(dataOutputStream);

        valueWriter.writeFieldValue(new BigDecimal(Integer.MAX_VALUE).add(new BigDecimal(1)));

    }

    @Test(expected = IllegalArgumentException.class)
    public void writingOverlyLargeScaleInBigDecimalShouldFail()
            throws IOException {

        OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) {
            }
        };

        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

        ValueWriter valueWriter = new ValueWriter(dataOutputStream);

        valueWriter.writeFieldValue(new BigDecimal(BigInteger.ONE, 500));
    }

    @Test
    public void bigDecimalWrittenAndReadMatches() throws IOException {
        BigDecimal value = new BigDecimal(BigInteger.valueOf(56), 3);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        ValueWriter valueWriter = new ValueWriter(dataOutputStream);
        valueWriter.writeFieldValue(value);

        BigDecimal read = (BigDecimal) ValueReader.readFieldValue(new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray())));
        assertThat(read).isEqualTo(value);
    }
}
