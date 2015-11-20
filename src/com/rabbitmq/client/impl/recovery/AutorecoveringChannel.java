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
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * {@link com.rabbitmq.client.Channel} implementation that is automatically
 * recovered during connection recovery.
 *
 * @since 3.3.0
 */
public class AutorecoveringChannel implements Channel, Recoverable {
    private RecoveryAwareChannelN delegate;
    private AutorecoveringConnection connection;
    private final List<ShutdownListener> shutdownHooks  = new ArrayList<ShutdownListener>();
    private final List<RecoveryListener> recoveryListeners = new ArrayList<RecoveryListener>();
    private final List<ReturnListener> returnListeners = new ArrayList<ReturnListener>();
    private final List<ConfirmListener> confirmListeners = new ArrayList<ConfirmListener>();
    private final List<FlowListener> flowListeners = new ArrayList<FlowListener>();
    private int prefetchCountConsumer;
    private int prefetchCountGlobal;
    private boolean usesPublisherConfirms;
    private boolean usesTransactions;

    public AutorecoveringChannel(AutorecoveringConnection connection, RecoveryAwareChannelN delegate) {
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

    public void close() throws IOException, TimeoutException {
        try {
          delegate.close();
        } finally {
          this.connection.unregisterChannel(this);
        }
    }

    public void close(int closeCode, String closeMessage) throws IOException, TimeoutException {
        try {
          delegate.close(closeCode, closeMessage);
        } finally {
          this.connection.unregisterChannel(this);
        }
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    public boolean flowBlocked() {
        return delegate.flowBlocked();
    }

    public void abort() throws IOException {
        delegate.abort();
    }

    public void abort(int closeCode, String closeMessage) throws IOException {
        delegate.abort(closeCode, closeMessage);
    }

    public void addReturnListener(ReturnListener listener) {
        this.returnListeners.add(listener);
        delegate.addReturnListener(listener);
    }

    public boolean removeReturnListener(ReturnListener listener) {
        this.returnListeners.remove(listener);
        return delegate.removeReturnListener(listener);
    }

    public void clearReturnListeners() {
        this.returnListeners.clear();
        delegate.clearReturnListeners();
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    public void addFlowListener(FlowListener listener) {
        this.flowListeners.add(listener);
        delegate.addFlowListener(listener);
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    public boolean removeFlowListener(FlowListener listener) {
        this.flowListeners.remove(listener);
        return delegate.removeFlowListener(listener);
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    public void clearFlowListeners() {
        this.flowListeners.clear();
        delegate.clearFlowListeners();
    }

    public void addConfirmListener(ConfirmListener listener) {
        this.confirmListeners.add(listener);
        delegate.addConfirmListener(listener);
    }

    public boolean removeConfirmListener(ConfirmListener listener) {
        this.confirmListeners.remove(listener);
        return delegate.removeConfirmListener(listener);
    }

    public void clearConfirmListeners() {
        this.confirmListeners.clear();
        delegate.clearConfirmListeners();
    }

    public Consumer getDefaultConsumer() {
        return delegate.getDefaultConsumer();
    }

    public void setDefaultConsumer(Consumer consumer) {
        delegate.setDefaultConsumer(consumer);
    }

    public void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException {
        if (global) {
            this.prefetchCountGlobal = prefetchCount;
        } else {
            this.prefetchCountConsumer = prefetchCount;
        }

        delegate.basicQos(prefetchSize, prefetchCount, global);
    }

    public void basicQos(int prefetchCount) throws IOException {
        basicQos(0, prefetchCount, false);
    }

    public void basicQos(int prefetchCount, boolean global) throws IOException {
        basicQos(0, prefetchCount, global);
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
        RecordedExchange x = new RecordedExchange(this, exchange).
          type(type).
          durable(durable).
          autoDelete(autoDelete).
          arguments(arguments);
        recordExchange(exchange, x);
        return ok;
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

    public AMQP.Exchange.DeclareOk exchangeDeclarePassive(String name) throws IOException {
        return delegate.exchangeDeclarePassive(name);
    }

    public AMQP.Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused) throws IOException {
        deleteRecordedExchange(exchange);
        return delegate.exchangeDelete(exchange, ifUnused);
    }

    public void exchangeDeleteNoWait(String exchange, boolean ifUnused) throws IOException {
        deleteRecordedExchange(exchange);
        delegate.exchangeDeleteNoWait(exchange, ifUnused);
    }

    public AMQP.Exchange.DeleteOk exchangeDelete(String exchange) throws IOException {
        return exchangeDelete(exchange, false);
    }

    public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey) throws IOException {
        return exchangeBind(destination, source, routingKey, null);
    }

    public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
        final AMQP.Exchange.BindOk ok = delegate.exchangeBind(destination, source, routingKey, arguments);
        recordExchangeBinding(destination, source, routingKey, arguments);
        return ok;
    }

    public void exchangeBindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
        delegate.exchangeBindNoWait(destination, source, routingKey, arguments);
        recordExchangeBinding(destination, source, routingKey, arguments);
    }

    public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey) throws IOException {
        return exchangeUnbind(destination, source, routingKey, null);
    }

    public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
        deleteRecordedExchangeBinding(destination, source, routingKey, arguments);
        this.maybeDeleteRecordedAutoDeleteExchange(source);
        return delegate.exchangeUnbind(destination, source, routingKey, arguments);
    }

