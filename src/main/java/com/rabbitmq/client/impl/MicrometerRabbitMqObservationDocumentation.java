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

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * {@link ObservationDocumentation} for RabbitMQ Clients.
 *
 * @since 6.0.0
 */
public enum MicrometerRabbitMqObservationDocumentation implements ObservationDocumentation {

    /**
     * Observation for Rabbit Client publishers.
     */
    PUBLISH_OBSERVATION {

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultMicrometerPublishObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityTags.values();
        }

    },

    /**
     * Observation for Rabbit Client consumers.
     */
    CONSUME_OBSERVATION {

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultMicrometerConsumeObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityTags.values();
        }

    };

    // SPAN NAME
    // <destination name> <operation name>
    // topic with spaces process
    // (anonymous) publish ((anonymous) being a stable identifier for an unnamed destination)
    // (anonymous) receive ((anonymous) being a stable identifier for an unnamed destination)

    // LOW CARDINALITY
    // messaging.system = rabbitmq
    // messaging.operation = publish

    // HIGH CARDINALITY

    // messaging.rabbitmq.destination.routing_key
    // messaging.destination.anonymous
    // messaging.destination.name
    // messaging.destination.template
    // messaging.destination.temporary
    // messaging.batch.message_count
    // messaging.message.conversation_id
    // messaging.message.id
    // messaging.message.payload_compressed_size_bytes
    // messaging.message.payload_size_bytes

    // net.peer.name
    // net.protocol.name
    // net.protocol.version
    // net.sock.family
    // net.sock.peer.addr
    // net.sock.peer.name
    // net.sock.peer.port

    /**
     * Low cardinality tags.
     */
    public enum LowCardinalityTags implements KeyName {

        /**
         * A string identifying the messaging system.
         */
        MESSAGING_SYSTEM {

            @Override
            public String asString() {
                return "messaging.system";
            }

        },

        /**
         * A string identifying the kind of messaging operation.
         */
        MESSAGING_OPERATION {

            @Override
            public String asString() {
                return "messaging.operation";
            }

        }

    }

    /**
     * High cardinality tags.
     */
    public enum HighCardinalityTags implements KeyName {

        /**
         * The message destination name.
         */
        MESSAGING_DESTINATION_NAME {

            @Override
            public String asString() {
                return "messaging.destination.name";
            }

        },

        /**
         * RabbitMQ message routing key.
         */
        MESSAGING_ROUTING_KEY {

            @Override
            public String asString() {
                return "messaging.rabbitmq.destination.routing_key";
            }

        }

    }

}
