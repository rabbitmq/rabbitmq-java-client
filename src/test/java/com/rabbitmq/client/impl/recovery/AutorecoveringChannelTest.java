package com.rabbitmq.client.impl.recovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class AutorecoveringChannelTest {

    private AutorecoveringChannel channel;

    @Mock
    private AutorecoveringConnection autorecoveringConnection;

    @Mock
    private RecoveryAwareChannelN recoveryAwareChannelN;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.channel = new AutorecoveringChannel(autorecoveringConnection, recoveryAwareChannelN);
    }

    @Test
    void abort() {
        this.channel.abort();
        verify(recoveryAwareChannelN, times(1)).abort();
    }

    @Test
    void abortWithDetails() {
        int closeCode = 1;
        String closeMessage = "reason";
        this.channel.abort(closeCode, closeMessage);
        verify(recoveryAwareChannelN, times(1)).abort(closeCode, closeMessage);
    }

    @Test
    void abortWithDetailsCloseMessageNull() {
        int closeCode = 1;
        this.channel.abort(closeCode, null);
        verify(recoveryAwareChannelN, times(1)).abort(closeCode, "");
    }

}
