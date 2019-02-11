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


package com.rabbitmq.client.test.ssl;

import com.rabbitmq.client.test.AbstractRMQTestSuite;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.util.ArrayList;
import java.util.List;

@RunWith(SSLTests.SslSuite.class)
@Suite.SuiteClasses({
	UnverifiedConnection.class,
	VerifiedConnection.class,
	BadVerifiedConnection.class,
	ConnectionFactoryDefaultTlsVersion.class,
	NioTlsUnverifiedConnection.class,
	HostnameVerification.class,
	TlsConnectionLogging.class
})
public class SSLTests {

	// initialize system properties
	static{
		new AbstractRMQTestSuite(){};
	}

	public static class SslSuite extends Suite {

		public SslSuite(Class<?> klass, RunnerBuilder builder) throws InitializationError {
			super(klass, builder);
		}

		public SslSuite(RunnerBuilder builder, Class<?>[] classes) throws InitializationError {
			super(builder, classes);
		}

		protected SslSuite(Class<?> klass, Class<?>[] suiteClasses) throws InitializationError {
			super(klass, suiteClasses);
		}

		protected SslSuite(RunnerBuilder builder, Class<?> klass, Class<?>[] suiteClasses) throws InitializationError {
			super(builder, klass, suiteClasses);
		}

		protected SslSuite(Class<?> klass, List<Runner> runners) throws InitializationError {
			super(klass, runners);
		}

		@Override
		protected List<Runner> getChildren() {
			if(!AbstractRMQTestSuite.requiredProperties() && !AbstractRMQTestSuite.isSSLAvailable()) {
				return new ArrayList<Runner>();
			} else {
				return super.getChildren();
			}
		}
	}

}
