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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MetricsCollector;
import io.micrometer.observation.transport.SenderContext;

/**
 * {@link io.micrometer.observation.Observation.Context} for use with RabbitMQ client
 * {@link io.micrometer.observation.Observation} instrumentation.
 *
 * @since 6.0.0
 */
public class MicrometerPublishContext extends SenderContext<AMQP.BasicProperties.Builder> {

    private final MetricsCollector.PublishArguments publishArguments;

    private final AMQP.BasicProperties.Builder builder;

    public MicrometerPublishContext(MetricsCollector.PublishArguments publishArguments) {
        super((basicProperties, key, value) -> {
            Map<String, Object> headers = publishArguments.getHeaders();
            headers.put(key, value);
            Objects.requireNonNull(basicProperties, "Properties must not be null").headers(headers);
        });
        this.publishArguments = publishArguments;
        this.builder = publishArguments.getProps().builder();
        if (publishArguments.getProps().getHeaders() == null) {
            this.builder.headers(new HashMap<>());
        }
        setCarrier(this.builder);
    }

    public MetricsCollector.PublishArguments getPublishArguments() {
        return publishArguments;
    }

    public AMQP.BasicProperties.Builder getPropertiesBuilder() {
        return builder;
    }
}
