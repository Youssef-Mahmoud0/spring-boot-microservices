#!/usr/bin/env python3
"""
Performance test for movie-info-service.
Compares two endpoints on the SAME running application with EQUAL request counts:

  WITHOUT cache : GET http://localhost:8082/movies/no-caching/{movieId}
  WITH    cache : GET http://localhost:8082/movies/{movieId}

Cache hit/miss detection via HTTP status codes (cached endpoint only):
  200 → cache HIT  (served from MongoDB)
  201 → cache MISS (fetched from data source, then stored)
  404 → not found

Both phases use the EXACT SAME shuffled request list so metrics are comparable.
A configurable % of requests use fake (not-found) IDs to simulate real-world misses.

Usage:
    python3 perf_test.py
    python3 perf_test.py --ids movies_ids.txt --workers 20 --total 1000
    python3 perf_test.py --not-found-pct 20
    python3 perf_test.py --output results.csv
"""

import requests
import time
import argparse
import statistics
import random
import csv
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime

BASE_URL         = "http://localhost:8082/movies"
NO_CACHE_SUFFIX  = "no-caching"
DEFAULT_IDS_FILE = "movies_ids.txt"
DEFAULT_WORKERS  = 10
DEFAULT_TOTAL    = 10000
DEFAULT_NF_PCT   = 5
TIMEOUT          = 30

# Status code semantics (cached endpoint)
STATUS_HIT      = 200   # served from MongoDB
STATUS_MISS     = 201   # fetched from source, now cached
STATUS_NOTFOUND = 404


# ─────────────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────────────

def load_ids(filepath: str) -> list:
    with open(filepath) as f:
        ids = [line.strip() for line in f if line.strip()]
    if not ids:
        raise ValueError(f"No IDs found in '{filepath}'")
    return ids


def build_request_list(real_ids: list, total: int, not_found_pct: float) -> list:
    """
    Returns a shuffled list of (movie_id, is_fake) tuples.
    - (100 - not_found_pct)% are randomly sampled real IDs (with replacement)
    - not_found_pct% are random fake IDs guaranteed to return 404
    """
    n_fake  = int(total * not_found_pct / 100)
    n_real  = total - n_fake

    real_sample = [random.choice(real_ids) for _ in range(n_real)]
    fake_sample = [f"FAKE-{uuid.uuid4().hex[:8]}" for _ in range(n_fake)]

    combined = [(mid, False) for mid in real_sample] + \
               [(mid, True)  for mid in fake_sample]
    random.shuffle(combined)
    return combined


def fetch(url: str, movie_id: str, is_fake: bool) -> dict:
    start = time.perf_counter()
    try:
        r = requests.get(url, timeout=TIMEOUT)
        elapsed_ms = (time.perf_counter() - start) * 1000
        status = r.status_code
        return {
            "id":         movie_id,
            "is_fake":    is_fake,
            "status":     status,
            "latency_ms": elapsed_ms,
            "is_hit":     status == STATUS_HIT,
            "is_miss":    status == STATUS_MISS,
            "not_found":  status == STATUS_NOTFOUND,
            "success":    status in (STATUS_HIT, STATUS_MISS),
            "error":      None,
        }
    except requests.exceptions.Timeout:
        elapsed_ms = (time.perf_counter() - start) * 1000
        return {"id": movie_id, "is_fake": is_fake, "status": None,
                "latency_ms": elapsed_ms, "is_hit": False, "is_miss": False,
                "not_found": False, "success": False, "error": "Timeout"}
    except requests.exceptions.ConnectionError:
        return {"id": movie_id, "is_fake": is_fake, "status": None,
                "latency_ms": 0, "is_hit": False, "is_miss": False,
                "not_found": False, "success": False, "error": "ConnectionError"}
    except Exception as e:
        return {"id": movie_id, "is_fake": is_fake, "status": None,
                "latency_ms": 0, "is_hit": False, "is_miss": False,
                "not_found": False, "success": False, "error": str(e)}


# ─────────────────────────────────────────────────────────────────────────────
# Load test runner
# ─────────────────────────────────────────────────────────────────────────────

