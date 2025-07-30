// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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


package com.rabbitmq.client.test.ssl;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
	UnverifiedConnection.class,
	VerifiedConnection.class,
	BadVerifiedConnection.class,
	ConnectionFactoryDefaultTlsVersion.class,
	NioTlsUnverifiedConnection.class,
	HostnameVerification.class,
	TlsConnectionLogging.class,
	SslContextFactoryTest.class
})
public class SslTestSuite {

}
