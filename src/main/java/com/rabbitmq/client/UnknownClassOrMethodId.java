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


package com.rabbitmq.client;

import java.io.IOException;

/**
 * Thrown when the protocol handlers detect an unknown class number or
 * method number.
 */
public class UnknownClassOrMethodId extends IOException {
    private static final long serialVersionUID = 1L;
    private static final int NO_METHOD_ID = -1;
    public final int classId;
    public final int methodId;
    public UnknownClassOrMethodId(int classId) {
        this(classId, NO_METHOD_ID);
    }
    public UnknownClassOrMethodId(int classId, int methodId) {
        this.classId = classId;
        this.methodId = methodId;
    }
    public String toString() {
        if (this.methodId == NO_METHOD_ID) {
            return super.toString() + "<" + classId + ">";
        } else {
            return super.toString() + "<" + classId + "." + methodId + ">";
        }
    }
}
