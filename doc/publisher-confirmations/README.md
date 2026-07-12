# ConfirmationPublisher - Asynchronous Publisher Confirmations

## Overview

`ConfirmationPublisher` provides asynchronous publisher confirmation tracking
with a `CompletableFuture`-based API, optional bounded-outstanding backpressure,
and a generic context parameter for message correlation. It owns a dedicated
channel created from the supplied `Connection`, requiring no modifications to
the existing `Channel` or `ChannelN` classes.

## Motivation

Traditional publisher confirms in the Java client require manual tracking of
sequence numbers and correlation of `basic.return` messages. This makes
per-message error handling complex and provides no built-in async pattern,
backpressure mechanism, or message correlation support.

`ConfirmationPublisher` addresses these limitations:

- Automatic confirmation tracking via `CompletableFuture<T>` API
- Generic context parameter for message correlation
- Optional bounded-outstanding-confirms backpressure (simple `Semaphore`)
- Futures completed on a configurable executor, never on the I/O thread
- Clean recovery semantics: outstanding futures fail, then publishing resumes

## Architecture

```
Connection
    |
    +-- createChannel() (private to the publisher)
            |
            ConfirmationPublisher
                - ConcurrentSkipListMap<seqNo, OutstandingConfirmation>
                - publishLock (serializes seqNo read + publish)
                - Semaphore (optional backpressure)
                - Confirm/Return/Shutdown listeners
```

The publisher keys its outstanding map by `channel.getNextPublishSeqNo()`,
read atomically with the publish under a `ReentrantLock`. Because the channel
is private, no external publish can desynchronize the counter. This guarantees
that the map key is the broker's delivery tag and correlation is correct.

### Confirmation Tracking

**Lifecycle:**

1. Acquire a semaphore permit (if bounded)
2. Lock, read `channel.getNextPublishSeqNo()`, insert entry, publish, unlock
3. If publish fails, remove entry, release permit, abort channel if tag-space
   skewed
4. Broker sends `basic.ack`/`basic.nack`/`basic.return`
5. Entry claimed via `ConcurrentSkipListMap.remove()`, permit released via
   `AtomicBoolean.compareAndSet`, future completed on the configured executor

**Multiple-ack handling:**

```java
NavigableMap<Long, OutstandingConfirmation> confirmed =
    outstanding.headMap(deliveryTag, true);
for (Long seqNo : confirmed.keySet()) {
    completeOne(seqNo, nack);
}
```

This is O(k log n) on the confirmed entries, matching `ChannelN`'s own
`unconfirmedSet.headSet(seqNo + 1).clear()` pattern.

### Return Correlation

A `basic.return` does not carry a delivery tag, so the publisher injects an
`x-seq-no` header **only when `mandatory = true`** (returns are impossible
otherwise). On the return path, the header is read back to find the
corresponding entry. Non-mandatory publishes avoid the header and the
`BasicProperties` copy entirely.

The broker echoes this header on normal delivery as well as on return, so when
a mandatory message is routable its consumers receive it with the `x-seq-no`
header present; it cannot be stripped on the routable path. Applications that
must not expose the header should publish such messages through their own
channel rather than this publisher.

The publisher rejects publishes where the caller already set an `x-seq-no`
header to prevent silent clobbering.

### Connection Recovery

When the wrapped channel is an `AutorecoveringChannel`, connection loss
triggers the shutdown listener, which fails all outstanding futures
exceptionally (their outcome is unknown). After recovery the channel's
`getNextPublishSeqNo()` returns the reset counter (verified experimentally),
so new publishes key correctly with no reset hook needed.

## API

### Creating a Publisher

```java
// Unlimited outstanding
ConfirmationPublisher publisher = ConfirmationPublisher.create(connection);

// At most 100 unconfirmed messages; basicPublishAsync blocks at the limit
ConfirmationPublisher publisher = ConfirmationPublisher.create(connection, 100);

// Custom executor for future completion
ConfirmationPublisher publisher = ConfirmationPublisher.create(connection, 100, myExecutor);
```

### Publishing

```java
<T> CompletableFuture<T> basicPublishAsync(String exchange, String routingKey,
    AMQP.BasicProperties props, byte[] body, T context)

<T> CompletableFuture<T> basicPublishAsync(String exchange, String routingKey,
    boolean mandatory, AMQP.BasicProperties props, byte[] body, T context)
```

The context parameter is returned in the completed future on success and
available via `PublishException.getContext()` on failure.

### Closing

```java
publisher.close();
```

Outstanding futures complete exceptionally with `ShutdownSignalException`.

## Error Handling

### PublishException

```java
public class PublishException extends IOException {
    public long getSequenceNumber();
    public boolean isReturn();
    public Return getReturned();   // null for nacks
    public Object getContext();
}
```

- **basic.nack**: `isReturn() == false`, `getReturned() == null`
- **basic.return**: `isReturn() == true`, `getReturned()` carries the full
  `Return` (reply code, text, exchange, routing key, properties, body)
- **Shutdown/close**: future completes with the `ShutdownSignalException`

## Usage Examples

### Basic

```java
Connection connection = factory.newConnection();
ConfirmationPublisher publisher = ConfirmationPublisher.create(connection, 100);

publisher.basicPublishAsync("exchange", "routing.key", props, body, "msg-123")
    .thenAccept(msgId -> System.out.println("Confirmed: " + msgId))
    .exceptionally(ex -> {
        System.err.println("Failed: " + ex.getCause().getMessage());
        return null;
    });
```

### With Context Objects

```java
class OrderContext {
    final String orderId;
    final Instant sent;
    OrderContext(String orderId) {
        this.orderId = orderId;
        this.sent = Instant.now();
    }
}

OrderContext ctx = new OrderContext("order-12345");
publisher.basicPublishAsync("orders", "new", props, body, ctx)
    .thenAccept(c -> {
        Duration latency = Duration.between(c.sent, Instant.now());
        System.out.println(c.orderId + " confirmed in " + latency.toMillis() + "ms");
    });
```

### Handling Returns

```java
publisher.basicPublishAsync("exchange", "key", true, props, body, "msg-1")
    .exceptionally(ex -> {
        if (ex.getCause() instanceof PublishException) {
            PublishException pe = (PublishException) ex.getCause();
            if (pe.isReturn()) {
                Return r = pe.getReturned();
                System.err.println("Unroutable: " + r.getReplyText());
            }
        }
        return null;
    });
```

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| Owns the channel | Prevents external publishes from skewing the tag space |
| Keys by `getNextPublishSeqNo()` | Uses the channel's authoritative counter; no shadow counter to diverge |
| `ReentrantLock` around seqNo + publish | Makes them atomic; contention negligible since `ChannelN` already serializes `transmit` |
| `ConcurrentSkipListMap` | O(k log n) `headMap` for multiple-acks vs O(n) keyset scan |
| `AtomicBoolean` on slot release | Prevents double-release race between ack and shutdown paths |
| Completes futures on an executor | Never blocks the connection's frame-reader thread |
| Header only on `mandatory=true` | Non-mandatory publishes avoid the wire overhead entirely |
| Aborts channel on publish failure after tag consumed | Safe because the channel is private; guarantees tag-space consistency |
