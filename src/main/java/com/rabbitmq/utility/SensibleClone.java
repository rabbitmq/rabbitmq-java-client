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

/** 
 * This interface exists as a workaround for the annoyingness of java.lang.Cloneable.
 * It is used for generic methods which need to accept something they can actually clone
 * (Object.clone is protected and java.lang.Cloneable does not define a public clone method)
 * and want to provide some guarantees of the type of the cloned object. 
 */
public interface SensibleClone<T extends SensibleClone<T>> extends Cloneable {

  /**
   * Like Object.clone but sensible; in particular, public and declared to return
   * the right type. 
   */
  public T sensibleClone();
}
