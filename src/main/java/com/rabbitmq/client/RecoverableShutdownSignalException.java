package com.rabbitmq.client;

public class RecoverableShutdownSignalException extends ShutdownSignalException {

    /** Default for non-checking. */
    private static final long serialVersionUID = 1L;

    private final boolean _recoveryToBeAttempted;
    
	public RecoverableShutdownSignalException(boolean hardError, boolean initiatedByApplication, Method reason,
			Object ref) {
		this(hardError, initiatedByApplication, reason, ref, "", null, false);
	}
	
    /**
     * Construct a RecoverableShutdownSignalException from the arguments.
     * @param hardError the relevant hard error
     * @param initiatedByApplication if the shutdown was client-initiated
     * @param reason AMQP method describing the exception reason
     * @param ref Reference to Connection or Channel that fired the signal
     * @param messagePrefix prefix to add to exception message
     */
    public RecoverableShutdownSignalException(boolean hardError,
                                   boolean initiatedByApplication,
                                   Method reason, Object ref, String messagePrefix, Throwable cause)
    {
        this(hardError, initiatedByApplication, reason, ref, messagePrefix, cause, false);
    }
    
    public RecoverableShutdownSignalException(ShutdownSignalException sse, boolean recoveryToBeAttempted) {
    	this(sse.isHardError(), sse.isInitiatedByApplication(), sse.getReason(), sse.getReference(), sse.getMessagePrefix(), sse.getCause(), recoveryToBeAttempted);
    }

    public RecoverableShutdownSignalException(boolean hardError,
            boolean initiatedByApplication,
            Method reason, Object ref, String messagePrefix, Throwable cause, boolean recoveryToBeAttempted)
    {
    	super(hardError, initiatedByApplication, reason, ref, messagePrefix, cause);
    	this._recoveryToBeAttempted = recoveryToBeAttempted;
	}

	public boolean isRecoveryToBeAttempted() {
		return _recoveryToBeAttempted;
	}
}
