//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//
package com.rabbitmq.client;

/**
 * Encapsulates a shutdown condition for a connection to an AMQP broker.
 * Depending on HardError when calling
 * {@link com.rabbitmq.client.ShutdownSignalException#getReference()} we will
 * either get a reference to the Connection or Channel instance that fired
 * this exception.
 */

public class ShutdownSignalException extends RuntimeException {
    /** True if the connection is shut down, or false if this signal refers to a channel */
    private final boolean _hardError;

    /**
     * True if this exception is caused by explicit application
     * action; false if it originated with the broker or as a result
     * of detectable non-deliberate application failure
     */
    private final boolean _initiatedByApplication;

    /** Possible explanation */
    private final Object _reason;

    /** Either Channel or Connection instance, depending on _hardError */
    private final Object _ref;

    /**
     * Construct a ShutdownSignalException from the arguments.
     * @param hardError the relevant hard error
     * @param initiatedByApplication if the shutdown was client-initiated
     * @param reason Object describing the origin of the exception
     * @param ref Reference to Connection or Channel that fired the signal
     */
    public ShutdownSignalException(boolean hardError,
                                   boolean initiatedByApplication,
                                   Object reason, Object ref)
    {
        this._hardError = hardError;
        this._initiatedByApplication = initiatedByApplication;
        this._reason = reason;
        // Depending on hardError what we got is either Connection or Channel reference
        this._ref = ref;
    }

    /** @return true if this signals a connection error, or false if a channel error */
    public boolean isHardError() { return _hardError; }

    /** @return true if this exception was caused by explicit application
     * action; false if it originated with the broker or as a result
     * of detectable non-deliberate application failure
     */
    public boolean isInitiatedByApplication() { return _initiatedByApplication; }

    /** @return the reason object, if any */
    public Object getReason() { return _reason; }

    /** @return Reference to Connection or Channel object that fired the signal **/
    public Object getReference() { return _ref; }

    public String toString() {
        return super.toString() + " (" +
            (_initiatedByApplication
                    ? ("clean " + (_hardError ? "connection" : "channel") + " shutdown")
             : ((_hardError ? "connection" : "channel") + " error"))
            + "; reason: " + _reason + ")";
    }
}


