package com.rabbitmq.client.impl;

import org.junit.Test;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Queue;

public class ValueWriterTest {
    @Test(expected = IllegalArgumentException.class) public void writingOverlyLargeBigDecimalShouldFail() throws IOException {
        Queue<Byte> queue = new ArrayDeque<>();

        OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) {
                queue.add((byte) b);
            }
        };

        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

        ValueWriter valueWriter = new ValueWriter(dataOutputStream);

        valueWriter.writeFieldValue(new BigDecimal(Integer.MAX_VALUE).add(new BigDecimal(1)));

    }
}
