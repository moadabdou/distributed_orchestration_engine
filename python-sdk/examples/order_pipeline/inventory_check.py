from fernos import xcom, bridge

bridge.log("Inventory Check: starting")

# Simulate checking stock levels for the ordered items
inventory = {
    "item_a": {"available": 42, "reserved": 5},
    "item_b": {"available": 17, "reserved": 2},
}

bridge.log(f"Inventory snapshot: {inventory}")

# Push stock data to XCom so downstream jobs can read it
xcom.push("stock_snapshot", inventory)
bridge.log("Inventory Check: complete")
