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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.server;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

import com.rabbitmq.client.test.functional.ExchangeEquivalenceBase;

public class AlternateExchangeEquivalence extends ExchangeEquivalenceBase {
    static Map<String, Object> args = new HashMap<String, Object>();
    {
        args.put("alternate-exchange", "UME");
    }

    public void testAlternateExchangeEquivalence() throws IOException {
        channel.exchangeDeclare("alternate", "direct", false, false, args);
        verifyEquivalent("alternate", "direct", false, false, args);
    }

    public void testAlternateExchangeNonEquivalence() throws IOException {
        channel.exchangeDeclare("alternate", "direct", false, false, args);
        Map<String, Object> altargs = new HashMap<String, Object>();
        altargs.put("alternate-exchange", "somewhere");
        verifyNotEquivalent("alternate", "direct", false, false, altargs);
    }
}
