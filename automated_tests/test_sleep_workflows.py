"""
test_sleep_workflows.py
=======================
Automated smoke tests for the Distributed Orchestration Engine.

Error modes (injected with probability --error-rate):
  - bad_arg   : uses key "shit" instead of "ms"  -> worker FAIL (unknown arg)
  - timeout   : sleep_ms > 60 000 ms              -> worker TIMEOUT
  - crash     : both bad_arg AND timeout combined

Graph topologies (--topology):
  linear    : A -> B -> C -> …  (original behaviour)
  fanout    : root  -> N leaves (all parallel)
  fanin     : N roots -> single sink
  diamond   : root -> [l, r] -> merge -> tail
  tree      : balanced binary tree of given depth
  complex   : multi-root, multi-sink arbitrary DAG
  random    : random sparse DAG (Erdős–Rényi style, DAG ensured by ordering)
"""

import argparse
import subprocess
import urllib.request
import urllib.error
import json
import random
import time
import math


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def clear_db():
    print("Clearing database...")
    sql_command = "delete from jobs; delete from job_dependencies; delete from workflows;"
    cmd = [
        "docker", "exec", "-i", "postgres-instance",
        "psql", "-U", "root", "-d", "fernos",
        "-c", sql_command,
    ]
    try:
        subprocess.run(cmd, check=True)
        print("Database cleared successfully.")
    except subprocess.CalledProcessError as e:
        print(f"Failed to clear database. Error: {e}")


