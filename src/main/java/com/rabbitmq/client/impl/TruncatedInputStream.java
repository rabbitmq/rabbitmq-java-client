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

    @Override public int available() throws IOException {
        return (int) Math.min(limit - counter, super.available());
    }

    @Override public void mark(int readlimit) {
        super.mark(readlimit);
        mark = counter;
    }

    @Override public int read() throws IOException {
        if (counter < limit) {
            int result = super.read();
            if (result >= 0)
                counter++;
            return result;
        }
            return -1;
    }

    @Override public int read(byte[] b, int off, int len) throws IOException {

        if (limit > counter) {
            int result = super.read(b, off, (int) Math.min(len, limit - counter));
            if (result > 0)
                counter += result;
            return result;
        }
            return -1;
    }

    @Override public void reset() throws IOException {
        super.reset();
        counter = mark;
    }

    @Override public long skip(long n) throws IOException {
        long result = super.skip(Math.min(n, limit - counter));
        counter += result;
        return result;
    }
}
