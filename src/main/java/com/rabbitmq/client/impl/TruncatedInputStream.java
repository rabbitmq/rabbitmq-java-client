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


package com.rabbitmq.client.impl;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility stream: proxies another stream, making it appear to be no
 * longer than a preset limit.
 */
public class TruncatedInputStream extends FilterInputStream {
    private final long limit;

    private long counter = 0L;

    private long mark = 0L;

    public TruncatedInputStream(InputStream in, long limit) {
        super(in);
        this.limit = limit;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(limit - counter, super.available());
    }

    @Override
    public synchronized void mark(int readlimit) {
        super.mark(readlimit);
        mark = counter;
    }

    @Override
    public int read() throws IOException {
        if (counter < limit) {
            int result = super.read();
            if (result >= 0)
                counter++;
            return result;
        }
            return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {

        if (limit > counter) {
            int result = super.read(b, off, (int) Math.min(len, limit - counter));
            if (result > 0)
                counter += result;
            return result;
        }
            return -1;
    }

    @Override
    public synchronized void reset() throws IOException {
        super.reset();
        counter = mark;
    }

    @Override
    public long skip(long n) throws IOException {
        long result = super.skip(Math.min(n, limit - counter));
        counter += result;
        return result;
    }
}
