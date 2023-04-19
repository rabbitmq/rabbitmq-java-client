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

import com.rabbitmq.client.impl.MicrometerRabbitMqObservationDocumentation.HighCardinalityTags;
import com.rabbitmq.client.impl.MicrometerRabbitMqObservationDocumentation.LowCardinalityTags;
import io.micrometer.common.KeyValues;
import io.micrometer.common.util.StringUtils;

/**
 * Default implementation of {@link MicrometerConsumeObservationConvention}.
 *
 * @since 6.0.0
 * @see MicrometerConsumeObservationConvention
 */
public class DefaultMicrometerConsumeObservationConvention implements MicrometerConsumeObservationConvention {

    /**
     * Singleton instance of this convention.
     */
    public static final DefaultMicrometerConsumeObservationConvention INSTANCE = new DefaultMicrometerConsumeObservationConvention();

    // There is no need to instantiate this class multiple times, but it may be extended,
    // hence protected visibility.
    protected DefaultMicrometerConsumeObservationConvention() {
    }

    @Override
    public String getName() {
        return "rabbit.consume"; // TODO: How should we call this
    }

    @Override
    public String getContextualName(MicrometerConsumeContext context) {
        return destination(context.getEnvelope().getRoutingKey()) + " consume";
    }

    private String destination(String destination) {
        return StringUtils.isNotBlank(destination) ? destination : "(anonymous)";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(MicrometerConsumeContext context) {
        return KeyValues.of(LowCardinalityTags.MESSAGING_OPERATION.withValue("publish"), LowCardinalityTags.MESSAGING_SYSTEM.withValue("rabbitmq"));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(MicrometerConsumeContext context) {
        return KeyValues.of(HighCardinalityTags.MESSAGING_ROUTING_KEY.withValue(context.getEnvelope().getRoutingKey()), HighCardinalityTags.MESSAGING_DESTINATION_NAME.withValue(context.getEnvelope().getExchange()));
    }

}