    public void exchangeUnbindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException {
        delegate.exchangeUnbindNoWait(destination, source, routingKey, arguments);
        deleteRecordedExchangeBinding(destination, source, routingKey, arguments);
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
        recordQueue(ok, q);
        return ok;
    }

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

    public AMQP.Queue.DeclareOk queueDeclarePassive(String queue) throws IOException {
        return delegate.queueDeclarePassive(queue);
    }

    public AMQP.Queue.DeleteOk queueDelete(String queue) throws IOException {
        return queueDelete(queue, false, false);
    }

    public AMQP.Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
        deleteRecordedQueue(queue);
        return delegate.queueDelete(queue, ifUnused, ifEmpty);
    }

    public void queueDeleteNoWait(String queue, boolean ifUnused, boolean ifEmpty) throws IOException {
        deleteRecordedQueue(queue);
        delegate.queueDeleteNoWait(queue, ifUnused, ifEmpty);
    }

    public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey) throws IOException {
        return queueBind(queue, exchange, routingKey, null);
    }

    public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
        AMQP.Queue.BindOk ok = delegate.queueBind(queue, exchange, routingKey, arguments);
        recordQueueBinding(queue, exchange, routingKey, arguments);
        return ok;
    }

    public void queueBindNoWait(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
        delegate.queueBindNoWait(queue, exchange, routingKey, arguments);
        recordQueueBinding(queue, exchange, routingKey, arguments);
    }

    public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey) throws IOException {
        return queueUnbind(queue, exchange, routingKey, null);
    }

    public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException {
        deleteRecordedQueueBinding(queue, exchange, routingKey, arguments);
        this.maybeDeleteRecordedAutoDeleteExchange(exchange);
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

    public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, Consumer callback) throws IOException {
        return basicConsume(queue, autoAck, "", false, false, arguments, callback);
    }

    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, Consumer callback) throws IOException {
        final String result = delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, callback);
        recordConsumer(result, queue, autoAck, exclusive, arguments, callback);
        return result;
    }

    public void basicCancel(String consumerTag) throws IOException {
        RecordedConsumer c = this.deleteRecordedConsumer(consumerTag);
        if(c != null) {
            this.maybeDeleteRecordedAutoDeleteQueue(c.getQueue());
        }
        delegate.basicCancel(consumerTag);
    }

    public AMQP.Basic.RecoverOk basicRecover() throws IOException {
        return delegate.basicRecover();
    }

    public AMQP.Basic.RecoverOk basicRecover(boolean requeue) throws IOException {
        return delegate.basicRecover(requeue);
    }

    public AMQP.Tx.SelectOk txSelect() throws IOException {
        this.usesTransactions = true;
        return delegate.txSelect();
    }

    public AMQP.Tx.CommitOk txCommit() throws IOException {
        return delegate.txCommit();
    }

    public AMQP.Tx.RollbackOk txRollback() throws IOException {
        return delegate.txRollback();
    }

    public AMQP.Confirm.SelectOk confirmSelect() throws IOException {
        this.usesPublisherConfirms = true;
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

    /**
     * @see Connection#addShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    public void addShutdownListener(ShutdownListener listener) {
        this.shutdownHooks.add(listener);
        delegate.addShutdownListener(listener);
    }

    public void removeShutdownListener(ShutdownListener listener) {
        this.shutdownHooks.remove(listener);
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

    //
    // Recovery
    //

    public void automaticallyRecover(AutorecoveringConnection connection, Connection connDelegate) throws IOException {
        RecoveryAwareChannelN defunctChannel = this.delegate;
        this.connection = connection;
        this.delegate = (RecoveryAwareChannelN) connDelegate.createChannel(this.getChannelNumber());
        this.delegate.inheritOffsetFrom(defunctChannel);

        this.recoverShutdownListeners();
        this.recoverReturnListeners();
        this.recoverConfirmListeners();
        this.recoverFlowListeners();
        this.recoverState();
        this.notifyRecoveryListeners();
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

    @Deprecated
    @SuppressWarnings("deprecation")
    private void recoverFlowListeners() {
        for(FlowListener fl : this.flowListeners) {
            this.delegate.addFlowListener(fl);
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

    private void notifyRecoveryListeners() {
        for (RecoveryListener f : this.recoveryListeners) {
            f.handleRecovery(this);
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

    private void recordQueue(AMQP.Queue.DeclareOk ok, RecordedQueue q) {
        this.connection.recordQueue(ok, q);
    }

    private void recordQueue(String queue, RecordedQueue meta) {
        this.connection.recordQueue(queue, meta);
    }

    private void deleteRecordedQueue(String queue) {
        this.connection.deleteRecordedQueue(queue);
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
        this.connection.recordConsumer(result, consumer);
    }

    private RecordedConsumer deleteRecordedConsumer(String consumerTag) {
        return this.connection.deleteRecordedConsumer(consumerTag);
    }

    private void maybeDeleteRecordedAutoDeleteQueue(String queue) {
        this.connection.maybeDeleteRecordedAutoDeleteQueue(queue);
    }

    private void maybeDeleteRecordedAutoDeleteExchange(String exchange) {
        this.connection.maybeDeleteRecordedAutoDeleteExchange(exchange);
    }
}