def run_phase(request_list: list, workers: int, use_cache: bool) -> dict:
    def url_for(mid):
        return f"{BASE_URL}/{mid}" if use_cache \
               else f"{BASE_URL}/{NO_CACHE_SUFFIX}/{mid}"

    results    = []
    wall_start = time.perf_counter()

    with ThreadPoolExecutor(max_workers=workers) as executor:
        futures = {
            executor.submit(fetch, url_for(mid), mid, is_fake): (mid, is_fake)
            for mid, is_fake in request_list
        }
        done = 0
        for future in as_completed(futures):
            results.append(future.result())
            done += 1
            if done % 50 == 0 or done == len(request_list):
                print(f"    {done}/{len(request_list)} done", end="\r")

    wall_elapsed = time.perf_counter() - wall_start
    print()
    return {"results": results, "wall_sec": wall_elapsed}


# ─────────────────────────────────────────────────────────────────────────────
# Stats
# ─────────────────────────────────────────────────────────────────────────────

def percentile(sorted_data: list, p: float) -> float:
    if not sorted_data:
        return 0.0
    idx = max(0, int(len(sorted_data) * p / 100) - 1)
    return sorted_data[idx]


def compute_stats(phase_data: dict, label: str, use_cache: bool) -> dict:
    results  = phase_data["results"]
    wall_sec = phase_data["wall_sec"]

    total      = len(results)
    real_reqs  = [r for r in results if not r["is_fake"]]
    fake_reqs  = [r for r in results if r["is_fake"]]
    successes  = [r for r in results if r["success"]]
    not_founds = [r for r in results if r["not_found"]]
    errors     = [r for r in results if not r["success"] and not r["not_found"]]

    # Latency over all responses that actually got a reply (including 404)
    responded = [r for r in results if r["latency_ms"] > 0]
    latencies = sorted(r["latency_ms"] for r in responded)

    # Hit/miss — derived from status codes, only meaningful for cached endpoint
    if use_cache:
        hits       = sum(1 for r in real_reqs if r["is_hit"])
        misses     = sum(1 for r in real_reqs if r["is_miss"])
        real_responded = hits + misses
        hit_ratio  = round(hits   / real_responded, 4) if real_responded > 0 else None
        miss_ratio = round(misses / real_responded, 4) if real_responded > 0 else None
    else:
        hits = misses = hit_ratio = miss_ratio = None

    return {
        "label":      label,
        "total":      total,
        "real_reqs":  len(real_reqs),
        "fake_reqs":  len(fake_reqs),
        "success":    len(successes),
        "hits":       hits,
        "misses":     misses,
        "not_found":  len(not_founds),
        "errors":     len(errors),
        "wall_sec":   round(wall_sec, 3),
        "throughput": round(total / wall_sec, 2) if wall_sec > 0 else 0,
        "avg_ms":     round(statistics.mean(latencies),  2) if latencies else 0,
        "p50_ms":     round(percentile(latencies, 50),   2),
        "p90_ms":     round(percentile(latencies, 90),   2),
        "p95_ms":     round(percentile(latencies, 95),   2),
        "p99_ms":     round(percentile(latencies, 99),   2),
        "min_ms":     round(min(latencies),              2) if latencies else 0,
        "max_ms":     round(max(latencies),              2) if latencies else 0,
        "stdev_ms":   round(statistics.stdev(latencies), 2) if len(latencies) > 1 else 0,
        "hit_ratio":  hit_ratio,
        "miss_ratio": miss_ratio,
    }


# ─────────────────────────────────────────────────────────────────────────────
# Output
# ─────────────────────────────────────────────────────────────────────────────

def fmt_ratio(v):
    return f"{v:.2%}" if v is not None else "N/A"

def fmt_int(v):
    return f"{v:,}" if v is not None else "N/A"


def print_stats(s: dict):
    print(f"""
  ┌──────────────────────────────────────────────────┐
  │  {s['label']:<48} │
  ├──────────────────────────────────────────────────┤
  │  Total requests  : {fmt_int(s['total']):>10}                   │
  │  Real IDs        : {fmt_int(s['real_reqs']):>10}                   │
  │  Fake IDs        : {fmt_int(s['fake_reqs']):>10}                   │
  │  200 Hit (cache) : {fmt_int(s['hits']):>10}                   │
  │  201 Miss(fetch) : {fmt_int(s['misses']):>10}                   │
  │  404 Not Found   : {fmt_int(s['not_found']):>10}                   │
  │  Errors          : {fmt_int(s['errors']):>10}                   │
  ├──────────────────────────────────────────────────┤
  │  Wall time       : {s['wall_sec']:>10.2f} s               │
  │  Throughput      : {s['throughput']:>10.2f} req/s          │
  ├──────────────────────────────────────────────────┤
  │  Avg latency     : {s['avg_ms']:>10.2f} ms             │
  │  P50 latency     : {s['p50_ms']:>10.2f} ms             │
  │  P90 latency     : {s['p90_ms']:>10.2f} ms             │
  │  P95 latency     : {s['p95_ms']:>10.2f} ms             │
  │  P99 latency     : {s['p99_ms']:>10.2f} ms             │
  │  Min latency     : {s['min_ms']:>10.2f} ms             │
  │  Max latency     : {s['max_ms']:>10.2f} ms             │
  │  Std deviation   : {s['stdev_ms']:>10.2f} ms             │
  ├──────────────────────────────────────────────────┤
  │  Hit  ratio (200): {fmt_ratio(s['hit_ratio']):>10}                   │
  │  Miss ratio (201): {fmt_ratio(s['miss_ratio']):>10}                   │
  └──────────────────────────────────────────────────┘""")


