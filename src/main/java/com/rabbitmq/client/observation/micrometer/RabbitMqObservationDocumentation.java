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
 * @since 5.19.0
 */
public enum RabbitMqObservationDocumentation implements ObservationDocumentation {
  /** Observation for publishing a message. */
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

  /** Observation for processing a message. */
  PROCESS_OBSERVATION {

    @Override
    public Class<? extends ObservationConvention<? extends Observation.Context>>
        getDefaultConvention() {
      return DefaultProcessObservationConvention.class;
    }

    @Override
    public KeyName[] getLowCardinalityKeyNames() {
      return LowCardinalityTags.values();
    }
  },

  /** Observation for polling for a message with <code>basic.get</code>. */
  RECEIVE_OBSERVATION {

    @Override
    public Class<? extends ObservationConvention<? extends Observation.Context>>
        getDefaultConvention() {
      return DefaultReceiveObservationConvention.class;
    }

    @Override
    public KeyName[] getLowCardinalityKeyNames() {
      return LowCardinalityTags.values();
    }
  };

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
    },

    /** A string identifying the protocol (AMQP). */
    NET_PROTOCOL_NAME {

      @Override
      public String asString() {
        return "net.protocol.name";
      }
    },

    /** A string identifying the protocol version (0.9.1). */
    NET_PROTOCOL_VERSION {

      @Override
      public String asString() {
        return "net.protocol.version";
      }
    },
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
    },

    /** The message destination name. */
    MESSAGING_SOURCE_NAME {

      @Override
      public String asString() {
        return "messaging.source.name";
      }
    },

    MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES {

      @Override
      public String asString() {
        return "messaging.message.payload_size_bytes";
      }
    },

    NET_SOCK_PEER_PORT {
      @Override
      public String asString() {
        return "net.sock.peer.port";
      }
    },

    NET_SOCK_PEER_ADDR {
      @Override
      public String asString() {
        return "net.sock.peer.addr";
      }
    }
  }
}
