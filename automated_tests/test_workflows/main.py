import argparse
import random
import time
from test_workflows.api_client import clear_db, submit_workflow
from test_workflows.topologies import build_graph, TOPOLOGIES

def main():
    parser = argparse.ArgumentParser(
        description="Automated workflow tests – Distributed Orchestration Engine"
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
        help="Depth / width parameter for topologies (default: 3)",
    )
    parser.add_argument(
        "--topology", default="mixed",
        choices=list(TOPOLOGIES) + ["mixed"],
        help="DAG topology to generate (default: mixed)",
    )
    parser.add_argument(
        "--jobs", default="sleep",
        help="Job types to use (e.g. 'sleep', 'python', 'bash', or 'mix' for random per job) (default: sleep)",
    )
    parser.add_argument(
        "--error-rate", type=float, default=0.0,
        help="Fraction [0.0–1.0] of jobs with injected faults (default: 0.0)",
    )
    parser.add_argument(
        "--delay", type=float, default=0.1,
        help="Delay in seconds between workflow submissions (default: 0.1)",
    )

    args = parser.parse_args()

    if args.clear:
        clear_db()
        time.sleep(1)

    print(
        f"\nSubmitting {args.count} workflow(s) | topology={args.topology} | "
        f"depth={args.depth} | jobs={args.jobs} | error_rate={args.error_rate:.0%}\n"
    )

    for i in range(args.count):
        chosen_topo = args.topology if args.topology != "mixed" else random.choice(TOPOLOGIES)
        jobs, deps, tags = build_graph(chosen_topo, args.depth, args.jobs, args.error_rate)
        
        name = f"WF-{i}-{chosen_topo}-{int(time.time())}"
        submit_workflow(i, name, jobs, deps)
        
        error_summary = [t for t in tags if not t.endswith(":ok") and ":ok" not in t]
        if error_summary:
            print(f"  => injected faults: {error_summary}")
            
        time.sleep(args.delay)

if __name__ == "__main__":
    main()
