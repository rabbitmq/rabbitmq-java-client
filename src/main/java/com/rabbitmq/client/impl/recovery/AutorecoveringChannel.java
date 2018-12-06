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

package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.AMQCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

/**
 * {@link com.rabbitmq.client.Channel} implementation that is automatically
 * recovered during connection recovery.
 *
 * @since 3.3.0
 */
public class AutorecoveringChannel implements RecoverableChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutorecoveringChannel.class);

    private volatile RecoveryAwareChannelN delegate;
    private volatile AutorecoveringConnection connection;
    private final List<ShutdownListener> shutdownHooks  = new CopyOnWriteArrayList<ShutdownListener>();
    private final List<RecoveryListener> recoveryListeners = new CopyOnWriteArrayList<RecoveryListener>();
    private final List<ReturnListener> returnListeners = new CopyOnWriteArrayList<ReturnListener>();
    private final List<ConfirmListener> confirmListeners = new CopyOnWriteArrayList<ConfirmListener>();
    private final Set<String> consumerTags = Collections.synchronizedSet(new HashSet<String>());
    private int prefetchCountConsumer;
    private int prefetchCountGlobal;
    private boolean usesPublisherConfirms;
    private boolean usesTransactions;

    public AutorecoveringChannel(AutorecoveringConnection connection, RecoveryAwareChannelN delegate) {
        this.connection = connection;
        this.delegate = delegate;
    }

    @Override
    public int getChannelNumber() {
        return delegate.getChannelNumber();
    }

    @Override
    public Connection getConnection() {
        return delegate.getConnection();
    }

    public Channel getDelegate() {
        return delegate;
    }

    @Override
    public void close() throws IOException, TimeoutException {
        try {
            delegate.close();
        } finally {
            for (String consumerTag : consumerTags) {
                this.connection.deleteRecordedConsumer(consumerTag);
            }
            this.connection.unregisterChannel(this);
        }
    }

    @Override
    public void close(int closeCode, String closeMessage) throws IOException, TimeoutException {
        try {
          delegate.close(closeCode, closeMessage);
        } finally {
          this.connection.unregisterChannel(this);
        }
    }

    @Override
    public void abort() throws IOException {
        delegate.abort();
    }

    @Override
    public void abort(int closeCode, String closeMessage) throws IOException {
        delegate.abort(closeCode, closeMessage);
    }

    @Override
    public void addReturnListener(ReturnListener listener) {
        this.returnListeners.add(listener);
        delegate.addReturnListener(listener);
    }

    @Override
    public ReturnListener addReturnListener(ReturnCallback returnCallback) {
        ReturnListener returnListener = (replyCode, replyText, exchange, routingKey, properties, body) -> returnCallback.handle(new Return(
            replyCode, replyText, exchange, routingKey, properties, body
        ));
        this.addReturnListener(returnListener);
        return returnListener;
    }

    @Override
    public boolean removeReturnListener(ReturnListener listener) {
        this.returnListeners.remove(listener);
        return delegate.removeReturnListener(listener);
    }

    @Override
    public void clearReturnListeners() {
        this.returnListeners.clear();
        delegate.clearReturnListeners();
    }

    @Override
    public void addConfirmListener(ConfirmListener listener) {
        this.confirmListeners.add(listener);
        delegate.addConfirmListener(listener);
    }

    @Override
    public ConfirmListener addConfirmListener(ConfirmCallback ackCallback, ConfirmCallback nackCallback) {
        ConfirmListener confirmListener = new ConfirmListener() {

            @Override
            public void handleAck(long deliveryTag, boolean multiple) throws IOException {
                ackCallback.handle(deliveryTag, multiple);
            }

            @Override
            public void handleNack(long deliveryTag, boolean multiple) throws IOException {
                nackCallback.handle(deliveryTag, multiple);
            }
        };
        this.addConfirmListener(confirmListener);
        return confirmListener;
    }

    @Override
    public boolean removeConfirmListener(ConfirmListener listener) {
        this.confirmListeners.remove(listener);
        return delegate.removeConfirmListener(listener);
    }

    @Override
    public void clearConfirmListeners() {
        this.confirmListeners.clear();
        delegate.clearConfirmListeners();
    }

    @Override
    public Consumer getDefaultConsumer() {
        return delegate.getDefaultConsumer();
    }

    @Override
    public void setDefaultConsumer(Consumer consumer) {
        delegate.setDefaultConsumer(consumer);
    }

    @Override
    public void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException {
        if (global) {
            this.prefetchCountGlobal = prefetchCount;
        } else {
            this.prefetchCountConsumer = prefetchCount;
        }

        delegate.basicQos(prefetchSize, prefetchCount, global);
    }

    @Override
    public void basicQos(int prefetchCount) throws IOException {
        basicQos(0, prefetchCount, false);
    }

    @Override
    public void basicQos(int prefetchCount, boolean global) throws IOException {
        basicQos(0, prefetchCount, global);
    }

    @Override
    public void basicPublish(String exchange, String routingKey, AMQP.BasicProperties props, byte[] body) throws IOException {
        delegate.basicPublish(exchange, routingKey, props, body);
    }

    @Override
    public void basicPublish(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body) throws IOException {
        delegate.basicPublish(exchange, routingKey, mandatory, props, body);
    }

    @Override
    public void basicPublish(String exchange, String routingKey, boolean mandatory, boolean immediate, AMQP.BasicProperties props, byte[] body) throws IOException {
        delegate.basicPublish(exchange, routingKey, mandatory, immediate, props, body);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type) throws IOException {
        return exchangeDeclare(exchange, type, false, false, null);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type) throws IOException {
        return exchangeDeclare(exchange, type.getType());
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable) throws IOException {
        return exchangeDeclare(exchange, type, durable, false, null);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable) throws IOException {
        return exchangeDeclare(exchange, type.getType(), durable);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException {
        return exchangeDeclare(exchange, type, durable, autoDelete, false, arguments);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException {
        return exchangeDeclare(exchange, type.getType(), durable, autoDelete, arguments);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
        final AMQP.Exchange.DeclareOk ok = delegate.exchangeDeclare(exchange, type, durable, autoDelete, internal, arguments);
        recordExchange(ok, exchange, type, durable, autoDelete, arguments);
        return ok;
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
        return exchangeDeclare(exchange, type.getType(), durable, autoDelete, internal, arguments);
    }

    @Override
    public void exchangeDeclareNoWait(String exchange, String type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
        RecordedExchange x = new RecordedExchange(this, exchange).
          type(type).
          durable(durable).
          autoDelete(autoDelete).
          arguments(arguments);
        recordExchange(exchange, x);
        delegate.exchangeDeclareNoWait(exchange, type, durable, autoDelete, internal, arguments);
    }

    @Override
    public void exchangeDeclareNoWait(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
        exchangeDeclareNoWait(exchange, type.getType(), durable, autoDelete, internal, arguments);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclarePassive(String name) throws IOException {
        return delegate.exchangeDeclarePassive(name);
    }

    @Override
    public AMQP.Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused) throws IOException {
        deleteRecordedExchange(exchange);
        return delegate.exchangeDelete(exchange, ifUnused);
    }

    @Override
    public void exchangeDeleteNoWait(String exchange, boolean ifUnused) throws IOException {
        deleteRecordedExchange(exchange);
        delegate.exchangeDeleteNoWait(exchange, ifUnused);
    }

    @Override
    public AMQP.Exchange.DeleteOk exchangeDelete(String exchange) throws IOException {
        return exchangeDelete(exchange, false);
    }

    @Override
    public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey) throws IOException {
        return exchangeBind(destination, source, routingKey, null);
    }

    @Override
    public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
        final AMQP.Exchange.BindOk ok = delegate.exchangeBind(destination, source, routingKey, arguments);
        recordExchangeBinding(destination, source, routingKey, arguments);
        return ok;
    }

    @Override
    public void exchangeBindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
        delegate.exchangeBindNoWait(destination, source, routingKey, arguments);
        recordExchangeBinding(destination, source, routingKey, arguments);
    }

    @Override
    public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey) throws IOException {
        return exchangeUnbind(destination, source, routingKey, null);
    }

    @Override
    public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
        deleteRecordedExchangeBinding(destination, source, routingKey, arguments);
        this.maybeDeleteRecordedAutoDeleteExchange(source);
        return delegate.exchangeUnbind(destination, source, routingKey, arguments);
    }

    @Override
    public void exchangeUnbindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
        delegate.exchangeUnbindNoWait(destination, source, routingKey, arguments);
        deleteRecordedExchangeBinding(destination, source, routingKey, arguments);
    }

    @Override
    public AMQP.Queue.DeclareOk queueDeclare() throws IOException {
        return queueDeclare("", false, true, true, null);
    }

    @Override
    public AMQP.Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) throws IOException {
        final AMQP.Queue.DeclareOk ok = delegate.queueDeclare(queue, durable, exclusive, autoDelete, arguments);
        recordQueue(ok, queue, durable, exclusive, autoDelete, arguments);
        return ok;
    }

    @Override
    public void queueDeclareNoWait(String queue,
                                   boolean durable,
                                   boolean exclusive,
                                   boolean autoDelete,
                                   Map<String, Object> arguments) throws IOException {
        RecordedQueue meta = new RecordedQueue(this, queue).
            durable(durable).
            exclusive(exclusive).
            autoDelete(autoDelete).
            arguments(arguments);
        delegate.queueDeclareNoWait(queue, durable, exclusive, autoDelete, arguments);
        recordQueue(queue, meta);

    }

    @Override
    public AMQP.Queue.DeclareOk queueDeclarePassive(String queue) throws IOException {
        return delegate.queueDeclarePassive(queue);
    }

    @Override
    public long messageCount(String queue) throws IOException {
        return delegate.messageCount(queue);
    }

    @Override
    public long consumerCount(String queue) throws IOException {
        return delegate.consumerCount(queue);
    }

    @Override
    public AMQP.Queue.DeleteOk queueDelete(String queue) throws IOException {
        return queueDelete(queue, false, false);
    }

    @Override
    public AMQP.Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
        deleteRecordedQueue(queue);
        return delegate.queueDelete(queue, ifUnused, ifEmpty);
    }

    @Override
    public void queueDeleteNoWait(String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
        deleteRecordedQueue(queue);
        delegate.queueDeleteNoWait(queue, ifUnused, ifEmpty);
    }

    @Override
    public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey) throws IOException {
        return queueBind(queue, exchange, routingKey, null);
    }

    @Override
    public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
        AMQP.Queue.BindOk ok = delegate.queueBind(queue, exchange, routingKey, arguments);
        recordQueueBinding(queue, exchange, routingKey, arguments);
        return ok;
    }

    @Override
    public void queueBindNoWait(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
        delegate.queueBindNoWait(queue, exchange, routingKey, arguments);
        recordQueueBinding(queue, exchange, routingKey, arguments);
    }

    @Override
    public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey) throws IOException {
        return queueUnbind(queue, exchange, routingKey, null);
    }

    @Override
    public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
        deleteRecordedQueueBinding(queue, exchange, routingKey, arguments);
        this.maybeDeleteRecordedAutoDeleteExchange(exchange);
        return delegate.queueUnbind(queue, exchange, routingKey, arguments);
    }

    @Override
    public AMQP.Queue.PurgeOk queuePurge(String queue) throws IOException {
        return delegate.queuePurge(queue);
    }

    @Override
    public GetResponse basicGet(String queue, boolean autoAck) throws IOException {
        return delegate.basicGet(queue, autoAck);
    }

    @Override
    public void basicAck(long deliveryTag, boolean multiple) throws IOException {
        delegate.basicAck(deliveryTag, multiple);
    }

    @Override
    public void basicNack(long deliveryTag, boolean multiple, boolean requeue) throws IOException {
        delegate.basicNack(deliveryTag, multiple, requeue);
    }

    @Override
    public void basicReject(long deliveryTag, boolean requeue) throws IOException {
        delegate.basicReject(deliveryTag, requeue);
    }

    @Override
    public String basicConsume(String queue, Consumer callback) throws IOException {
        return basicConsume(queue, false, callback);
    }

    @Override
    public String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
        return basicConsume(queue, consumerFromDeliverCancelCallbacks(deliverCallback, cancelCallback));
    }

    @Override
    public String basicConsume(String queue, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
        return basicConsume(queue, consumerFromDeliverShutdownCallbacks(deliverCallback, shutdownSignalCallback));
    }

    @Override
    public String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback,
        ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
        return basicConsume(queue, false, consumerFromDeliverCancelShutdownCallbacks(deliverCallback, cancelCallback, shutdownSignalCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, Consumer callback) throws IOException {
        return basicConsume(queue, autoAck, "", callback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
        return basicConsume(queue, autoAck, "", consumerFromDeliverCancelCallbacks(deliverCallback, cancelCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback)
        throws IOException {
        return basicConsume(queue, autoAck, "", consumerFromDeliverShutdownCallbacks(deliverCallback, shutdownSignalCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, CancelCallback cancelCallback,
        ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
        return basicConsume(queue, autoAck, "", consumerFromDeliverCancelShutdownCallbacks(deliverCallback, cancelCallback, shutdownSignalCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, Consumer callback) throws IOException {
        return basicConsume(queue, autoAck, consumerTag, false, false, null, callback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, CancelCallback cancelCallback)
        throws IOException {
        return basicConsume(queue, autoAck, consumerTag, false, false, null, consumerFromDeliverCancelCallbacks(deliverCallback, cancelCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback,
        ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
        return basicConsume(queue, autoAck, consumerTag, false, false, null, consumerFromDeliverShutdownCallbacks(deliverCallback, shutdownSignalCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, CancelCallback cancelCallback,
        ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
        return basicConsume(queue, autoAck, consumerTag, false, false, null, consumerFromDeliverCancelShutdownCallbacks(deliverCallback, cancelCallback, shutdownSignalCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, Consumer callback) throws IOException {
        return basicConsume(queue, autoAck, "", false, false, arguments, callback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback)
        throws IOException {
        return basicConsume(queue, autoAck, "", false, false, arguments, consumerFromDeliverCancelCallbacks(deliverCallback, cancelCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback,
        ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
        return basicConsume(queue, autoAck, "", false, false, arguments, consumerFromDeliverShutdownCallbacks(deliverCallback, shutdownSignalCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback,
        ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
        return basicConsume(queue, autoAck, "", false, false, arguments, consumerFromDeliverCancelShutdownCallbacks(deliverCallback, cancelCallback, shutdownSignalCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, Consumer callback) throws IOException {
        final String result = delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, callback);
        recordConsumer(result, queue, autoAck, exclusive, arguments, callback);
        return result;
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments,
        DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
        return basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, consumerFromDeliverCancelCallbacks(deliverCallback, cancelCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments,
        DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
        return basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, consumerFromDeliverShutdownCallbacks(deliverCallback, shutdownSignalCallback));
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments,
        DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException {
        return basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, consumerFromDeliverCancelShutdownCallbacks(deliverCallback, cancelCallback, shutdownSignalCallback));
    }

    private Consumer consumerFromDeliverCancelCallbacks(final DeliverCallback deliverCallback, final CancelCallback cancelCallback) {
        return new Consumer() {

            @Override
            public void handleConsumeOk(String consumerTag) { }

            @Override
            public void handleCancelOk(String consumerTag) { }

            @Override
            public void handleCancel(String consumerTag) throws IOException {
                cancelCallback.handle(consumerTag);
            }

            @Override
            public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) { }

            @Override
            public void handleRecoverOk(String consumerTag) { }

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                deliverCallback.handle(consumerTag, new Delivery(envelope, properties, body));
            }
        };
    }

    private Consumer consumerFromDeliverShutdownCallbacks(final DeliverCallback deliverCallback, final ConsumerShutdownSignalCallback shutdownSignalCallback) {
        return new Consumer() {
            @Override
            public void handleConsumeOk(String consumerTag) { }

            @Override
            public void handleCancelOk(String consumerTag) { }

            @Override
            public void handleCancel(String consumerTag) throws IOException { }

            @Override
            public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
                shutdownSignalCallback.handleShutdownSignal(consumerTag, sig);
            }

            @Override
            public void handleRecoverOk(String consumerTag) { }

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                deliverCallback.handle(consumerTag, new Delivery(envelope, properties, body));
            }
        };
    }

    private Consumer consumerFromDeliverCancelShutdownCallbacks(final DeliverCallback deliverCallback, final CancelCallback cancelCallback, final ConsumerShutdownSignalCallback shutdownSignalCallback) {
        return new Consumer() {
            @Override
            public void handleConsumeOk(String consumerTag) { }

            @Override
            public void handleCancelOk(String consumerTag) { }

            @Override
            public void handleCancel(String consumerTag) throws IOException {
                cancelCallback.handle(consumerTag);
            }

            @Override
            public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
                shutdownSignalCallback.handleShutdownSignal(consumerTag, sig);
            }

            @Override
            public void handleRecoverOk(String consumerTag) { }

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                deliverCallback.handle(consumerTag, new Delivery(envelope, properties, body));
            }
        };
    }

    @Override
    public void basicCancel(String consumerTag) throws IOException {
        RecordedConsumer c = this.deleteRecordedConsumer(consumerTag);
        if(c != null) {
            this.maybeDeleteRecordedAutoDeleteQueue(c.getQueue());
        }
        delegate.basicCancel(consumerTag);
    }

    @Override
    public AMQP.Basic.RecoverOk basicRecover() throws IOException {
        return delegate.basicRecover();
    }

    @Override
    public AMQP.Basic.RecoverOk basicRecover(boolean requeue) throws IOException {
        return delegate.basicRecover(requeue);
    }

    @Override
    public AMQP.Tx.SelectOk txSelect() throws IOException {
        this.usesTransactions = true;
        return delegate.txSelect();
    }

    @Override
    public AMQP.Tx.CommitOk txCommit() throws IOException {
        return delegate.txCommit();
    }

    @Override
    public AMQP.Tx.RollbackOk txRollback() throws IOException {
        return delegate.txRollback();
    }

    @Override
    public AMQP.Confirm.SelectOk confirmSelect() throws IOException {
        this.usesPublisherConfirms = true;
        return delegate.confirmSelect();
    }

    @Override
    public long getNextPublishSeqNo() {
        return delegate.getNextPublishSeqNo();
    }

    @Override
    public boolean waitForConfirms() throws InterruptedException {
        return delegate.waitForConfirms();
    }

    @Override
    public boolean waitForConfirms(long timeout) throws InterruptedException, TimeoutException {
        return delegate.waitForConfirms(timeout);
    }

    @Override
    public void waitForConfirmsOrDie() throws IOException, InterruptedException {
        delegate.waitForConfirmsOrDie();
    }

    @Override
    public void waitForConfirmsOrDie(long timeout) throws IOException, InterruptedException, TimeoutException {
        delegate.waitForConfirmsOrDie(timeout);
    }

    @Override
    public void asyncRpc(Method method) throws IOException {
        delegate.asyncRpc(method);
    }

    @Override
    public Command rpc(Method method) throws IOException {
        recordOnRpcRequest(method);
        AMQCommand response = delegate.rpc(method);
        recordOnRpcResponse(response.getMethod(), method);
        return response;
    }

    /**
     * @see Connection#addShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    @Override
    public void addShutdownListener(ShutdownListener listener) {
        this.shutdownHooks.add(listener);
        delegate.addShutdownListener(listener);
    }

    @Override
    public void removeShutdownListener(ShutdownListener listener) {
        this.shutdownHooks.remove(listener);
        delegate.removeShutdownListener(listener);
    }

    @Override
    public ShutdownSignalException getCloseReason() {
        return delegate.getCloseReason();
    }

    @Override
    public void notifyListeners() {
        delegate.notifyListeners();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void addRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.add(listener);
    }

    @Override
    public void removeRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.remove(listener);
    }

    //
    // Recovery
    //

    public void automaticallyRecover(AutorecoveringConnection connection, Connection connDelegate) throws IOException {
        RecoveryAwareChannelN defunctChannel = this.delegate;
        this.connection = connection;

        final RecoveryAwareChannelN newChannel = (RecoveryAwareChannelN) connDelegate.createChannel(this.getChannelNumber());
        // No Sonar: the channel could be null
        if (newChannel == null) //NOSONAR
            throw new IOException("Failed to create new channel for channel number=" + this.getChannelNumber() + " during recovery");
        newChannel.inheritOffsetFrom(defunctChannel);
        this.delegate = newChannel;

        this.notifyRecoveryListenersStarted();
        this.recoverShutdownListeners();
        this.recoverReturnListeners();
        this.recoverConfirmListeners();
        this.recoverState();
        this.notifyRecoveryListenersComplete();
    }

    private void recoverShutdownListeners() {
        for (ShutdownListener sh : this.shutdownHooks) {
            this.delegate.addShutdownListener(sh);
        }
    }

    private void recoverReturnListeners() {
        for(ReturnListener rl : this.returnListeners) {
            this.delegate.addReturnListener(rl);
        }
    }

    private void recoverConfirmListeners() {
        for(ConfirmListener cl : this.confirmListeners) {
            this.delegate.addConfirmListener(cl);
        }
    }

    private void recoverState() throws IOException {
        if (this.prefetchCountConsumer != 0) {
            basicQos(this.prefetchCountConsumer, false);
        }
        if (this.prefetchCountGlobal != 0) {
            basicQos(this.prefetchCountGlobal, true);
        }
        if(this.usesPublisherConfirms) {
            this.confirmSelect();
        }
        if(this.usesTransactions) {
            this.txSelect();
        }
    }

    private void notifyRecoveryListenersComplete() {
        for (RecoveryListener f : this.recoveryListeners) {
            f.handleRecovery(this);
        }
    }

    private void notifyRecoveryListenersStarted() {
        for (RecoveryListener f : this.recoveryListeners) {
            f.handleRecoveryStarted(this);
        }
    }

    private void recordQueueBinding(String queue, String exchange, String routingKey, Map<String, Object> arguments) {
        this.connection.recordQueueBinding(this, queue, exchange, routingKey, arguments);
    }

    private boolean deleteRecordedQueueBinding(String queue, String exchange, String routingKey, Map<String, Object> arguments) {
        return this.connection.deleteRecordedQueueBinding(this, queue, exchange, routingKey, arguments);
    }

    private void recordExchangeBinding(String destination, String source, String routingKey, Map<String, Object> arguments) {
        this.connection.recordExchangeBinding(this, destination, source, routingKey, arguments);
    }

    private boolean deleteRecordedExchangeBinding(String destination, String source, String routingKey, Map<String, Object> arguments) {
        return this.connection.deleteRecordedExchangeBinding(this, destination, source, routingKey, arguments);
    }

    private void recordQueue(AMQP.Queue.DeclareOk ok, String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) {
        RecordedQueue q = new RecordedQueue(this, ok.getQueue()).
                durable(durable).
                exclusive(exclusive).
                autoDelete(autoDelete).
                arguments(arguments);
        if (queue.equals(RecordedQueue.EMPTY_STRING)) {
            q.serverNamed(true);
        }
        recordQueue(ok, q);
    }

    private void recordQueue(AMQP.Queue.DeclareOk ok, RecordedQueue q) {
        this.connection.recordQueue(ok, q);
    }

    private void recordQueue(String queue, RecordedQueue meta) {
        this.connection.recordQueue(queue, meta);
    }

    private void deleteRecordedQueue(String queue) {
        this.connection.deleteRecordedQueue(queue);
    }

    private void recordExchange(AMQP.Exchange.DeclareOk ok, String exchange, String type, boolean durable, boolean autoDelete, Map<String, Object> arguments) {
        RecordedExchange x = new RecordedExchange(this, exchange).
                type(type).
                durable(durable).
                autoDelete(autoDelete).
                arguments(arguments);
        recordExchange(exchange, x);
    }

    private void recordExchange(String exchange, RecordedExchange x) {
        this.connection.recordExchange(exchange, x);
    }

    private void deleteRecordedExchange(String exchange) {
        this.connection.deleteRecordedExchange(exchange);
    }

    private void recordConsumer(String result,
                                String queue,
                                boolean autoAck,
                                boolean exclusive,
                                Map<String, Object> arguments,
                                Consumer callback) {
        RecordedConsumer consumer = new RecordedConsumer(this, queue).
                                            autoAck(autoAck).
                                            consumerTag(result).
                                            exclusive(exclusive).
                                            arguments(arguments).
                                            consumer(callback);
        this.consumerTags.add(result);
        this.connection.recordConsumer(result, consumer);
    }

    private RecordedConsumer deleteRecordedConsumer(String consumerTag) {
        this.consumerTags.remove(consumerTag);
        return this.connection.deleteRecordedConsumer(consumerTag);
    }

    private void maybeDeleteRecordedAutoDeleteQueue(String queue) {
        this.connection.maybeDeleteRecordedAutoDeleteQueue(queue);
    }

    private void maybeDeleteRecordedAutoDeleteExchange(String exchange) {
        this.connection.maybeDeleteRecordedAutoDeleteExchange(exchange);
    }

    void updateConsumerTag(String tag, String newTag) {
        synchronized (this.consumerTags) {
            consumerTags.remove(tag);
            consumerTags.add(newTag);
        }
    }

    @Override
    public CompletableFuture<Command> asyncCompletableRpc(Method method) throws IOException {
        recordOnRpcRequest(method);
        CompletableFuture<Command> future = this.delegate.asyncCompletableRpc(method);
        future.thenAccept(command -> {
            if (command != null) {
                recordOnRpcResponse(command.getMethod(), method);
            }
        });
        return future;
    }

    private void recordOnRpcRequest(Method method) {
        if (method instanceof AMQP.Queue.Delete) {
            deleteRecordedQueue(((AMQP.Queue.Delete) method).getQueue());
        } else if (method instanceof AMQP.Exchange.Delete) {
            deleteRecordedExchange(((AMQP.Exchange.Delete) method).getExchange());
        } else if (method instanceof AMQP.Queue.Unbind) {
            AMQP.Queue.Unbind unbind = (AMQP.Queue.Unbind) method;
            deleteRecordedQueueBinding(
                    unbind.getQueue(), unbind.getExchange(),
                    unbind.getRoutingKey(), unbind.getArguments()
            );
            this.maybeDeleteRecordedAutoDeleteExchange(unbind.getExchange());
        } else if (method instanceof AMQP.Exchange.Unbind) {
            AMQP.Exchange.Unbind unbind = (AMQP.Exchange.Unbind) method;
            deleteRecordedExchangeBinding(
                    unbind.getDestination(), unbind.getSource(),
                    unbind.getRoutingKey(), unbind.getArguments()
            );
            this.maybeDeleteRecordedAutoDeleteExchange(unbind.getSource());
        }
    }

    private void recordOnRpcResponse(Method response, Method request) {
        if (response instanceof AMQP.Queue.DeclareOk) {
            if (request instanceof AMQP.Queue.Declare) {
                AMQP.Queue.DeclareOk ok = (AMQP.Queue.DeclareOk) response;
                AMQP.Queue.Declare declare = (AMQP.Queue.Declare) request;
                recordQueue(
                        ok, declare.getQueue(),
                        declare.getDurable(), declare.getExclusive(), declare.getAutoDelete(),
                        declare.getArguments()
                );
            } else {
                LOGGER.warn("RPC response {} and RPC request {} not compatible, topology not recorded.",
                        response.getClass(), request.getClass());
            }
        } else if (response instanceof AMQP.Exchange.DeclareOk) {
            if (request instanceof AMQP.Exchange.Declare) {
                AMQP.Exchange.DeclareOk ok = (AMQP.Exchange.DeclareOk) response;
                AMQP.Exchange.Declare declare = (AMQP.Exchange.Declare) request;
                recordExchange(
                        ok, declare.getExchange(), declare.getType(),
                        declare.getDurable(), declare.getAutoDelete(),
                        declare.getArguments()
                );
            } else {
                LOGGER.warn("RPC response {} and RPC request {} not compatible, topology not recorded.",
                        response.getClass(), request.getClass());
            }
        } else if (response instanceof AMQP.Queue.BindOk) {
            if (request instanceof AMQP.Queue.Bind) {
                AMQP.Queue.Bind bind = (AMQP.Queue.Bind) request;
                recordQueueBinding(bind.getQueue(), bind.getExchange(), bind.getRoutingKey(), bind.getArguments());
            } else {
                LOGGER.warn("RPC response {} and RPC request {} not compatible, topology not recorded.",
                        response.getClass(), request.getClass());
            }
        } else if (response instanceof AMQP.Exchange.BindOk) {
            if (request instanceof AMQP.Exchange.Bind) {
                AMQP.Exchange.Bind bind = (AMQP.Exchange.Bind) request;
                recordExchangeBinding(bind.getDestination(), bind.getSource(), bind.getRoutingKey(), bind.getArguments());
            } else {
                LOGGER.warn("RPC response {} and RPC request {} not compatible, topology not recorded.",
                        response.getClass(), request.getClass());
            }
        }
    }

    @Override
    public String toString() {
        return this.delegate.toString();
    }

}