def print_comparison(nc: dict, wc: dict):
    def diff(b, a, higher_is_better=False):
        if b is None or a is None or b == 0:
            return "    N/A "
        pct = ((a - b) / b) * 100
        if not higher_is_better:
            pct = -pct
        icon = "✅" if pct > 0 else "❌"
        return f"{icon}{abs(pct):5.1f}%"

    print(f"""
  ╔═══════════════════════════════════════════════════════════════════╗
  ║  SAME {nc['total']:,} REQUESTS — NO-CACHE  vs  WITH-CACHE             ║
  ╠════════════════════╦═══════════════╦═══════════════╦═════════════╣
  ║ Metric             ║   No Cache    ║  With Cache   ║   Change    ║
  ╠════════════════════╬═══════════════╬═══════════════╬═════════════╣
  ║ Throughput (req/s) ║ {nc['throughput']:>13.2f} ║ {wc['throughput']:>13.2f} ║ {diff(nc['throughput'], wc['throughput'], higher_is_better=True):>11} ║
  ║ Avg latency   (ms) ║ {nc['avg_ms']:>13.2f} ║ {wc['avg_ms']:>13.2f} ║ {diff(nc['avg_ms'], wc['avg_ms']):>11} ║
  ║ P50 latency   (ms) ║ {nc['p50_ms']:>13.2f} ║ {wc['p50_ms']:>13.2f} ║ {diff(nc['p50_ms'], wc['p50_ms']):>11} ║
  ║ P90 latency   (ms) ║ {nc['p90_ms']:>13.2f} ║ {wc['p90_ms']:>13.2f} ║ {diff(nc['p90_ms'], wc['p90_ms']):>11} ║
  ║ P95 latency   (ms) ║ {nc['p95_ms']:>13.2f} ║ {wc['p95_ms']:>13.2f} ║ {diff(nc['p95_ms'], wc['p95_ms']):>11} ║
  ║ P99 latency   (ms) ║ {nc['p99_ms']:>13.2f} ║ {wc['p99_ms']:>13.2f} ║ {diff(nc['p99_ms'], wc['p99_ms']):>11} ║
  ╠════════════════════╬═══════════════╬═══════════════╬═════════════╣
  ║ Hit  ratio   (200) ║ {'N/A':>13} ║ {fmt_ratio(wc['hit_ratio']):>13} ║             ║
  ║ Miss ratio   (201) ║ {'N/A':>13} ║ {fmt_ratio(wc['miss_ratio']):>13} ║             ║
  ║ 404 Not Found      ║ {nc['not_found']:>13,} ║ {wc['not_found']:>13,} ║             ║
  ╚════════════════════╩═══════════════╩═══════════════╩═════════════╝""")


