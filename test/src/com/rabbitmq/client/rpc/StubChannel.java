//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2012 VMware, Inc.  All rights reserved.
//
package com.rabbitmq.client.rpc;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP.Basic.RecoverOk;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.Channel.FlowOk;
import com.rabbitmq.client.AMQP.Exchange.BindOk;
import com.rabbitmq.client.AMQP.Exchange.DeclareOk;
import com.rabbitmq.client.AMQP.Exchange.DeleteOk;
import com.rabbitmq.client.AMQP.Exchange.UnbindOk;
import com.rabbitmq.client.AMQP.Queue.PurgeOk;
import com.rabbitmq.client.AMQP.Tx.CommitOk;
import com.rabbitmq.client.AMQP.Tx.RollbackOk;
import com.rabbitmq.client.AMQP.Tx.SelectOk;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.FlowListener;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * Stub implementation of Channel for tests
 */
public class StubChannel implements Channel {

    private Map<byte[], byte[]> replyMap;

    StubChannel() {
        this(null);
    };

    StubChannel(Map<byte[], byte[]> replyMap) {
        this.replyMap = replyMap;
    }
    public void addShutdownListener(ShutdownListener listener) {
        throw new UnsupportedOperationException();
    }

    public void removeShutdownListener(ShutdownListener listener) {
        throw new UnsupportedOperationException();
    }

    public ShutdownSignalException getCloseReason() {
        throw new UnsupportedOperationException();
    }

    public void notifyListeners() {
        throw new UnsupportedOperationException();
    }

    public boolean isOpen() {
        throw new UnsupportedOperationException();
    }

    public int getChannelNumber() {
        throw new UnsupportedOperationException();
    }

    public Connection getConnection() {
        throw new UnsupportedOperationException();
    }

    public void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    public void close(int closeCode, String closeMessage) throws IOException {
        throw new UnsupportedOperationException();
    }

    public FlowOk flow(boolean active) throws IOException {
        throw new UnsupportedOperationException();
    }

    public FlowOk getFlow() {
        throw new UnsupportedOperationException();
    }

    public void abort() throws IOException {
        throw new UnsupportedOperationException();
    }

    public void abort(int closeCode, String closeMessage) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void addReturnListener(ReturnListener listener) {
        throw new UnsupportedOperationException();
    }

    public boolean removeReturnListener(ReturnListener listener) {
        throw new UnsupportedOperationException();
    }

    public void clearReturnListeners() {
        throw new UnsupportedOperationException();
    }

    public void addFlowListener(FlowListener listener) {
        throw new UnsupportedOperationException();
    }

    public boolean removeFlowListener(FlowListener listener) {
        throw new UnsupportedOperationException();
    }

    public void clearFlowListeners() {
        throw new UnsupportedOperationException();
    }

    public void addConfirmListener(ConfirmListener listener) {
        throw new UnsupportedOperationException();
    }

    public boolean removeConfirmListener(ConfirmListener listener) {
        throw new UnsupportedOperationException();
    }

    public void clearConfirmListeners() {
        throw new UnsupportedOperationException();
    }

    public Consumer getDefaultConsumer() {
        throw new UnsupportedOperationException();
    }

    public void setDefaultConsumer(Consumer consumer) {
        throw new UnsupportedOperationException();
    }

    public void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void basicQos(int prefetchCount) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void basicPublish(String exchange, String routingKey,
            BasicProperties props, byte[] body) throws IOException {
        this.basicPublish(exchange, routingKey, false, false, props, body);
    }

    private long deliveryTag = 0;
    public void basicPublish(String exchange, String routingKey, boolean mandatory,
            boolean immediate, BasicProperties props, byte[] body) throws IOException {
        if (this.consumer != null) {
            Envelope envelope = new Envelope(this.deliveryTag, false, exchange, routingKey);
            if (this.replyMap != null)
                if (this.replyMap.containsKey(body))
                    this.consumer.handleDelivery(routingKey, envelope, props, this.replyMap.get(body));
                else
                    throw new UnsupportedOperationException("No reply in replyMap");
            else
                throw new UnsupportedOperationException("No replyMap set");
        }
    }

    public DeclareOk exchangeDeclare(String exchange, String type) throws IOException {
        throw new UnsupportedOperationException();
    }

    public DeclareOk exchangeDeclare(String exchange, String type, boolean durable)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    public DeclareOk exchangeDeclare(String exchange, String type, boolean durable,
            boolean autoDelete, Map<String, Object> arguments) throws IOException {
        throw new UnsupportedOperationException();
    }

