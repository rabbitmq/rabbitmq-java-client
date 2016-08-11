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


package com.rabbitmq.client;

import java.io.IOException;

/**
 * Encapsulates a frame format error at the wire level.
 */
public class MalformedFrameException extends IOException {
    /** Standard serialization ID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiate a MalformedFrameException.
     * @param reason a string describing the exception
     */
    public MalformedFrameException(String reason) {
        super(reason);
    }
}
