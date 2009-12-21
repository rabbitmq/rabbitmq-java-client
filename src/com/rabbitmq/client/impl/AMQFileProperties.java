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
package com.rabbitmq.client.impl;

import java.util.Date;
import java.util.Map;
import java.util.Hashtable;

import com.rabbitmq.client.FileProperties;
import com.rabbitmq.client.StreamProperties;

public abstract class AMQFileProperties
        extends AMQContentHeader implements FileProperties {
    
    @Override
    public Object clone() throws CloneNotSupportedException {
        AMQFileProperties fpClone = (AMQFileProperties) super.clone();
        
        Map<String, Object> thisHeaders = getHeaders();
        if (thisHeaders != null) {
            Map<String, Object> headers = new Hashtable<String, Object>();
            headers.putAll(thisHeaders);
            fpClone.setHeaders(headers);
        }
        
        Date thisTimestamp = getTimestamp();
        if (thisTimestamp != null) {
            fpClone.setTimestamp((Date) thisTimestamp.clone());
        }
        
        return fpClone;
    }
}
