#039 — Chaos Testing Harness

**Milestone:** 6 — Testing & Hardening  
**Labels:** `testing`, `chaos-engineering`, `reliability`, `priority:critical`  
**Assignee:** —  
**Estimate:** 1 day  
**Depends on:** #028, #030  

## Description

Build an automated chaos testing script that validates the system under adversarial conditions — bulk job submission + random worker termination.

### Chaos Script Flow

```bash
#!/bin/bash
# chaos-test.sh

# 1. Start the cluster
docker-compose up -d --scale worker=5

# 2. Wait for all workers to register
sleep 10

# 3. Submit 1000 jobs via API
for i in $(seq 1 1000); do
  curl -s -X POST http://localhost:8080/api/v1/jobs \
    -H "Content-Type: application/json" \
    -d "{\"payload\":{\"type\":\"fibonacci\",\"n\":$((RANDOM % 30))}}" &
done
wait

# 4. Randomly kill workers during execution
for i in $(seq 1 5); do
  sleep $((RANDOM % 10 + 5))
  VICTIM=$(docker-compose ps -q worker | shuf -n1)
  docker kill $VICTIM
  echo "Killed worker container: $VICTIM"
done

# 5. Wait for completion
sleep 60

# 6. Verify: count jobs by status
COMPLETED=$(curl -s "http://localhost:8080/api/v1/jobs?status=COMPLETED&size=1" | jq '.totalElements')
FAILED=$(curl -s "http://localhost:8080/api/v1/jobs?status=FAILED&size=1" | jq '.totalElements')
PENDING=$(curl -s "http://localhost:8080/api/v1/jobs?status=PENDING&size=1" | jq '.totalElements')

echo "COMPLETED: $COMPLETED | FAILED: $FAILED | PENDING: $PENDING"
echo "TOTAL: $((COMPLETED + FAILED))"

# 7. Assert no jobs lost
[ $((COMPLETED + FAILED)) -eq 1000 ] && echo "✅ PASS: No jobs lost!" || echo "❌ FAIL: Jobs lost!"
```

## Acceptance Criteria

- [ ] `scripts/chaos-test.sh` executable and documented
- [ ] Submits 1,000+ jobs to running cluster
- [ ] Randomly kills 3–5 worker containers during execution
- [ ] Waits for system to stabilize
- [ ] Asserts: `COMPLETED + FAILED == total submitted` (zero lost)
- [ ] Outputs summary report with timing and counts
- [ ] Passes on 5 consecutive runs without failure
- [ ] Optional: more aggressive variant — kill Manager too and verify startup recovery
