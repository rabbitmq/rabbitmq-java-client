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
