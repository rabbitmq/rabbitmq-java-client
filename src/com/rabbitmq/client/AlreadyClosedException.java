package com.rabbitmq.client;

/*
 * Thrown when application tries to perform an action on connection/channel
 * which was already closed
 */
public class AlreadyClosedException extends ShutdownSignalException {
	public AlreadyClosedException(String s)
	{
		super(true, true, s);
	}
}
