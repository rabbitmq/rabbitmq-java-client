package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.AMQConnection;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class RecoveringConnection implements Connection, Recoverable {
    private final ConnectionFactory cf;
    private final Map<Integer, RecoveringChannel> channels;
    private final List<ShutdownListener> shutdownHooks;
    private final List<RecoveryListener> recoveryListeners;
    private final int networkRecoveryInterval;
    private ExecutorService executorService;
    private AMQConnection delegate;
    private boolean automaticTopologyRecoveryEnabled = true;

    public RecoveringConnection(ConnectionFactory cf) {
        this.cf = cf;

        this.channels = new ConcurrentHashMap<Integer, RecoveringChannel>();
        this.shutdownHooks = new ArrayList<ShutdownListener>();
        this.recoveryListeners = new ArrayList<RecoveryListener>();
        this.networkRecoveryInterval = cf.getNetworkRecoveryInterval();
    }

    public void init(ExecutorService executor) throws IOException {
        this.executorService = executor;
        this.delegate = (AMQConnection) this.cf.newConnection(executor);
        this.addAutomaticRecoveryListener();
    }

    public void init(ExecutorService executor, Address[] addrs) throws IOException {
        this.executorService = executor;
        this.delegate = (AMQConnection) this.cf.newConnection(executor, addrs);
        this.addAutomaticRecoveryListener();
    }

    private void addAutomaticRecoveryListener() {
        final RecoveringConnection c = this;
        ShutdownListener automaticRecoveryListener = new ShutdownListener() {
            public void shutdownCompleted(ShutdownSignalException cause) {
                try {
                    if (!cause.isInitiatedByApplication()) {
                        c.beginAutomaticRecovery();
                    }
                } catch (Exception e) {
                    // TODO: investigate if we can avoid swallowing the exception
                    //       here without affecting automatic recovery and changing shutdownCompleted
                    //       signature.
                    System.err.println("Caught an exception during connection recovery!");
                    e.printStackTrace(System.err);
                }
            }
        };
        synchronized (this) {
            this.shutdownHooks.add(automaticRecoveryListener);
            this.delegate.addShutdownListener(automaticRecoveryListener);
        }
    }

    synchronized private void beginAutomaticRecovery() throws InterruptedException, IOException {
        Thread.sleep(networkRecoveryInterval);
        // System.out.println("About to recover connection...");
        this.recoverConnection();
        // System.out.println("About to recover shutdown hooks...");
        this.recoverShutdownHooks();
        // System.out.println("About to recover channels...");
        this.recoverChannels();

        for (RecoveryListener f : this.recoveryListeners) {
            f.handleRecovery(this);
        }
        this.runChannelRecoveryHooks();
    }

    private void recoverShutdownHooks() {
        for (ShutdownListener sh : this.shutdownHooks) {
            this.delegate.addShutdownListener(sh);
        }
    }

    private void recoverConnection() throws IOException, InterruptedException {
        boolean recovering = true;
        while (recovering) {
            try {
                this.delegate = (AMQConnection) this.cf.newConnection(this.executorService);
                recovering = false;
            } catch (ConnectException ce) {
                System.err.println("Failed to reconnect: " + ce.getMessage());
                // TODO: exponential back-off
                Thread.sleep(networkRecoveryInterval);
            }
        }
    }

    private void recoverChannels() {
        for (RecoveringChannel ch : this.channels.values()) {
            try {
                ch.automaticallyRecover(this, this.delegate);
            } catch (Throwable t) {
                System.err.println("Caught an exception when recovering channel " + ch.getChannelNumber());
                t.printStackTrace(System.err);
            }
        }
    }

    public void start() throws IOException {
        delegate.start();
    }

    private void runChannelRecoveryHooks() {
        for (RecoveringChannel ch : this.channels.values()) {
            ch.runRecoveryHooks();
        }
    }

    public InetAddress getAddress() {
        return delegate.getAddress();
    }

    public void abort() {
        delegate.abort();
    }

    public Map<String, Object> getServerProperties() {
        return delegate.getServerProperties();
    }

    public Channel createChannel() throws IOException {
        com.rabbitmq.client.Channel ch = delegate.createChannel();
        if (ch == null) {
            return null;
        } else {
            return this.wrapChannel(ch);
        }
    }

    /**
     * Creates a recovering channel from a regular channel and registers it.
     * If the regular channel cannot be created (e.g. too many channels are open
     * already), returns null.
     *
     * @param delegateChannel Channel to wrap.
     * @return Recovering channel.
     */
    private Channel wrapChannel(com.rabbitmq.client.Channel delegateChannel) {
        final RecoveringChannel channel = new RecoveringChannel(this, delegateChannel);
        if (delegateChannel == null) {
            return null;
        } else {
            this.registerChannel(channel);
            return channel;
        }
    }

    private void registerChannel(RecoveringChannel channel) {
        this.channels.put(channel.getChannelNumber(), channel);
    }

    public void unregisterChannel(RecoveringChannel channel) {
        this.channels.remove(channel.getChannelNumber());
    }

    public boolean removeBlockedListener(BlockedListener listener) {
        return delegate.removeBlockedListener(listener);
    }

    public Map<String, Object> getClientProperties() {
        return delegate.getClientProperties();
    }

    public void close(int closeCode, String closeMessage, int timeout) throws IOException {
        delegate.close(closeCode, closeMessage, timeout);
    }

    public void abort(int timeout) {
        delegate.abort(timeout);
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public void close() throws IOException {
        delegate.close();
    }

    public void notifyListeners() {
        delegate.notifyListeners();
    }

    public int getFrameMax() {
        return delegate.getFrameMax();
    }

    public int getHeartbeat() {
        return delegate.getHeartbeat();
    }

    public ShutdownSignalException getCloseReason() {
        return delegate.getCloseReason();
    }

    public void abort(int closeCode, String closeMessage, int timeout) {
        delegate.abort(closeCode, closeMessage, timeout);
    }

    public int getChannelMax() {
        return delegate.getChannelMax();
    }

    public void addShutdownListener(ShutdownListener listener) {
        delegate.addShutdownListener(listener);
    }

    public Channel createChannel(int channelNumber) throws IOException {
        return delegate.createChannel(channelNumber);
    }

    public void abort(int closeCode, String closeMessage) {
        delegate.abort(closeCode, closeMessage);
    }

    public void close(int timeout) throws IOException {
        delegate.close(timeout);
    }

    public void addBlockedListener(BlockedListener listener) {
        delegate.addBlockedListener(listener);
    }

    public int getPort() {
        return delegate.getPort();
    }

    public void clearBlockedListeners() {
        delegate.clearBlockedListeners();
    }

    public void close(int closeCode, String closeMessage) throws IOException {
        delegate.close(closeCode, closeMessage);
    }

    public void removeShutdownListener(ShutdownListener listener) {
        delegate.removeShutdownListener(listener);
    }

    public boolean isAutomaticTopologyRecoveryEnabled() {
        return this.automaticTopologyRecoveryEnabled;
    }

    public void enableAutomaticTopologyRecovery() {
        this.automaticTopologyRecoveryEnabled = true;
    }

    public void disableAutomaticTopologyRecovery() {
        this.automaticTopologyRecoveryEnabled = false;
    }

    public void addRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.add(listener);
    }

    public void removeRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.remove(listener);
    }
}
