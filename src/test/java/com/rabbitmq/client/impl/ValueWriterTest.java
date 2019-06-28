package com.rabbitmq.client.impl;

import org.junit.Test;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

public class ValueWriterTest {
    @Test(expected = IllegalArgumentException.class) public void writingOverlyLargeBigDecimalShouldFail()
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

    @Test(expected = IllegalArgumentException.class) public void writingOverlyLargeScaleInBigDecimalShouldFail()
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
}
