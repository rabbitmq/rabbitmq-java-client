package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.FlowListener;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class RecoveringChannel implements Channel, Recoverable {
    private Channel delegate;
    private RecoveringConnection connection;
    private List<RecoveryListener> recoveryListeners = new ArrayList<RecoveryListener>();
    private final Map<String, RecordedQueue> queues = new ConcurrentHashMap<String, RecordedQueue>();
    private final List<RecordedBinding> bindings = new ArrayList<RecordedBinding>();
    private final Map<String, RecordedConsumer> consumers = new ConcurrentHashMap<String, RecordedConsumer>();
    private final Map<String, RecordedExchange> exchanges = new ConcurrentHashMap<String, RecordedExchange>();

    public RecoveringChannel(RecoveringConnection connection, Channel delegate) {
        this.connection = connection;
        this.delegate = delegate;
    }

    public int getChannelNumber() {
        return delegate.getChannelNumber();
    }

    public Connection getConnection() {
        return delegate.getConnection();
    }

    public Channel getDelegate() {
        return delegate;
    }

    public void close() throws IOException {
        try {
          delegate.close();
        } finally {
          this.connection.unregisterChannel(this);
        }
    }

    public void close(int closeCode, String closeMessage) throws IOException {
        try {
          delegate.close(closeCode, closeMessage);
        } finally {
          this.connection.unregisterChannel(this);
        }
    }

    public AMQP.Channel.FlowOk flow(boolean active) throws IOException {
        return delegate.flow(active);
    }

    public AMQP.Channel.FlowOk getFlow() {
        return delegate.getFlow();
    }

    public void abort() throws IOException {
        delegate.abort();
    }

    public void abort(int closeCode, String closeMessage) throws IOException {
        delegate.abort(closeCode, closeMessage);
    }

    public void addReturnListener(ReturnListener listener) {
        delegate.addReturnListener(listener);
    }

    public boolean removeReturnListener(ReturnListener listener) {
        return delegate.removeReturnListener(listener);
    }

    public void clearReturnListeners() {
        delegate.clearReturnListeners();
    }

    public void addFlowListener(FlowListener listener) {
        delegate.addFlowListener(listener);
    }

    public boolean removeFlowListener(FlowListener listener) {
        return delegate.removeFlowListener(listener);
    }

    public void clearFlowListeners() {
        delegate.clearFlowListeners();
    }

    public void addConfirmListener(ConfirmListener listener) {
        delegate.addConfirmListener(listener);
    }

    public boolean removeConfirmListener(ConfirmListener listener) {
        return delegate.removeConfirmListener(listener);
    }

    public void clearConfirmListeners() {
        delegate.clearConfirmListeners();
    }

    public Consumer getDefaultConsumer() {
        return delegate.getDefaultConsumer();
    }

    public void setDefaultConsumer(Consumer consumer) {
        delegate.setDefaultConsumer(consumer);
    }

    public void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException {
        delegate.basicQos(prefetchSize, prefetchCount, global);
    }

    public void basicQos(int prefetchCount) throws IOException {
        delegate.basicQos(prefetchCount);
    }

    public void basicPublish(String exchange, String routingKey, AMQP.BasicProperties props, byte[] body) throws IOException {
        delegate.basicPublish(exchange, routingKey, props, body);
    }

    public void basicPublish(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body) throws IOException {
        delegate.basicPublish(exchange, routingKey, mandatory, props, body);
    }

    public void basicPublish(String exchange, String routingKey, boolean mandatory, boolean immediate, AMQP.BasicProperties props, byte[] body) throws IOException {
        delegate.basicPublish(exchange, routingKey, mandatory, immediate, props, body);
    }

    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type) throws IOException {
        return exchangeDeclare(exchange, type, false, false, null);
    }

    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable) throws IOException {
        return exchangeDeclare(exchange, type, durable, false, null);
    }

    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException {
        return exchangeDeclare(exchange, type, durable, autoDelete, false, arguments);
    }

    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
        final AMQP.Exchange.DeclareOk ok = delegate.exchangeDeclare(exchange, type, durable, autoDelete, internal, arguments);
        if(!RecordedExchange.isPredefined(exchange)) {
            RecordedExchange x = new RecordedExchange(this, exchange).
                    type(type).
                    durable(durable).
                    autoDelete(autoDelete).
                    arguments(arguments);
            this.exchanges.put(exchange, x);
        }
        return ok;
    }

    public AMQP.Exchange.DeclareOk exchangeDeclarePassive(String name) throws IOException {
        return delegate.exchangeDeclarePassive(name);
    }

    public AMQP.Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused) throws IOException {
        this.exchanges.remove(exchange);
        return delegate.exchangeDelete(exchange, ifUnused);
    }

    public AMQP.Exchange.DeleteOk exchangeDelete(String exchange) throws IOException {
        return exchangeDelete(exchange, false);
    }

    public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey) throws IOException {
        return delegate.exchangeBind(destination, source, routingKey);
    }

    public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
        AMQP.Exchange.BindOk ok = delegate.exchangeBind(destination, source, routingKey, arguments);
        RecordedBinding binding = new RecordedExchangeBinding(this).
                                          source(source).
                                          destination(destination).
                                          routingKey(routingKey).
                                          arguments(arguments);
        this.bindings.add(binding);
        return ok;
    }

    public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey) throws IOException {
        return exchangeUnbind(destination, source, routingKey, null);
    }

    public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
        deleteRecordedExchangeBinding(destination, source, routingKey, arguments);
        return delegate.exchangeUnbind(destination, source, routingKey, arguments);
    }

    public AMQP.Queue.DeclareOk queueDeclare() throws IOException {
        return queueDeclare("", false, true, true, null);
    }

    public AMQP.Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) throws IOException {
        final AMQP.Queue.DeclareOk ok = delegate.queueDeclare(queue, durable, exclusive, autoDelete, arguments);
        RecordedQueue q = new RecordedQueue(this, ok.getQueue()).
            durable(durable).
            exclusive(exclusive).
            autoDelete(autoDelete).
            arguments(arguments);
        if (queue.equals(RecordedQueue.EMPTY_STRING)) {
            q.serverNamed(true);
        }
        this.queues.put(ok.getQueue(), q);
        return ok;
    }

    public AMQP.Queue.DeclareOk queueDeclarePassive(String queue) throws IOException {
        return delegate.queueDeclarePassive(queue);
    }

    public AMQP.Queue.DeleteOk queueDelete(String queue) throws IOException {
        return queueDelete(queue, false, false);
    }

    public AMQP.Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
        this.queues.remove(queue);
        return delegate.queueDelete(queue, ifUnused, ifEmpty);
    }

    public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey) throws IOException {
        return queueBind(queue, exchange, routingKey, null);
    }

    public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
        AMQP.Queue.BindOk ok = delegate.queueBind(queue, exchange, routingKey, arguments);
        recordQueueBinding(queue, exchange, routingKey, arguments);
        return ok;
    }

    public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey) throws IOException {
        return queueUnbind(queue, exchange, routingKey, null);
    }

    public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
        deleteRecordedQueueBinding(queue, exchange, routingKey, arguments);
        return delegate.queueUnbind(queue, exchange, routingKey, arguments);
    }

    public AMQP.Queue.PurgeOk queuePurge(String queue) throws IOException {
        return delegate.queuePurge(queue);
    }

    public GetResponse basicGet(String queue, boolean autoAck) throws IOException {
        return delegate.basicGet(queue, autoAck);
    }

    public void basicAck(long deliveryTag, boolean multiple) throws IOException {
        delegate.basicAck(deliveryTag, multiple);
    }

    public void basicNack(long deliveryTag, boolean multiple, boolean requeue) throws IOException {
        delegate.basicNack(deliveryTag, multiple, requeue);
    }

    public void basicReject(long deliveryTag, boolean requeue) throws IOException {
        delegate.basicReject(deliveryTag, requeue);
    }

    public String basicConsume(String queue, Consumer callback) throws IOException {
        return basicConsume(queue, false, callback);
    }

    public String basicConsume(String queue, boolean autoAck, Consumer callback) throws IOException {
        return basicConsume(queue, autoAck, "", callback);
    }

    public String basicConsume(String queue, boolean autoAck, String consumerTag, Consumer callback) throws IOException {
        return basicConsume(queue, autoAck, consumerTag, false, false, null, callback);
    }

    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, Consumer callback) throws IOException {
        final String tag = delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, callback);
        RecordedConsumer consumer = new RecordedConsumer(this, queue).
                                            autoAck(autoAck).
                                            consumerTag(tag).
                                            exclusive(exclusive).
                                            arguments(arguments).
                                            consumer(callback);
        this.consumers.put(tag, consumer);
        return tag;
    }

    public void basicCancel(String consumerTag) throws IOException {
        delegate.basicCancel(consumerTag);
    }

    public AMQP.Basic.RecoverOk basicRecover() throws IOException {
        return delegate.basicRecover();
    }

    public AMQP.Basic.RecoverOk basicRecover(boolean requeue) throws IOException {
        return delegate.basicRecover(requeue);
    }

    @Deprecated
    @SuppressWarnings("deprecation")
    public void basicRecoverAsync(boolean requeue) throws IOException {
        delegate.basicRecoverAsync(requeue);
    }

    public AMQP.Tx.SelectOk txSelect() throws IOException {
        return delegate.txSelect();
    }

    public AMQP.Tx.CommitOk txCommit() throws IOException {
        return delegate.txCommit();
    }

    public AMQP.Tx.RollbackOk txRollback() throws IOException {
        return delegate.txRollback();
    }

    public AMQP.Confirm.SelectOk confirmSelect() throws IOException {
        return delegate.confirmSelect();
    }

    public long getNextPublishSeqNo() {
        return delegate.getNextPublishSeqNo();
    }

    public boolean waitForConfirms() throws InterruptedException {
        return delegate.waitForConfirms();
    }

    public boolean waitForConfirms(long timeout) throws InterruptedException, TimeoutException {
        return delegate.waitForConfirms(timeout);
    }

    public void waitForConfirmsOrDie() throws IOException, InterruptedException {
        delegate.waitForConfirmsOrDie();
    }

    public void waitForConfirmsOrDie(long timeout) throws IOException, InterruptedException, TimeoutException {
        delegate.waitForConfirmsOrDie(timeout);
    }

    public void asyncRpc(Method method) throws IOException {
        delegate.asyncRpc(method);
    }

    public Command rpc(Method method) throws IOException {
        return delegate.rpc(method);
    }

    public void addShutdownListener(ShutdownListener listener) {
        delegate.addShutdownListener(listener);
    }

    public void removeShutdownListener(ShutdownListener listener) {
        delegate.removeShutdownListener(listener);
    }

    public ShutdownSignalException getCloseReason() {
        return delegate.getCloseReason();
    }

    public void notifyListeners() {
        delegate.notifyListeners();
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public void addRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.add(listener);
    }

    public void removeRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.remove(listener);
    }

    public void automaticallyRecover(RecoveringConnection connection, Connection connDelegate) throws IOException {
        this.connection = connection;
        this.delegate = connDelegate.createChannel(this.getChannelNumber());

        if (this.connection.isAutomaticTopologyRecoveryEnabled()) {
            this.recoverTopology();
        }
    }

    private void recoverTopology() {
        // The recovery sequence is the following:
        //
        // 1. Recover exchanges
        // 2. Recover queues
        // 3. Recover bindings
        // 4. Recover consumers
        recoverExchanges();
        recoverQueues();
        recoverBindings();
        recoverConsumers();
    }


    private void recoverExchanges() {
        // recorded exchanges are guaranteed to be
        // non-predefined (we filter out predefined ones
        // in exchangeDeclare). MK.
        for (RecordedExchange x : this.exchanges.values()) {
            try {
                x.recover();
            } catch (Exception e) {
                System.err.println("Caught an exception while recovering exchange " + x.getName());
                this.connection.getExceptionHandler().handleChannelRecoveryException(this, e);
            }
        }
    }

    public void recoverQueues() {
        for (Map.Entry<String, RecordedQueue> entry : this.queues.entrySet()) {
            String oldName = entry.getKey();
            RecordedQueue q = entry.getValue();
            try {
                q.recover();
                String newName = q.getName();
                // make sure server-named queues are re-added with
                // their new names. MK.
                synchronized (this.queues) {
                    this.queues.remove(oldName);
                    this.queues.put(newName, q);
                    this.propagateQueueNameChangeToBindings(oldName, newName);
                    this.propagateQueueNameChangeToConsumers(oldName, newName);
                }
            } catch (Exception e) {
                System.err.println("Caught an exception while recovering queue " + oldName);
                this.connection.getExceptionHandler().handleChannelRecoveryException(this, e);
            }
        }
    }

    private void propagateQueueNameChangeToBindings(String oldName, String newName) {
        for (RecordedBinding b : this.bindings) {
            if (b.getDestination().equals(oldName)) {
                b.setDestination(newName);
            }
        }
    }

    private void propagateQueueNameChangeToConsumers(String oldName, String newName) {
        for (RecordedConsumer c : this.consumers.values()) {
            if (c.getQueue().equals(oldName)) {
                c.setQueue(newName);
            }
        }
    }

    public void recoverBindings() {
        for (RecordedBinding b : this.bindings) {
            try {
                b.recover();
            } catch (Exception e) {
                System.err.println("Caught an exception while recovering binding between " + b.getSource() + " and " + b.getDestination());
                this.connection.getExceptionHandler().handleChannelRecoveryException(this, e);
            }
        }
    }

    public void recoverConsumers() {
        for (Map.Entry<String, RecordedConsumer> entry : this.consumers.entrySet()) {
            String tag = entry.getKey();
            RecordedConsumer consumer = entry.getValue();

            try {
                String newTag = (String) consumer.recover();
                // make sure server-generated tags are re-added. MK.
                synchronized (this.consumers) {
                    this.consumers.remove(tag);
                    this.consumers.put(newTag, consumer);
                }
            } catch (Exception e) {
                System.err.println("Caught an exception while recovering consumer " + tag);
                this.connection.getExceptionHandler().handleChannelRecoveryException(this, e);
            }
        }
    }

    public void runRecoveryHooks() {
        for (RecoveryListener f : this.recoveryListeners) {
            try {
                f.handleRecovery(this);
            } catch (Exception e) {
                this.connection.getExceptionHandler().handleChannelRecoveryException(this, e);
            }
        }
    }

    private void recordQueueBinding(String queue, String exchange, String routingKey, Map<String, Object> arguments) {
        RecordedBinding binding = new RecordedQueueBinding(this).
            source(exchange).
            destination(queue).
            routingKey(routingKey).
            arguments(arguments);
        if (!this.bindings.contains(binding)) {
            this.bindings.add(binding);
        }
    }

    private boolean deleteRecordedQueueBinding(String queue, String exchange, String routingKey, Map<String, Object> arguments) {
        RecordedBinding b = new RecordedQueueBinding(this).
            source(exchange).
            destination(queue).
            routingKey(routingKey).
            arguments(arguments);
        return this.bindings.remove(b);
    }

    private boolean deleteRecordedExchangeBinding(String destination, String source, String routingKey, Map<String, Object> arguments) {
        RecordedBinding b = new RecordedExchangeBinding(this).
            source(source).
            destination(destination).
            routingKey(routingKey).
            arguments(arguments);
        return this.bindings.remove(b);
    }
}