def execute_workflow(workflow_id):
    req = urllib.request.Request(
        f"http://localhost:8080/api/v1/workflows/{workflow_id}/execute",
        data=b"",
        headers={"Content-Length": "0"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as response:
            status = response.status
            tag = "OK" if status == 200 else "FAIL"
            print(f"  [{tag}] Execute workflow {workflow_id} | HTTP {status}")
    except urllib.error.URLError as e:
        print(f"  [ERR] Execute workflow {workflow_id} | {e}")


# ---------------------------------------------------------------------------
# Job payload factories
# ---------------------------------------------------------------------------

TIMEOUT_THRESHOLD_MS = 60_000   # anything above this is "definitely too long"
NORMAL_SLEEP_RANGE   = (2_000, 20_000)


def _make_payload(error_rate: float) -> tuple[dict, str]:
    """
    Returns (payload_dict, mode_tag) where mode_tag describes what was injected.
    """
    roll = random.random()
    if roll < error_rate:
        error_kind = random.choice(["bad_arg", "timeout", "crash"])
        if error_kind == "bad_arg":
            return {"type": "sleep", "shit": str(random.randint(*NORMAL_SLEEP_RANGE))}, "bad_arg"
        elif error_kind == "timeout":
            return {"type": "sleep", "ms": str(random.randint(TIMEOUT_THRESHOLD_MS, TIMEOUT_THRESHOLD_MS * 3))}, "timeout"
        else:  # crash = both
            return {"type": "sleep", "shit": str(random.randint(TIMEOUT_THRESHOLD_MS, TIMEOUT_THRESHOLD_MS * 3))}, "crash"
    return {"type": "sleep", "ms": str(random.randint(*NORMAL_SLEEP_RANGE))}, "ok"


def _make_job(label: str, error_rate: float) -> tuple[dict, str]:
    payload, tag = _make_payload(error_rate)
    return {"label": label, "payload": json.dumps(payload)}, tag


# ---------------------------------------------------------------------------
# Topology builders  ->  each returns (jobs: list, dependencies: list)
# ---------------------------------------------------------------------------

def build_linear(depth: int, error_rate: float):
    """A -> B -> C -> …"""
    jobs, deps, tags = [], [], []
    for i in range(depth):
        job, tag = _make_job(f"J{i}", error_rate)
        jobs.append(job)
        tags.append(tag)
        if i > 0:
            deps.append({"fromJobLabel": f"J{i-1}", "toJobLabel": f"J{i}"})
    return jobs, deps, tags


def build_fanout(width: int, error_rate: float):
    """root -> [leaf_0, leaf_1, …, leaf_N]"""
    jobs, deps, tags = [], [], []
    root, tag = _make_job("root", error_rate)
    jobs.append(root)
    tags.append(f"root:{tag}")
    for i in range(width):
        leaf, tag = _make_job(f"leaf{i}", error_rate)
        jobs.append(leaf)
        tags.append(f"leaf{i}:{tag}")
        deps.append({"fromJobLabel": "root", "toJobLabel": f"leaf{i}"})
    return jobs, deps, tags


def build_fanin(width: int, error_rate: float):
    """[src_0, …, src_N] -> sink"""
    jobs, deps, tags = [], [], []
    for i in range(width):
        src, tag = _make_job(f"src{i}", error_rate)
        jobs.append(src)
        tags.append(f"src{i}:{tag}")
        deps.append({"fromJobLabel": f"src{i}", "toJobLabel": "sink"})
    sink, tag = _make_job("sink", error_rate)
    jobs.append(sink)
    tags.append(f"sink:{tag}")
    return jobs, deps, tags


def build_diamond(depth: int, error_rate: float):
    """
    root -> [left_0, right_0] -> merge_0 -> [left_1, right_1] -> merge_1 -> … -> tail
    Repeats the diamond pattern `depth` times.
    """
    jobs, deps, tags = [], [], []

    def add_job(label):
        job, tag = _make_job(label, error_rate)
        jobs.append(job)
        tags.append(f"{label}:{tag}")
        return label

    prev = add_job("root")
    for d in range(depth):
        left  = add_job(f"L{d}")
        right = add_job(f"R{d}")
        merge = add_job(f"M{d}")
        deps += [
            {"fromJobLabel": prev,  "toJobLabel": left},
            {"fromJobLabel": prev,  "toJobLabel": right},
            {"fromJobLabel": left,  "toJobLabel": merge},
            {"fromJobLabel": right, "toJobLabel": merge},
        ]
        prev = merge

    tail = add_job("tail")
    deps.append({"fromJobLabel": prev, "toJobLabel": tail})
    return jobs, deps, tags


def build_tree(depth: int, error_rate: float):
    """Balanced binary tree: each node has two children, up to `depth` levels."""
    jobs, deps, tags = [], [], []
    counter = [0]

    def add_job(label):
        job, tag = _make_job(label, error_rate)
        jobs.append(job)
        tags.append(f"{label}:{tag}")
        return label

    def recurse(parent_label, level):
        if level >= depth:
            return
        for side in ("a", "b"):
            child_label = f"n{counter[0]}{side}"
            counter[0] += 1
            add_job(child_label)
            deps.append({"fromJobLabel": parent_label, "toJobLabel": child_label})
            recurse(child_label, level + 1)

    root_label = "root"
    add_job(root_label)
    recurse(root_label, 0)
    return jobs, deps, tags


def build_complex(n_nodes: int, error_rate: float):
    """
    Multi-root, multi-sink hand-crafted complex DAG.
    Combines fan-out, fan-in, serial chains, and cross-edges.

    Layout (example for n_nodes=12):
      roots: A, B
      mid-layer 1:  C, D, E
      mid-layer 2:  F, G
      convergence:  H
      parallel tails: I, J
      final sink:   K

    For arbitrary n_nodes the graph is grown layer by layer.
    """
    jobs, deps, tags = [], [], []
    labels = [f"N{i}" for i in range(n_nodes)]

    for lbl in labels:
        job, tag = _make_job(lbl, error_rate)
        jobs.append(job)
        tags.append(f"{lbl}:{tag}")

    # Divide nodes into layers; each layer connects to some nodes in next layer
    n_layers = max(3, int(math.log2(n_nodes)) + 1)
    layer_sizes = []
    remaining = n_nodes
    for l in range(n_layers):
        size = max(1, remaining // (n_layers - l))
        layer_sizes.append(size)
        remaining -= size
    if remaining:
        layer_sizes[-1] += remaining

    # Build layers
    layers = []
    idx = 0
    for size in layer_sizes:
        layers.append(labels[idx: idx + size])
        idx += size

    # Connect layers with random cross-connections (ensure DAG by layer ordering)
    seen_edges = set()
    for li in range(len(layers) - 1):
        src_layer  = layers[li]
        dst_layer  = layers[li + 1]
        # Ensure every dst node has at least one parent
        for dst in dst_layer:
            src = random.choice(src_layer)
            edge = (src, dst)
            if edge not in seen_edges:
                seen_edges.add(edge)
                deps.append({"fromJobLabel": src, "toJobLabel": dst})
        # Add some random extra cross-edges
        extra = random.randint(0, max(1, len(src_layer)))
        for _ in range(extra):
            src = random.choice(src_layer)
            dst = random.choice(dst_layer)
            edge = (src, dst)
            if src != dst and edge not in seen_edges:
                seen_edges.add(edge)
                deps.append({"fromJobLabel": src, "toJobLabel": dst})

    return jobs, deps, tags


def build_random_dag(n_nodes: int, edge_prob: float, error_rate: float):
    """
    Random sparse DAG: nodes ordered 0..N-1.
    Edge (i->j) included with probability `edge_prob` for all i < j.
    Forces connectivity: each node i>0 has at least one parent in [0..i-1].
    """
    jobs, deps, tags = [], [], []
    labels = [f"V{i}" for i in range(n_nodes)]

    for lbl in labels:
        job, tag = _make_job(lbl, error_rate)
        jobs.append(job)
        tags.append(f"{lbl}:{tag}")

    seen_edges = set()
    for j in range(1, n_nodes):
        # Guarantee at least one parent
        i = random.randint(0, j - 1)
        seen_edges.add((i, j))
        deps.append({"fromJobLabel": labels[i], "toJobLabel": labels[j]})
        # Random extra parents
        for i in range(j):
            if (i, j) not in seen_edges and random.random() < edge_prob:
                seen_edges.add((i, j))
                deps.append({"fromJobLabel": labels[i], "toJobLabel": labels[j]})

    return jobs, deps, tags


# ---------------------------------------------------------------------------
# Topology dispatcher
# ---------------------------------------------------------------------------

TOPOLOGIES = ("linear", "fanout", "fanin", "diamond", "tree", "complex", "random")


def build_graph(topology: str, depth: int, error_rate: float):
    if topology == "linear":
        return build_linear(depth, error_rate)
    elif topology == "fanout":
        return build_fanout(depth, error_rate)
    elif topology == "fanin":
        return build_fanin(depth, error_rate)
    elif topology == "diamond":
        return build_diamond(depth, error_rate)
    elif topology == "tree":
        return build_tree(depth, error_rate)
    elif topology == "complex":
        return build_complex(max(6, depth * 3), error_rate)
    elif topology == "random":
        return build_random_dag(max(4, depth * 2), edge_prob=0.25, error_rate=error_rate)
    else:
        raise ValueError(f"Unknown topology: {topology}")


# ---------------------------------------------------------------------------
# Submission
# ---------------------------------------------------------------------------

def submit_workflow(index: int, topology: str, depth: int, error_rate: float):
    chosen_topo = topology if topology != "mixed" else random.choice(TOPOLOGIES)
    jobs, deps, tags = build_graph(chosen_topo, depth, error_rate)

    error_summary = [t for t in tags if not t.endswith(":ok")]
    n_errors = len(error_summary)

    data = {
        "name": f"WF-{index}-{chosen_topo}-{int(time.time())}",
        "jobs": jobs,
        "dependencies": deps,
    }

    req_body = json.dumps(data).encode("utf-8")
    req = urllib.request.Request(
        "http://localhost:8080/api/v1/workflows",
        data=req_body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(req) as response:
            resp_json = json.loads(response.read().decode("utf-8"))
            workflow_id = resp_json.get("id")
            print(
                f"[WF-{index}] topo={chosen_topo} jobs={len(jobs)} "
                f"deps={len(deps)} errors={n_errors} | "
                f"HTTP {response.status} | id={workflow_id}"
            )
            if error_summary:
                print(f"  => injected faults: {error_summary}")
            if workflow_id:
                execute_workflow(workflow_id)
    except urllib.error.URLError as e:
        print(f"[WF-{index}] Submit FAILED: {e}")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Automated smoke tests – Distributed Orchestration Engine"
    )
    parser.add_argument(
        "-clear", action="store_true",
        help="Clear the database via Docker psql before running",
    )
    parser.add_argument(
        "--count", type=int, default=5,
        help="Number of workflows to submit (default: 5)",
    )
    parser.add_argument(
        "--depth", type=int, default=3,
        help=(
            "Depth / width parameter for topologies:\n"
            "  linear  -> chain length\n"
            "  fanout  -> number of leaves\n"
            "  fanin   -> number of sources\n"
            "  diamond -> number of diamond stages\n"
            "  tree    -> tree depth\n"
            "  complex -> multiplied by 3 for node count\n"
            "  random  -> multiplied by 2 for node count\n"
            "(default: 3)"
        ),
    )
    parser.add_argument(
        "--topology", default="mixed",
        choices=list(TOPOLOGIES) + ["mixed"],
        help="DAG topology to generate (default: mixed – random per workflow)",
    )
    parser.add_argument(
        "--error-rate", type=float, default=0.0,
        metavar="RATE",
        help=(
            "Fraction [0.0–1.0] of jobs that will receive an injected fault.\n"
            "Faults are randomly chosen from: bad_arg (key='shit'), "
            "timeout (sleep > 60 s), crash (both). (default: 0.0)"
        ),
    )
    # Legacy flag for backward compatibility
    parser.add_argument(
        "-err", action="store_true",
        help="Shorthand for --error-rate 0.10 (10%% error injection)",
    )
    parser.add_argument(
        "--delay", type=float, default=0.1,
        metavar="SEC",
        help="Delay in seconds between workflow submissions (default: 0.1)",
    )

    args = parser.parse_args()

    error_rate = args.error_rate
    if args.err and error_rate == 0.0:
        error_rate = 0.10

    if args.clear:
        clear_db()
        time.sleep(1)

    print(
        f"\nSubmitting {args.count} workflow(s) | topology={args.topology} | "
        f"depth={args.depth} | error_rate={error_rate:.0%}\n"
    )

    for i in range(args.count):
        submit_workflow(i, args.topology, args.depth, error_rate)
        time.sleep(args.delay)
