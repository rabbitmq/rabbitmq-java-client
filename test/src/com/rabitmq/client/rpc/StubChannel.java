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
package com.rabitmq.client.rpc;

import java.io.IOException;
import java.util.Map;

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
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.Basic.RecoverOk;
import com.rabbitmq.client.AMQP.Channel.FlowOk;
import com.rabbitmq.client.AMQP.Exchange.BindOk;
import com.rabbitmq.client.AMQP.Exchange.DeclareOk;
import com.rabbitmq.client.AMQP.Exchange.DeleteOk;
import com.rabbitmq.client.AMQP.Exchange.UnbindOk;
import com.rabbitmq.client.AMQP.Queue.PurgeOk;
import com.rabbitmq.client.AMQP.Tx.CommitOk;
import com.rabbitmq.client.AMQP.Tx.RollbackOk;
import com.rabbitmq.client.AMQP.Tx.SelectOk;

/**
 * 
 */
public class StubChannel implements Channel {

    /* (non-Javadoc)
     * @see com.rabbitmq.client.ShutdownNotifier#addShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    public void addShutdownListener(ShutdownListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.ShutdownNotifier#removeShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    public void removeShutdownListener(ShutdownListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.ShutdownNotifier#getCloseReason()
     */
    public ShutdownSignalException getCloseReason() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.ShutdownNotifier#notifyListeners()
     */
    public void notifyListeners() {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.ShutdownNotifier#isOpen()
     */
    public boolean isOpen() {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#getChannelNumber()
     */
    public int getChannelNumber() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#getConnection()
     */
    public Connection getConnection() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#close()
     */
    public void close() throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#close(int, java.lang.String)
     */
    public void close(int closeCode, String closeMessage) throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#flow(boolean)
     */
    public FlowOk flow(boolean active) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#getFlow()
     */
    public FlowOk getFlow() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#abort()
     */
    public void abort() throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#abort(int, java.lang.String)
     */
    public void abort(int closeCode, String closeMessage) throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#addReturnListener(com.rabbitmq.client.ReturnListener)
     */
    public void addReturnListener(ReturnListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#removeReturnListener(com.rabbitmq.client.ReturnListener)
     */
    public boolean removeReturnListener(ReturnListener listener) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#clearReturnListeners()
     */
    public void clearReturnListeners() {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#addFlowListener(com.rabbitmq.client.FlowListener)
     */
    public void addFlowListener(FlowListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#removeFlowListener(com.rabbitmq.client.FlowListener)
     */
    public boolean removeFlowListener(FlowListener listener) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#clearFlowListeners()
     */
    public void clearFlowListeners() {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#addConfirmListener(com.rabbitmq.client.ConfirmListener)
     */
    public void addConfirmListener(ConfirmListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#removeConfirmListener(com.rabbitmq.client.ConfirmListener)
     */
    public boolean removeConfirmListener(ConfirmListener listener) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#clearConfirmListeners()
     */
    public void clearConfirmListeners() {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#getDefaultConsumer()
     */
    public Consumer getDefaultConsumer() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#setDefaultConsumer(com.rabbitmq.client.Consumer)
     */
    public void setDefaultConsumer(Consumer consumer) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicQos(int, int, boolean)
     */
    public void basicQos(int prefetchSize, int prefetchCount, boolean global)
            throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicQos(int)
     */
    public void basicQos(int prefetchCount) throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicPublish(java.lang.String, java.lang.String, com.rabbitmq.client.AMQP.BasicProperties, byte[])
     */
    public void basicPublish(String exchange, String routingKey,
            BasicProperties props, byte[] body) throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicPublish(java.lang.String, java.lang.String, boolean, boolean, com.rabbitmq.client.AMQP.BasicProperties, byte[])
     */
    public void basicPublish(String exchange, String routingKey,
            boolean mandatory, boolean immediate, BasicProperties props,
            byte[] body) throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeDeclare(java.lang.String, java.lang.String)
     */
    public DeclareOk exchangeDeclare(String exchange, String type)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeDeclare(java.lang.String, java.lang.String, boolean)
     */
    public DeclareOk exchangeDeclare(String exchange, String type,
            boolean durable) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeDeclare(java.lang.String, java.lang.String, boolean, boolean, java.util.Map)
     */
    public DeclareOk exchangeDeclare(String exchange, String type,
            boolean durable, boolean autoDelete, Map<String, Object> arguments)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeDeclare(java.lang.String, java.lang.String, boolean, boolean, boolean, java.util.Map)
     */
    public DeclareOk exchangeDeclare(String exchange, String type,
            boolean durable, boolean autoDelete, boolean internal,
            Map<String, Object> arguments) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeDeclarePassive(java.lang.String)
     */
    public DeclareOk exchangeDeclarePassive(String name) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeDelete(java.lang.String, boolean)
     */
    public DeleteOk exchangeDelete(String exchange, boolean ifUnused)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeDelete(java.lang.String)
     */
    public DeleteOk exchangeDelete(String exchange) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeBind(java.lang.String, java.lang.String, java.lang.String)
     */
    public BindOk exchangeBind(String destination, String source,
            String routingKey) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeBind(java.lang.String, java.lang.String, java.lang.String, java.util.Map)
     */
    public BindOk exchangeBind(String destination, String source,
            String routingKey, Map<String, Object> arguments)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeUnbind(java.lang.String, java.lang.String, java.lang.String)
     */
    public UnbindOk exchangeUnbind(String destination, String source,
            String routingKey) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#exchangeUnbind(java.lang.String, java.lang.String, java.lang.String, java.util.Map)
     */
    public UnbindOk exchangeUnbind(String destination, String source,
            String routingKey, Map<String, Object> arguments)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#queueDeclare()
     */
    public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare()
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#queueDeclare(java.lang.String, boolean, boolean, boolean, java.util.Map)
     */
    public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclare(String queue,
            boolean durable, boolean exclusive, boolean autoDelete,
            Map<String, Object> arguments) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#queueDeclarePassive(java.lang.String)
     */
    public com.rabbitmq.client.AMQP.Queue.DeclareOk queueDeclarePassive(
            String queue) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#queueDelete(java.lang.String)
     */
    public com.rabbitmq.client.AMQP.Queue.DeleteOk queueDelete(String queue)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#queueDelete(java.lang.String, boolean, boolean)
     */
    public com.rabbitmq.client.AMQP.Queue.DeleteOk queueDelete(String queue,
            boolean ifUnused, boolean ifEmpty) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#queueBind(java.lang.String, java.lang.String, java.lang.String)
     */
    public com.rabbitmq.client.AMQP.Queue.BindOk queueBind(String queue,
            String exchange, String routingKey) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#queueBind(java.lang.String, java.lang.String, java.lang.String, java.util.Map)
     */
    public com.rabbitmq.client.AMQP.Queue.BindOk queueBind(String queue,
            String exchange, String routingKey, Map<String, Object> arguments)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#queueUnbind(java.lang.String, java.lang.String, java.lang.String)
     */
    public com.rabbitmq.client.AMQP.Queue.UnbindOk queueUnbind(String queue,
            String exchange, String routingKey) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#queueUnbind(java.lang.String, java.lang.String, java.lang.String, java.util.Map)
     */
    public com.rabbitmq.client.AMQP.Queue.UnbindOk queueUnbind(String queue,
            String exchange, String routingKey, Map<String, Object> arguments)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#queuePurge(java.lang.String)
     */
    public PurgeOk queuePurge(String queue) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicGet(java.lang.String, boolean)
     */
    public GetResponse basicGet(String queue, boolean autoAck)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicAck(long, boolean)
     */
    public void basicAck(long deliveryTag, boolean multiple) throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicNack(long, boolean, boolean)
     */
    public void basicNack(long deliveryTag, boolean multiple, boolean requeue)
            throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicReject(long, boolean)
     */
    public void basicReject(long deliveryTag, boolean requeue)
            throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicConsume(java.lang.String, com.rabbitmq.client.Consumer)
     */
    public String basicConsume(String queue, Consumer callback)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicConsume(java.lang.String, boolean, com.rabbitmq.client.Consumer)
     */
    public String basicConsume(String queue, boolean autoAck, Consumer callback)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicConsume(java.lang.String, boolean, java.lang.String, com.rabbitmq.client.Consumer)
     */
    public String basicConsume(String queue, boolean autoAck,
            String consumerTag, Consumer callback) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicConsume(java.lang.String, boolean, java.lang.String, boolean, boolean, java.util.Map, com.rabbitmq.client.Consumer)
     */
    public String basicConsume(String queue, boolean autoAck,
            String consumerTag, boolean noLocal, boolean exclusive,
            Map<String, Object> arguments, Consumer callback)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicCancel(java.lang.String)
     */
    public void basicCancel(String consumerTag) throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicRecover()
     */
    public RecoverOk basicRecover() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicRecover(boolean)
     */
    public RecoverOk basicRecover(boolean requeue) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#basicRecoverAsync(boolean)
     */
    public void basicRecoverAsync(boolean requeue) throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#txSelect()
     */
    public SelectOk txSelect() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#txCommit()
     */
    public CommitOk txCommit() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#txRollback()
     */
    public RollbackOk txRollback() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#confirmSelect()
     */
    public com.rabbitmq.client.AMQP.Confirm.SelectOk confirmSelect()
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#getNextPublishSeqNo()
     */
    public long getNextPublishSeqNo() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#waitForConfirms()
     */
    public boolean waitForConfirms() throws InterruptedException {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#waitForConfirmsOrDie()
     */
    public void waitForConfirmsOrDie() throws IOException, InterruptedException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#asyncRpc(com.rabbitmq.client.Method)
     */
    public void asyncRpc(Method method) throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.rabbitmq.client.Channel#rpc(com.rabbitmq.client.Method)
     */
    public Command rpc(Method method) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

}