    public DeclareOk exchangeDeclare(String exchange, String type, boolean durable,
            boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException {
        throw new UnsupportedOperationException();
    }

    public DeclareOk exchangeDeclarePassive(String name) throws IOException {
        throw new UnsupportedOperationException();
    }

    public DeleteOk exchangeDelete(String exchange, boolean ifUnused) throws IOException {
        throw new UnsupportedOperationException();
    }

    public DeleteOk exchangeDelete(String exchange) throws IOException {
        throw new UnsupportedOperationException();
    }

    public BindOk exchangeBind(String destination, String source, String routingKey)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    public BindOk exchangeBind(String destination, String source, String routingKey,
            Map<String, Object> arguments) throws IOException {
        throw new UnsupportedOperationException();
    }

    public UnbindOk exchangeUnbind(String destination, String source, String routingKey)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    public UnbindOk exchangeUnbind(String destination, String source, String routingKey,
            Map<String, Object> arguments) throws IOException {
        throw new UnsupportedOperationException();
    }

    public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare() throws IOException {
        String queue = generateQueueName();
        return this.queueDeclare(queue, false, true, true, null);
    }

    private String queueName = null;
    private String generateQueueName() {
        return "QN-1";
    }

    public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare(String queue, boolean durable,
            boolean exclusive, boolean autoDelete, Map<String, Object> arguments)
            throws IOException {
        if (this.queueName != null)
            throw new IllegalStateException("Only supports one queue");

        this.queueName = queue;
        return new com.rabbitmq.client.AMQP.Queue.DeclareOk
               .Builder()
               .consumerCount(this.consumer == null ? 0 : 1)
               .messageCount(0)
               .queue(queue)
               .build();
    }

    public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclarePassive(String queue)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    public com.rabbitmq.client.AMQP.Queue.DeleteOk queueDelete(String queue) throws IOException {
        throw new UnsupportedOperationException();
    }

    public com.rabbitmq.client.AMQP.Queue.DeleteOk queueDelete(String queue, boolean ifUnused,
            boolean ifEmpty) throws IOException {
        throw new UnsupportedOperationException();
    }

    public com.rabbitmq.client.AMQP.Queue.BindOk queueBind(String queue, String exchange,
            String routingKey) throws IOException {
        throw new UnsupportedOperationException();
    }

    public com.rabbitmq.client.AMQP.Queue.BindOk queueBind(String queue, String exchange,
            String routingKey, Map<String, Object> arguments) throws IOException {
        throw new UnsupportedOperationException();
    }

    public com.rabbitmq.client.AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange,
            String routingKey) throws IOException {
        throw new UnsupportedOperationException();
    }

    public com.rabbitmq.client.AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange,
            String routingKey, Map<String, Object> arguments) throws IOException {
        throw new UnsupportedOperationException();
    }

    public PurgeOk queuePurge(String queue) throws IOException {
        throw new UnsupportedOperationException();
    }

    public GetResponse basicGet(String queue, boolean autoAck) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void basicAck(long deliveryTag, boolean multiple) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void basicNack(long deliveryTag, boolean multiple, boolean requeue) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void basicReject(long deliveryTag, boolean requeue) throws IOException {
        throw new UnsupportedOperationException();
    }

    public String basicConsume(String queue, Consumer callback) throws IOException {
        return this.basicConsume(queue, false, callback);
    }

    public String basicConsume(String queue, boolean autoAck, Consumer callback) throws IOException {
        return this.basicConsume(queue, autoAck, generateConsumerTag(), callback);
    }

    public String basicConsume(String queue, boolean autoAck, String consumerTag, Consumer callback)
            throws IOException {
        return this.basicConsume(queue, autoAck, consumerTag, false, false, null, callback);
    }

    private Consumer consumer = null;

    private String consumerTag = null;
    private String generateConsumerTag() {
        return "CT-1";
    }

    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal,
            boolean exclusive, Map<String, Object> arguments, Consumer callback) throws IOException {
        if (this.consumer != null)
            throw new IllegalStateException("Only one consumer implemented");
        this.consumer = callback;
        return this.consumerTag = consumerTag;
    }

    public void basicCancel(String consumerTag) throws IOException {
        if (consumerTag == null || !consumerTag.equals(this.consumerTag))
            throw new IllegalStateException("Unknown consumerTag");
        this.consumer.handleCancelOk(consumerTag);
        this.consumer = null;
        this.consumerTag = null;
    }

    public RecoverOk basicRecover() throws IOException {
        throw new UnsupportedOperationException();
    }

    public RecoverOk basicRecover(boolean requeue) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void basicRecoverAsync(boolean requeue) throws IOException {
        throw new UnsupportedOperationException();
    }

    public SelectOk txSelect() throws IOException {
        throw new UnsupportedOperationException();
    }

    public CommitOk txCommit() throws IOException {
        throw new UnsupportedOperationException();
    }

    public RollbackOk txRollback() throws IOException {
        throw new UnsupportedOperationException();
    }

    public com.rabbitmq.client.AMQP.Confirm.SelectOk confirmSelect() throws IOException {
        throw new UnsupportedOperationException();
    }

    public long getNextPublishSeqNo() {
        throw new UnsupportedOperationException();
    }

    public boolean waitForConfirms() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    public void waitForConfirmsOrDie() throws IOException, InterruptedException {
        throw new UnsupportedOperationException();
    }

    public boolean waitForConfirms(long timeout) throws InterruptedException,
            TimeoutException {
        throw new UnsupportedOperationException();
    }

    public void waitForConfirmsOrDie(long timeout) throws IOException,
            InterruptedException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    public void asyncRpc(Method method) throws IOException {
        throw new UnsupportedOperationException();
    }

    public Command rpc(Method method) throws IOException {
        throw new UnsupportedOperationException();
    }

}
