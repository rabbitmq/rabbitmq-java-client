// Copyright (c) 2023 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

package com.rabbitmq.client.observation.micrometer;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * {@link ObservationDocumentation} for RabbitMQ Clients.
 *
 * @since 5.18.0
 */
public enum RabbitMqObservationDocumentation implements ObservationDocumentation {

  /** Observation for Rabbit Client publishers. */
  PUBLISH_OBSERVATION {

    @Override
    public Class<? extends ObservationConvention<? extends Observation.Context>>
        getDefaultConvention() {
      return DefaultPublishObservationConvention.class;
    }

    @Override
    public KeyName[] getLowCardinalityKeyNames() {
      return LowCardinalityTags.values();
    }
  },

  /** Observation for Rabbit Client consumers. */
  CONSUME_OBSERVATION {

    @Override
    public Class<? extends ObservationConvention<? extends Observation.Context>>
        getDefaultConvention() {
      return DefaultConsumeObservationConvention.class;
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

  /** Low cardinality tags. */
  public enum LowCardinalityTags implements KeyName {

    /** A string identifying the messaging system. */
    MESSAGING_SYSTEM {

      @Override
      public String asString() {
        return "messaging.system";
      }
    },

    /** A string identifying the kind of messaging operation. */
    MESSAGING_OPERATION {

      @Override
      public String asString() {
        return "messaging.operation";
      }
    }
  }

  /** High cardinality tags. */
  public enum HighCardinalityTags implements KeyName {

    /** The message destination name. */
    MESSAGING_DESTINATION_NAME {

      @Override
      public String asString() {
        return "messaging.destination.name";
      }
    },

    /** RabbitMQ message routing key. */
    MESSAGING_ROUTING_KEY {

      @Override
      public String asString() {
        return "messaging.rabbitmq.destination.routing_key";
      }
    }
  }
}
