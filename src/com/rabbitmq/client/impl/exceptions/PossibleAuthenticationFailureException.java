package com.rabbitmq.client.impl.exceptions;

import java.io.IOException;

/**
 * Thrown when the likely cause is an authentication failure.
 */
public class PossibleAuthenticationFailureException extends IOException
{
    public PossibleAuthenticationFailureException(Throwable cause)
    {
        super("Possibly caused by authentication failure",
              cause);
    }
}
