from typing import Any, Sequence
import matplotlib
import matplotlib.pyplot as plt

ResultRow = dict[str, Any]
matplotlib.use("Agg")
METRIC_KEYS = ("recall", "qps", "build_s", "size_mb")
METRIC_LABELS = ("Recall@100", "Query QPS", "Build (s)", "Size (MB)")


def plot_sweep(
    xs: Sequence[int],
    results: Sequence[ResultRow],
    x_label: str,
    title: str,
    path: str,
) -> None:
    fig, axes = plt.subplots(2, 2, figsize=(10, 7))
    fig.suptitle(title)
    for ax, key, ylabel in zip(axes.flat, METRIC_KEYS, METRIC_LABELS):
        ax.plot(xs, [row[key] for row in results], "o-")
        ax.set_xlabel(x_label)
        ax.set_ylabel(ylabel)
        ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(path, dpi=130, bbox_inches="tight")
    plt.close(fig)


def plot_comparison(
    series: list[tuple[list[ResultRow], str, str, str]],
    path: str,
) -> None:
    panels = (
        ("qps", "Query QPS", True),
        ("size_mb", "Index size (MB)", False),
        ("build_s", "Build time (s)", True),
    )
    fig, axes = plt.subplots(1, 3, figsize=(20, 6))
    for ax, (y_key, y_label, log_y) in zip(axes, panels):
        for results, label, color, marker in series:
            ax.scatter(
                [row["recall"] for row in results],
                [row[y_key] for row in results],
                label=label, color=color, marker=marker, s=50, alpha=0.8,
            )
        ax.set_xlabel("Recall@100")
        ax.set_ylabel(y_label)
        if log_y:
            ax.set_yscale("log")
        ax.grid(alpha=0.3)
        ax.legend()
    fig.suptitle("Сравнение алгоритмов")
    fig.tight_layout()
    fig.savefig(path, dpi=130, bbox_inches="tight")
    plt.close(fig)
