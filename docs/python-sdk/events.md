# Real-Time Events System

The Fern-OS Python SDK includes a real-time event system that allows jobs to communicate asynchronously and react to system events.

## Concepts

- **Publish**: Send an event with data to the Manager.
- **Subscribe**: Listen for specific events and execute a callback when they occur.
- **Register**: Claim ownership of an event name (optional but recommended for clarity).

## Usage

The SDK provides a global `events` object for interacting with the event system.

### Emitting Events

To send an event to other jobs or the system, you **must first register the event** to claim ownership of the name.

```python
from fernos import events

# 1. Register the event (REQUIRED before emit)
events.register("data_ready")

# 2. Emit the event with some associated data
events.emit("data_ready", {"batch_id": "12345", "count": 100})
```

### Subscribing to Events

To react when an event is received:

```python
from fernos import events

def handle_data(data):
    print(f"New data received: {data['batch_id']}")

# Subscribe to 'data_ready'
events.on("data_ready", handle_data)
```

### Registering Events

Registration is mandatory before emitting an event. It tells the Manager which job owns the event, allowing it to route notifications correctly to subscribers.

```python
from fernos import events

# Always register before your first emit
events.register("data_ready")
```

## How it works

1. When a `PythonJob` starts, the SDK automatically establishes a dedicated TCP connection to the Fern-OS Manager using the `FERNOS_JOB_TOKEN`.
2. A background listener thread is started to handle incoming event notifications.
3. When you call `emit`, the event is sent over this TCP connection.
4. When the Manager notifies the SDK of an event you've subscribed to, the registered callback is executed in the background thread.

## Configuration

The event system uses the following environment variables (automatically provided to jobs):

- `FERNOS_MANAGER_HOST`: Manager hostname.
- `FERNOS_MANAGER_TCP_PORT`: Manager TCP port (default: `9090`).
- `FERNOS_JOB_TOKEN`: Authentication token for the job.
