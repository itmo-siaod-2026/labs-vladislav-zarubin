import os
import csv
import time
from typing import Any
import numpy as np

from main import (
    TOP_K,
    prepare,
    build_hnsw, build_lsh, build_ivfpq,
    eval_hnsw, eval_faiss,
    index_size_mb,
)
from graphs import plot_sweep, plot_comparison

ResultRow = dict[str, Any]

HNSW_GRID = [
    (m, ef) for m in (4, 8, 16, 32) for ef in (20, 40, 80)
]
LSH_NBITS = [64, 128, 256, 512, 1024, 2048]
IVFPQ_GRID = [
    (nlist, m_pq, nprobe)
    for nlist in (64, 256, 1024)
    for m_pq in (15, 30, 60)
    for nprobe in (1, 4, 16, 64)
]


def run_hnsw(
    vectors: np.ndarray,
    queries: np.ndarray,
    query_idx: np.ndarray,
    ground_truth: np.ndarray,
) -> list[ResultRow]:
    """Свип HNSW по сетке (M, ef_construction)."""
    results: list[ResultRow] = []
    for m, ef_construction in HNSW_GRID:
        ef_query = max(ef_construction, TOP_K + 1)
        t_start = time.perf_counter()
        index = build_hnsw(vectors, m, ef_construction, ef_query)
        build_time = time.perf_counter() - t_start
        recall, qps = eval_hnsw(index, queries, query_idx, ground_truth, TOP_K)
        size_mb = index_size_mb(index)
        results.append({
            "m": m, "ef_c": ef_construction, "ef_q": ef_query,
            "build_s": build_time, "recall": recall, "qps": qps, "size_mb": size_mb,
        })
    return results


def run_lsh(
    vectors: np.ndarray,
    queries: np.ndarray,
    query_idx: np.ndarray,
    ground_truth: np.ndarray,
) -> list[ResultRow]:
    """Свип LSH по nbits."""
    results: list[ResultRow] = []
    for nbits in LSH_NBITS:
        t_start = time.perf_counter()
        index = build_lsh(vectors, nbits)
        build_time = time.perf_counter() - t_start
        recall, qps = eval_faiss(index, queries, query_idx, ground_truth, TOP_K)
        size_mb = index_size_mb(index)
        results.append({
            "nbits": nbits,
            "build_s": build_time, "recall": recall, "qps": qps, "size_mb": size_mb,
        })
    return results


def run_ivfpq(
    vectors: np.ndarray,
    queries: np.ndarray,
    query_idx: np.ndarray,
    ground_truth: np.ndarray,
) -> list[ResultRow]:
    """Свип IVF+PQ по (nlist, m_pq, nprobe). Кеширует индекс по (nlist, m_pq) — nprobe меняется без перестройки."""
    results: list[ResultRow] = []
    cache: dict[tuple[int, int], tuple[Any, float, float]] = {}
    for nlist, m_pq, nprobe in IVFPQ_GRID:
        cache_key = (nlist, m_pq)
        if cache_key not in cache:
            t_start = time.perf_counter()
            index = build_ivfpq(vectors, nlist, m_pq, nprobe)
            build_time = time.perf_counter() - t_start
            size_mb = index_size_mb(index)
            cache[cache_key] = (index, build_time, size_mb)
        index, build_time, size_mb = cache[cache_key]
        index.nprobe = nprobe
        recall, qps = eval_faiss(index, queries, query_idx, ground_truth, TOP_K)
        results.append({
            "nlist": nlist, "m_pq": m_pq, "nprobe": nprobe,
            "build_s": build_time, "recall": recall, "qps": qps, "size_mb": size_mb,
        })
    return results


def run_all_sweeps(
    vectors: np.ndarray,
    queries: np.ndarray,
    query_idx: np.ndarray,
    ground_truth: np.ndarray,
) -> tuple[list[ResultRow], list[ResultRow], list[ResultRow]]:
    """Запускает три свипа подряд и возвращает их результаты."""
    hnsw_results = run_hnsw(vectors, queries, query_idx, ground_truth)
    lsh_results = run_lsh(vectors, queries, query_idx, ground_truth)
    ivfpq_results = run_ivfpq(vectors, queries, query_idx, ground_truth)
    return hnsw_results, lsh_results, ivfpq_results


def save_csv(results: list[ResultRow], path: str) -> None:
    """Записывает список словарей в CSV."""
    if not results:
        return
    with open(path, "w", newline="") as fp:
        writer = csv.DictWriter(fp, fieldnames=list(results[0].keys()))
        writer.writeheader()
        writer.writerows(results)


def save_all_csv(
    hnsw_results: list[ResultRow],
    lsh_results: list[ResultRow],
    ivfpq_results: list[ResultRow],
) -> None:
    """Сохраняет результаты трёх свипов."""
    save_csv(hnsw_results, "results/hnsw.csv")
    save_csv(lsh_results, "results/lsh.csv")
    save_csv(ivfpq_results, "results/ivfpq.csv")


def make_all_plots(
    hnsw_results: list[ResultRow],
    lsh_results: list[ResultRow],
    ivfpq_results: list[ResultRow],
) -> None:
    """Рендер."""
    plot_sweep(LSH_NBITS, lsh_results, "nbits", "LSH: nbits sweep", "graphs/lsh_sweep.png")

    hnsw_by_m = [row for row in hnsw_results if row["ef_c"] == 40]
    plot_sweep(
        [row["m"] for row in hnsw_by_m], hnsw_by_m, "M",
        "HNSW: M sweep (ef_c=40)", "graphs/hnsw_m_sweep.png",
    )

    hnsw_by_ef = [row for row in hnsw_results if row["m"] == 8]
    plot_sweep(
        [row["ef_c"] for row in hnsw_by_ef], hnsw_by_ef, "ef_construction",
        "HNSW: ef_c sweep (M=8)", "graphs/hnsw_ef_sweep.png",
    )

    ivfpq_by_nprobe = [row for row in ivfpq_results if row["nlist"] == 256 and row["m_pq"] == 30]
    plot_sweep(
        [row["nprobe"] for row in ivfpq_by_nprobe], ivfpq_by_nprobe, "nprobe",
        "IVF+PQ: nprobe sweep (nlist=256, m_pq=30)", "graphs/ivfpq_nprobe_sweep.png",
    )

    ivfpq_by_nlist = [row for row in ivfpq_results if row["nprobe"] == 16 and row["m_pq"] == 30]
    plot_sweep(
        [row["nlist"] for row in ivfpq_by_nlist], ivfpq_by_nlist, "nlist",
        "IVF+PQ: nlist sweep (nprobe=16, m_pq=30)", "graphs/ivfpq_nlist_sweep.png",
    )

    series = [
        (hnsw_results, "HNSW", "steelblue", "o"),
        (lsh_results, "LSH", "darkorange", "s"),
        (ivfpq_results, "IVF+PQ", "forestgreen", "^"),
    ]
    plot_comparison(series, "graphs/comparison.png")


def main() -> None:
    os.makedirs("graphs", exist_ok=True)
    os.makedirs("results", exist_ok=True)
    vectors, query_idx, ground_truth = prepare()
    queries = vectors[query_idx]

    hnsw_results, lsh_results, ivfpq_results = run_all_sweeps(vectors, queries, query_idx, ground_truth)
    save_all_csv(hnsw_results, lsh_results, ivfpq_results)
    make_all_plots(hnsw_results, lsh_results, ivfpq_results)


if __name__ == "__main__":
    main()