def save_csv(nc: dict, wc: dict, filepath: str):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    fields = [
        "timestamp", "endpoint_type", "label",
        "total", "real_reqs", "fake_reqs",
        "success", "hits", "misses", "not_found", "errors",
        "wall_sec", "throughput",
        "avg_ms", "p50_ms", "p90_ms", "p95_ms", "p99_ms",
        "min_ms", "max_ms", "stdev_ms",
        "hit_ratio", "miss_ratio",
    ]

    rows = []
    for ep_type, s in [("no_cache", nc), ("with_cache", wc)]:
        rows.append({
            "timestamp":     timestamp,
            "endpoint_type": ep_type,
            "label":         s["label"],
            "total":         s["total"],
            "real_reqs":     s["real_reqs"],
            "fake_reqs":     s["fake_reqs"],
            "success":       s["success"],
            "hits":          s["hits"]       if s["hits"]       is not None else "",
            "misses":        s["misses"]     if s["misses"]     is not None else "",
            "not_found":     s["not_found"],
            "errors":        s["errors"],
            "wall_sec":      s["wall_sec"],
            "throughput":    s["throughput"],
            "avg_ms":        s["avg_ms"],
            "p50_ms":        s["p50_ms"],
            "p90_ms":        s["p90_ms"],
            "p95_ms":        s["p95_ms"],
            "p99_ms":        s["p99_ms"],
            "min_ms":        s["min_ms"],
            "max_ms":        s["max_ms"],
            "stdev_ms":      s["stdev_ms"],
            "hit_ratio":     s["hit_ratio"]  if s["hit_ratio"]  is not None else "",
            "miss_ratio":    s["miss_ratio"] if s["miss_ratio"] is not None else "",
        })

    with open(filepath, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)

    print(f"\n  Results saved to '{filepath}'")


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Perf test: /movies/no-caching/{id}  vs  /movies/{id}"
    )
    parser.add_argument("--ids",           default=DEFAULT_IDS_FILE)
    parser.add_argument("--workers",       type=int,   default=DEFAULT_WORKERS)
    parser.add_argument("--total",         type=int,   default=DEFAULT_TOTAL,
                        help="Total requests per phase (default: 500)")
    parser.add_argument("--not-found-pct", type=float, default=DEFAULT_NF_PCT,
                        help="%% of requests using fake IDs (default: 5)")
    parser.add_argument("--output",        default="perf_results.csv")
    parser.add_argument("--skip-clear",    action="store_true",
                        help="Skip cache clear before test (default: clears cache)")
    args = parser.parse_args()

    print("\n" + "=" * 62)
    print("  Movie Info Service — Performance Comparison")
    print("=" * 62)
    print(f"  No-cache endpoint : {BASE_URL}/{NO_CACHE_SUFFIX}/{{id}}")
    print(f"  Cache endpoint    : {BASE_URL}/{{id}}")
    print(f"  Hit  = HTTP 200   | Miss = HTTP 201 | Not Found = HTTP 404")
    print(f"  Workers           : {args.workers}")
    print(f"  Requests/phase    : {args.total:,}  (SAME list for both phases)")
    print(f"  Fake ID ratio     : {args.not_found_pct}%")

    real_ids = load_ids(args.ids)
    print(f"  Loaded {len(real_ids):,} real IDs from '{args.ids}'")

    # ONE shared request list for BOTH phases
    request_list = build_request_list(real_ids, args.total, args.not_found_pct)
    real_count   = sum(1 for _, fake in request_list if not fake)
    fake_count   = sum(1 for _, fake in request_list if fake)
    print(f"  Request list      : {real_count:,} real + {fake_count:,} fake = {args.total:,} total")

    # ── Phase 1: No-cache ─────────────────────────────────────────────────────
    print("\n" + "─" * 62)
    print("  PHASE 1 — No-Cache  (/movies/no-caching/{id})")
    print(f"  {args.total:,} requests — every call hits the data source")
    print("─" * 62)

    nc_data  = run_phase(request_list, args.workers, use_cache=False)
    nc_stats = compute_stats(nc_data, "NO-CACHE  /movies/no-caching/{id}", use_cache=False)
    print_stats(nc_stats)

    # ── Phase 2a: Warm-up (fill MongoDB with all real IDs from request list) ──
    unique_real_ids = list({mid for mid, fake in request_list if not fake})
    print("\n" + "─" * 62)
    print(f"  PHASE 2a — Cache Warm-up")
    print(f"  Populating MongoDB with {len(unique_real_ids):,} unique real IDs...")
    print("─" * 62)

    warm_list = [(mid, False) for mid in unique_real_ids]
    run_phase(warm_list, args.workers, use_cache=True)
    print(f"  Warm-up complete. All real IDs now cached (HTTP 201 → stored).")

    # ── Phase 2b: With-cache — SAME list as Phase 1 ──────────────────────────
    print("\n" + "─" * 62)
    print("  PHASE 2b — With-Cache  (/movies/{id})")
    print(f"  SAME {args.total:,} requests — real IDs → 200 (hit), fake → 404")
    print("─" * 62)

    wc_data  = run_phase(request_list, args.workers, use_cache=True)
    wc_stats = compute_stats(wc_data, "WITH-CACHE /movies/{id}", use_cache=True)
    print_stats(wc_stats)

    # ── Comparison + CSV ──────────────────────────────────────────────────────
    print_comparison(nc_stats, wc_stats)
    save_csv(nc_stats, wc_stats, args.output)
    print("\n  Done.\n")


if __name__ == "__main__":
    main()