# ConfirmationChannel - Asynchronous Publisher Confirmations

**Status:** Complete
**Date:** 2025-12-10

## Overview

`ConfirmationChannel` provides asynchronous publisher confirmation tracking with a `CompletableFuture`-based API, optional rate limiting, and generic context parameter for message correlation. The implementation wraps existing `Channel` instances using listener-based integration, requiring no modifications to the core `ChannelN` class.

## Motivation

Traditional publisher confirms in the Java client require manual tracking of sequence numbers and correlation of Basic.Return messages. This makes per-message error handling complex and provides no built-in async pattern, backpressure mechanism, or message correlation support.

`ConfirmationChannel` addresses these limitations by providing:
- Automatic confirmation tracking via `CompletableFuture<T>` API
- Generic context parameter for message correlation
- Optional rate limiting for backpressure control
- Clean separation from core `Channel` implementation

## Architecture

### Interface Hierarchy

```
Channel (existing interface)
    ↑
    |
ConfirmationChannel (new interface)
    ↑
    |
ConfirmationChannelN (new implementation)
```

### Key Components

**ConfirmationChannel Interface**
- Extends `Channel` interface
- Adds `basicPublishAsync()` methods (with and without mandatory flag)
- Generic `<T>` context parameter for correlation
- Returns `CompletableFuture<T>`

**ConfirmationChannelN Implementation**
- Wraps an existing `Channel` instance (composition, not inheritance)
- Maintains its own sequence number counter (`AtomicLong`)
- Registers return and confirm listeners on the wrapped channel
- Delegates all other `Channel` methods to the wrapped instance
- Throws `UnsupportedOperationException` for `basicPublish()` methods

### Sequence Number Management

**Independent Sequence Space:**
- `ConfirmationChannelN` maintains its own `AtomicLong nextSeqNo`
- No coordination with `ChannelN`'s sequence numbers
- Sequence numbers start at 1 and increment for each `basicPublishAsync()` call
- Sequence number added to message headers as `x-seq-no`

**Why Independent?**
- `basicPublish()` is disallowed on `ConfirmationChannel`
- No risk of sequence number conflicts
- Simpler implementation - no need to access `ChannelN` internals
- Clean separation of concerns

### Confirmation Tracking

**State Management:**
```java
private final ConcurrentHashMap<Long, ConfirmationEntry<?>> confirmations;

private static class ConfirmationEntry<T> {
    final CompletableFuture<T> future;
    final RateLimiter.Permit permit;
    final T context;
}
```

**Lifecycle:**
1. `basicPublishAsync()` called
2. Acquire rate limiter permit (if configured)
3. Get next sequence number
4. Create `CompletableFuture<T>` and `ConfirmationEntry<T>`
5. Add `x-seq-no` header to message
6. Store entry in `confirmations` map
7. Call `delegate.basicPublish()`
8. Return future to caller

**Completion Paths:**
- **Basic.Ack** → Complete future with context value, release permit
- **Basic.Nack** → Complete exceptionally with `PublishException`, release permit
- **Basic.Return** → Complete exceptionally with `PublishException`, release permit
- **Channel close** → Complete all pending futures exceptionally, release all permits

### Listener Integration

**Return Listener:**
```java
delegate.addReturnListener((replyCode, replyText, exchange, routingKey, props, body) -> {
    long seqNo = extractSequenceNumber(props.getHeaders());
    ConfirmationEntry<?> entry = confirmations.remove(seqNo);
    if (entry != null) {
        entry.future.completeExceptionally(
            new PublishException(seqNo, true, exchange, routingKey, replyCode, replyText, entry.context)
        );
        entry.releasePermit();
    }
});
```

**Confirm Listeners:**
```java
delegate.addConfirmListener(
    (seqNo, multiple) -> handleAck(seqNo, multiple),
    (seqNo, multiple) -> handleNack(seqNo, multiple)
);
```

**Multiple Acknowledgments:**
When `multiple=true`, all sequence numbers ≤ `seqNo` are processed:
```java
for (Long seq : new ArrayList<>(confirmations.keySet())) {
    if (seq <= seqNo) {
        ConfirmationEntry<?> entry = confirmations.remove(seq);
        // Complete future and release permit
    }
}
```

## API Design

### Constructor

```java
public ConfirmationChannelN(Channel delegate, RateLimiter rateLimiter)
```

**Parameters:**
- `delegate` - The underlying `Channel` instance (typically `ChannelN`)
- `rateLimiter` - Optional rate limiter for controlling publish concurrency (can be null)

**Initialization:**
- Calls `delegate.confirmSelect()` to enable publisher confirmations
- Registers return and confirm listeners
- Initializes confirmation tracking map

### basicPublishAsync Methods

```java
<T> CompletableFuture<T> basicPublishAsync(String exchange, String routingKey,
                                            AMQP.BasicProperties props, byte[] body, T context)

<T> CompletableFuture<T> basicPublishAsync(String exchange, String routingKey,
                                            boolean mandatory,
                                            AMQP.BasicProperties props, byte[] body, T context)
```

**Context Parameter:**
- Generic type `<T>` allows any user-defined correlation object
- Returned in the completed future on success
- Available in `PublishException.getContext()` on failure
- Can be null if correlation not needed

**Return Value:**
- `CompletableFuture<T>` that completes when broker confirms/rejects
- Completes successfully with context value on Basic.Ack
- Completes exceptionally with `PublishException` on Basic.Nack or Basic.Return

### basicPublish Methods (Disallowed)

All `basicPublish()` method overloads throw `UnsupportedOperationException`:

