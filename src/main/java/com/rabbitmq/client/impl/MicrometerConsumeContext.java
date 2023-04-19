/*
 * Copyright 2022 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rabbitmq.client.impl;

import java.util.Objects;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import io.micrometer.observation.transport.ReceiverContext;

/**
 * {@link io.micrometer.observation.Observation.Context} for use with RabbitMQ client
 * {@link io.micrometer.observation.Observation} instrumentation.
 *
 * @since 6.0.0
 */
public class MicrometerConsumeContext extends ReceiverContext<AMQBasicProperties> {

    private final String consumerTag;
    private final Envelope envelope;

    private final AMQP.BasicProperties properties;

    private final byte[] body;

    public MicrometerConsumeContext(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
        super((props, key) -> {
            Object result = Objects.requireNonNull(props).getHeaders().get(key);
            if (result == null) {
                return null;
            }
            return String.valueOf(result);
        });
        this.consumerTag = consumerTag;
        this.envelope = envelope;
        this.properties = properties;
        this.body = body;
        setCarrier(properties);
    }

    public String getConsumerTag() {
        return consumerTag;
    }

    public Envelope getEnvelope() {
        return envelope;
    }

    public AMQP.BasicProperties getProperties() {
        return properties;
    }

    public byte[] getBody() {
        return body;
    }
}
