//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.utility;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Catch-all holder class for static helper methods.
 */

public class Utility {
    static class ThrowableCreatedElsewhere extends Throwable {
        /** Default for non-checking. */
        private static final long serialVersionUID = 1L;

        public ThrowableCreatedElsewhere(Throwable throwable) {
          super(throwable.getClass() + " created elsewhere");
          this.setStackTrace(throwable.getStackTrace());
        }

        @Override public Throwable fillInStackTrace(){
            return this;
        }
    }

    public static <T extends Throwable & SensibleClone<T>> T fixStackTrace(T throwable) {
      throwable = throwable.sensibleClone();

      if(throwable.getCause() == null) {
        // We'd like to preserve the original stack trace in the cause.
        // Unfortunately Java doesn't let you set the cause once it's been
        // set once. This means we have to choose between either
        //  - not preserving the type
        //  - sometimes losing the original stack trace
        //  - performing nasty reflective voodoo which may or may not work
        // We only lose the original stack trace when there's a root cause
        // which will hopefully be enlightening enough on its own that it
        // doesn't matter too much.
        try {
          throwable.initCause(new ThrowableCreatedElsewhere(throwable));
        } catch(IllegalStateException e) {
          // This exception was explicitly initialised with a null cause.
          // Alas this means we can't set the cause even though it has none.
          // Thanks.
        }
      }


      throwable.fillInStackTrace();
      // We want to remove fixStackTrace from the trace.
      StackTraceElement[] existing = throwable.getStackTrace();
      StackTraceElement[] newTrace = new StackTraceElement[existing.length - 1];
      System.arraycopy(existing, 1, newTrace, 0, newTrace.length);
      throwable.setStackTrace(newTrace);
      return throwable;
    }


    public static String makeStackTrace(Throwable throwable) {
        ByteArrayOutputStream baOutStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(baOutStream, false);
        throwable.printStackTrace(printStream);
        printStream.flush(); // since we don't automatically do so
        String text = baOutStream.toString();
        printStream.close(); // closes baOutStream
        return text;
    }
}
