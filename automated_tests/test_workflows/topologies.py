import random
import math
from .payloads import make_job

def build_linear(depth, job_type, error_rate):
    jobs, deps, tags = [], [], []
    for i in range(depth):
        job, tag = make_job(f"J{i}", job_type, error_rate)
        jobs.append(job)
        tags.append(tag)
        if i > 0:
            deps.append({"fromJobLabel": f"J{i-1}", "toJobLabel": f"J{i}"})
    return jobs, deps, tags

def build_fanout(width, job_type, error_rate):
    jobs, deps, tags = [], [], []
    root, tag = make_job("root", job_type, error_rate)
    jobs.append(root)
    tags.append(f"root:{tag}")
    for i in range(width):
        leaf, tag = make_job(f"leaf{i}", job_type, error_rate)
        jobs.append(leaf)
        tags.append(f"leaf{i}:{tag}")
        deps.append({"fromJobLabel": "root", "toJobLabel": f"leaf{i}"})
    return jobs, deps, tags

def build_fanin(width, job_type, error_rate):
    jobs, deps, tags = [], [], []
    for i in range(width):
        src, tag = make_job(f"src{i}", job_type, error_rate)
        jobs.append(src)
        tags.append(f"src{i}:{tag}")
        deps.append({"fromJobLabel": f"src{i}", "toJobLabel": "sink"})
    sink, tag = make_job("sink", job_type, error_rate)
    jobs.append(sink)
    tags.append(f"sink:{tag}")
    return jobs, deps, tags

def build_diamond(depth, job_type, error_rate):
    jobs, deps, tags = [], [], []

    def add_job(label):
        job, tag = make_job(label, job_type, error_rate)
        jobs.append(job)
        tags.append(f"{label}:{tag}")
        return label

    prev = add_job("root")
    for d in range(depth):
        left = add_job(f"L{d}")
        right = add_job(f"R{d}")
        merge = add_job(f"M{d}")
        deps += [
            {"fromJobLabel": prev, "toJobLabel": left},
            {"fromJobLabel": prev, "toJobLabel": right},
            {"fromJobLabel": left, "toJobLabel": merge},
            {"fromJobLabel": right, "toJobLabel": merge},
        ]
        prev = merge

    tail = add_job("tail")
    deps.append({"fromJobLabel": prev, "toJobLabel": tail})
    return jobs, deps, tags

def build_tree(depth, job_type, error_rate):
    jobs, deps, tags = [], [], []
    counter = [0]

    def add_job(label):
        job, tag = make_job(label, job_type, error_rate)
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

def build_complex(n_nodes, job_type, error_rate):
    jobs, deps, tags = [], [], []
    labels = [f"N{i}" for i in range(n_nodes)]

    for lbl in labels:
        job, tag = make_job(lbl, job_type, error_rate)
        jobs.append(job)
        tags.append(f"{lbl}:{tag}")

    n_layers = max(3, int(math.log2(n_nodes)) + 1)
    layer_sizes = []
    remaining = n_nodes
    for l in range(n_layers):
        size = max(1, remaining // (n_layers - l))
        layer_sizes.append(size)
        remaining -= size
    if remaining:
        layer_sizes[-1] += remaining

    layers = []
    idx = 0
    for size in layer_sizes:
        layers.append(labels[idx: idx + size])
        idx += size

    seen_edges = set()
    for li in range(len(layers) - 1):
        src_layer = layers[li]
        dst_layer = layers[li+1]
        for dst in dst_layer:
            src = random.choice(src_layer)
            edge = (src, dst)
            if edge not in seen_edges:
                seen_edges.add(edge)
                deps.append({"fromJobLabel": src, "toJobLabel": dst})
        extra = random.randint(0, max(1, len(src_layer)))
        for _ in range(extra):
            src = random.choice(src_layer)
            dst = random.choice(dst_layer)
            edge = (src, dst)
            if src != dst and edge not in seen_edges:
                seen_edges.add(edge)
                deps.append({"fromJobLabel": src, "toJobLabel": dst})

    return jobs, deps, tags

def build_random_dag(n_nodes, job_type, error_rate, edge_prob=0.25):
    jobs, deps, tags = [], [], []
    labels = [f"V{i}" for i in range(n_nodes)]

    for lbl in labels:
        job, tag = make_job(lbl, job_type, error_rate)
        jobs.append(job)
        tags.append(f"{lbl}:{tag}")

    seen_edges = set()
    for j in range(1, n_nodes):
        i = random.randint(0, j - 1)
        seen_edges.add((i, j))
        deps.append({"fromJobLabel": labels[i], "toJobLabel": labels[j]})
        for i in range(j):
            if (i, j) not in seen_edges and random.random() < edge_prob:
                seen_edges.add((i, j))
                deps.append({"fromJobLabel": labels[i], "toJobLabel": labels[j]})

    return jobs, deps, tags

TOPOLOGIES = ("linear", "fanout", "fanin", "diamond", "tree", "complex", "random")

def build_graph(topology, depth, job_type, error_rate):
    if topology == "linear":
        return build_linear(depth, job_type, error_rate)
    elif topology == "fanout":
        return build_fanout(depth, job_type, error_rate)
    elif topology == "fanin":
        return build_fanin(depth, job_type, error_rate)
    elif topology == "diamond":
        return build_diamond(depth, job_type, error_rate)
    elif topology == "tree":
        return build_tree(depth, job_type, error_rate)
    elif topology == "complex":
        return build_complex(max(6, depth * 3), job_type, error_rate)
    elif topology == "random":
        return build_random_dag(max(4, depth * 2), job_type, error_rate)
    else:
        raise ValueError(f"Unknown topology: {topology}")
