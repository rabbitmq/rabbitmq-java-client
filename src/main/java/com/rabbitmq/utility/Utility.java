// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        @Override
        public synchronized Throwable fillInStackTrace(){
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
    
    /**
     * Synchronizes on the list and then returns a copy of the list that is safe to iterate over. Useful when wanting to do thread-safe iteration over
     * a List wrapped in {@link Collections#synchronizedList(List)}.
     *
     * @param list
     *            The list, which may not be {@code null}
     * @return ArrayList copy of the list
     */
    public static <E> List<E> copy(final List<E> list) {
        // No Sonar: this very list instance can be synchronized in other places of its owning class
        synchronized (list) { //NOSONAR
            return new ArrayList<E>(list);
        }
    }
    
    /**
     * Synchronizes on the map and then returns a copy of the map that is safe to iterate over. Useful when wanting to do thread-safe iteration over a
     * Map wrapped in {@link Collections#synchronizedMap(Map)}
     *
     * @param map
     *            The map, which may not be {@code null}
     * @return LinkedHashMap copy of the map
     */
    public static <K, V> Map<K, V> copy(final Map<K, V> map) {
        // No Sonar: this very map instance can be synchronized in other places of its owning class
        synchronized (map) { //NOSONAR
            return new LinkedHashMap<K, V>(map);
        }
    }
}
