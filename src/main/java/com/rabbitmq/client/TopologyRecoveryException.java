// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.client;

import com.rabbitmq.client.impl.recovery.RecordedEntity;

/**
 * Indicates an exception thrown during topology recovery.
 *
 * @see com.rabbitmq.client.ConnectionFactory#setTopologyRecoveryEnabled(boolean)
 * @since 3.3.0
 */
public class TopologyRecoveryException extends Exception {

	private final RecordedEntity recordedEntity;

	public TopologyRecoveryException(String message, Throwable cause) {
		this(message, cause, null);
	}

	public TopologyRecoveryException(String message, Throwable cause, final RecordedEntity recordedEntity) {
		super(message, cause);
		this.recordedEntity = recordedEntity;
	}

	public RecordedEntity getRecordedEntity() {
		return recordedEntity;
	}
}
