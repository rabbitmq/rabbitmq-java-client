package com.rabbitmq.client;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Recoverable;

/**
 * Convenient interface when working against auto-recovery channels.
 *
 * @since 4.0.0
 */
public interface RecoverableChannel extends Recoverable, Channel {

}
