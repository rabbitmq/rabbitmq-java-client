package com.rabbitmq.examples.perf.openstack;

import com.rabbitmq.examples.perf.ProducerConsumerBase;

/*
 * This producer will publish OpenStack-alike messages to the RabbitMQ server.
 * These messages simulate the OpenStack heartbeats, in particular, those that
 * are used by Nova and Neutron modules to update N-CPU, L2, L3 and DHCP agents'
 * status. 
 */

public class Producer extends ProducerConsumerBase {

}
