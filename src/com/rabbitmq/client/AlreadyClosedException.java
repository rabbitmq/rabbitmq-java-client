package com.rabbitmq.client;

public class AlreadyClosedException extends IllegalStateException {
	
	public AlreadyClosedException()
	{
		super();
	}
	
	public AlreadyClosedException(Throwable e)
	{
		super(e);
	}

	public AlreadyClosedException(String s)
	{
		super(s);
	}
}