```java
@Override
public void basicPublish(String exchange, String routingKey,
                         AMQP.BasicProperties props, byte[] body) {
    throw new UnsupportedOperationException(
        "basicPublish() is not supported on ConfirmationChannel. Use basicPublishAsync() instead."
    );
}
```

**Rationale:**
- Prevents mixing synchronous and asynchronous publish patterns
- Eliminates sequence number coordination complexity
- Clear API contract - this channel is for async confirmations only

### Delegated Methods

All other `Channel` methods are delegated to the wrapped instance:
- `basicConsume()`, `basicGet()`, `basicAck()`, etc.
- `exchangeDeclare()`, `queueDeclare()`, etc.
- `addReturnListener()`, `addConfirmListener()`, etc.
- `close()`, `abort()`, etc.

## Rate Limiting

**Optional Feature:**
- Pass `RateLimiter` to constructor to enable
- Limits concurrent in-flight messages
- Blocks in `basicPublishAsync()` until permit available
- Permits released when confirmation received (ack/nack/return)

**Integration:**
```java
RateLimiter.Permit permit = null;
if (rateLimiter != null) {
    permit = rateLimiter.acquire(); // May block
}
// ... publish message ...
// Store permit in ConfirmationEntry for later release
```

## Error Handling

### PublishException

Enhanced with context parameter:
```java
public class PublishException extends IOException {
    private final Object context; // User-provided correlation object

    // Constructor for nacks (no routing details available)
    public PublishException(long sequenceNumber, Object context)

    // Constructor for returns (full routing details)
    public PublishException(long sequenceNumber, boolean isReturn,
                           String exchange, String routingKey,
                           Integer replyCode, String replyText, Object context)
}
```

### Exception Scenarios

**Basic.Nack:**
- Broker rejected the message
- `isReturn() == false`
- Exchange, routingKey, replyCode, replyText are null
- Only sequence number and context available

**Basic.Return:**
- Message unroutable (mandatory flag set)
- `isReturn() == true`
- Full routing details available
- Reply code indicates reason (NO_ROUTE, NO_CONSUMERS, etc.)

**Channel Closed:**
- All pending futures completed with `AlreadyClosedException`
- All rate limiter permits released
- Confirmations map cleared

**I/O Error:**
- Future completed with the I/O exception
- Rate limiter permit released
- Entry removed from confirmations map

## Usage Examples

### Basic Usage

```java
Connection connection = factory.newConnection();
Channel channel = connection.createChannel();
ConfirmationChannel confirmChannel = ConfirmationChannel.create(channel, null);

confirmChannel.basicPublishAsync("exchange", "routing.key", props, body, "msg-123")
    .thenAccept(msgId -> System.out.println("Confirmed: " + msgId))
    .exceptionally(ex -> {
        System.err.println("Failed: " + ex.getMessage());
        return null;
    });
```

### With Rate Limiting

```java
RateLimiter rateLimiter = new ThrottlingRateLimiter(1000); // Max 1000 in-flight
ConfirmationChannel confirmChannel = ConfirmationChannel.create(channel, rateLimiter);

for (int i = 0; i < 10000; i++) {
    String msgId = "msg-" + i;
    confirmChannel.basicPublishAsync("exchange", "key", props, body, msgId)
        .thenAccept(id -> System.out.println("Confirmed: " + id))
        .exceptionally(ex -> {
            if (ex.getCause() instanceof PublishException) {
                PublishException pe = (PublishException) ex.getCause();
                System.err.println("Failed: " + pe.getContext());
            }
            return null;
        });
}
```

### With Context Objects

```java
class MessageContext {
    final String orderId;
    final Instant timestamp;

    MessageContext(String orderId) {
        this.orderId = orderId;
        this.timestamp = Instant.now();
    }
}

MessageContext ctx = new MessageContext("order-12345");
confirmChannel.basicPublishAsync("orders", "new", props, body, ctx)
    .thenAccept(context -> {
        Duration latency = Duration.between(context.timestamp, Instant.now());
        System.out.println("Order " + context.orderId + " confirmed in " + latency.toMillis() + "ms");
    });
```

## Test Results

- **Confirm tests:** 24/24 passing
- **ThrottlingRateLimiterTest:** 9/9 passing
- **Total:** 33/33 tests passing

## Testing Strategy

### Unit Tests
- Sequence number generation and tracking
- Confirmation entry lifecycle
- Rate limiter integration
- Exception handling

### Integration Tests (Existing)
- All 25 tests in `Confirm.java` adapted to use `ConfirmationChannel`
- Basic.Ack handling (single and multiple)
- Basic.Nack handling (single and multiple)
- Basic.Return handling
- Context parameter correlation
- Channel close cleanup

### Rate Limiter Tests (Existing)
- 9 tests in `ThrottlingRateLimiterTest.java`
- No changes needed (rate limiter is independent)

## Trade-offs

**Pros:**
- Clean architecture with clear boundaries
- No risk of breaking existing functionality
- Easy to understand and maintain
- Can evolve independently of `ChannelN`

**Cons:**
- Requires wrapping a channel (extra object)
- Two ways to do publisher confirmations (`waitForConfirms()` vs `basicPublishAsync()`)
- Cannot mix `basicPublish()` and `basicPublishAsync()` on same channel
- Slightly more verbose setup code

## Future Enhancements

1. **Factory method on Connection** - `connection.createConfirmationChannel(rateLimiter)`
2. **Batch operations** - `basicPublishAsyncBatch()` for multiple messages
3. **Metrics integration** - Add metrics for `basicPublishAsync()`
4. **Observability** - Integration with observation collectors
5. **Alternative rate limiters** - Token bucket, sliding window, etc.
