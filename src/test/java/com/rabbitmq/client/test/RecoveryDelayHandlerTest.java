package com.rabbitmq.client.test;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import com.rabbitmq.client.RecoveryDelayHandler;
import com.rabbitmq.client.RecoveryDelayHandler.DefaultRecoveryDelayHandler;
import com.rabbitmq.client.RecoveryDelayHandler.ExponentialBackoffDelayHandler;

import org.junit.Test;

public class RecoveryDelayHandlerTest {

    @Test
    public void testDefaultRecoveryDelayHandler() {
        final RecoveryDelayHandler handler = new DefaultRecoveryDelayHandler(5000);
        assertEquals(5000L, handler.getDelay(0));
        assertEquals(5000L, handler.getDelay(1));
        assertEquals(5000L, handler.getDelay(Integer.MAX_VALUE));
    }
    
    @Test
    public void testExponentialBackoffDelayHandler_default() {
        final RecoveryDelayHandler handler = new ExponentialBackoffDelayHandler();
        assertEquals(0, handler.getDelay(0));
        assertEquals(1000L, handler.getDelay(1));
        assertEquals(1000L, handler.getDelay(2));
        assertEquals(2000L, handler.getDelay(3));
        assertEquals(3000L, handler.getDelay(4));
        assertEquals(5000L, handler.getDelay(5));
        assertEquals(8000L, handler.getDelay(6));
        assertEquals(13000L, handler.getDelay(7));
        assertEquals(21000L, handler.getDelay(8));
        assertEquals(21000L, handler.getDelay(9));
        assertEquals(21000L, handler.getDelay(Integer.MAX_VALUE));
    }
    
    @Test
    public void testExponentialBackoffDelayHandler_sequence() {
        final RecoveryDelayHandler handler = new ExponentialBackoffDelayHandler(Arrays.asList(1L, 2L));
        assertEquals(1, handler.getDelay(0));
        assertEquals(2, handler.getDelay(1));
        assertEquals(2, handler.getDelay(2));
        assertEquals(2, handler.getDelay(Integer.MAX_VALUE));
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testExponentialBackoffDelayHandler_sequence_null() {
        new ExponentialBackoffDelayHandler(null);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testExponentialBackoffDelayHandler_sequence_empty() {
        new ExponentialBackoffDelayHandler(Collections.<Long>emptyList());
    }
}
