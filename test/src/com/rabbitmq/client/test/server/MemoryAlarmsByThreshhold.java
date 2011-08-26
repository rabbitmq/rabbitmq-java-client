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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.server;

import java.io.IOException;

import com.rabbitmq.tools.Host;

public class MemoryAlarmsByThreshhold extends MemoryAlarms {

    @Override
    protected void setMemoryAlarm() throws IOException, InterruptedException {
        Host.rabbitmqctl("set_vm_memory_high_watermark " + java.lang.Double.MIN_NORMAL);
        Thread.sleep(1100);
    }

    @Override
    protected void clearMemoryAlarm() throws IOException, InterruptedException {
        Host.rabbitmqctl("set_vm_memory_high_watermark 0.4");
        Thread.sleep(1100);
    }

    @Override
    protected void tearDown() throws IOException {
        super.tearDown();
        Host.executeCommand("cd ../rabbitmq-test; make restart-app");
    }

}
