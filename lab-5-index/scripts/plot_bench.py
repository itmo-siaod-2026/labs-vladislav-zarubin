import csv
import os
import re
import sys
import tempfile

os.environ.setdefault("MPLCONFIGDIR", os.path.join(tempfile.gettempdir(), "inverted-index-matplotlib"))
os.environ.setdefault("XDG_CACHE_HOME", os.path.join(tempfile.gettempdir(), "inverted-index-cache"))
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt


def num(s):
    return float(s.replace(",", ".").strip()) if s not in (None, "") else float("nan")


def base_op(benchmark):
    short = benchmark.split(".")[-1]
    return re.sub(r"(Match|Batch|Throughput)$", "", short)


def load(path):
    with open(path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    param_key = next((k for k in rows[0] if k.strip().lower().startswith("param")), None)
    ops = {}
    for r in rows:
        op = base_op(r["Benchmark"])
        kind = "thrpt" if r["Mode"].strip().lower() == "thrpt" else "time"
        param = num(r[param_key]) if param_key and r[param_key] else None
        score = num(r[next(k for k in r if k.strip() == "Score")])
        err = num(r[next(k for k in r if k.strip().lower().startswith("score error"))])
        unit = (r["Unit"] or "").strip()
        ops.setdefault(op, {"time": {}, "thrpt": {}})
        ops[op][kind][param] = (score, err, unit)
    return ops, (param_key is not None)


def category(op):
    if op.lower().startswith("diskranked"):
        return "disk"
    if op.lower().startswith("ranked"):
        return "basic"
    return "other"


CAT_TITLE = {
    "basic": "In-memory + BM25 (high-low)",
    "disk": "Disk + BM25 (high-low, mmap)",
}

OUTPUT = {
    ("basic", "time"): "query_basic_time.png",
    ("basic", "thrpt"): "query_basic_thrpt.png",
    ("disk", "time"): "query_disk_time.png",
    ("disk", "thrpt"): "query_disk_thrpt.png",
}

LABELS = {
    "rankedTerm": "term", "rankedAnd": "AND", "rankedOr": "OR", "rankedNot": "NOT",
    "rankedAdj": "ADJ", "rankedNear": "NEAR",
    "diskRankedTerm": "term", "diskRankedAnd": "AND", "diskRankedOr": "OR",
    "diskRankedNot": "NOT", "diskRankedAdj": "ADJ", "diskRankedNear": "NEAR",
}

PLOT_ORDER = {
    "basic": ("rankedTerm", "rankedAnd", "rankedOr", "rankedNot", "rankedAdj", "rankedNear"),
    "disk": ("diskRankedTerm", "diskRankedAnd", "diskRankedOr", "diskRankedNot",
             "diskRankedAdj", "diskRankedNear"),
}


def ylabel(unit):
    if unit == "us/op":
        return "время, мкс"
    if unit == "ops/s":
        return "операций в секунду"
    return unit


def plot_lines(ops, outdir):
    made = []
    cats = {}
    for op, d in ops.items():
        cat = category(op)
        if op in PLOT_ORDER.get(cat, ()):
            cats.setdefault(cat, {})[op] = d
    for cat, cops in cats.items():
        for kind in ("time", "thrpt"):
            by_unit = {}
            for op in PLOT_ORDER[cat]:
                if op not in cops:
                    continue
                pts = sorted((p, s, e, u) for p, (s, e, u) in cops[op][kind].items() if p is not None)
                if pts:
                    by_unit.setdefault(pts[0][3], {})[op] = pts
            for unit, uops in by_unit.items():
                fig, ax = plt.subplots(figsize=(8, 5))
                for op in PLOT_ORDER[cat]:
                    if op not in uops:
                        continue
                    pts = uops[op]
                    ax.errorbar([p for p, _, _, _ in pts], [s for _, s, _, _ in pts],
                                yerr=[e for _, _, e, _ in pts], marker="o", capsize=3, linewidth=2,
                                label=LABELS.get(op, op))
                ax.set_xlabel("число документов")
                ax.set_ylabel(ylabel(unit))
                ax.set_title(CAT_TITLE.get(cat, cat))
                ax.ticklabel_format(axis="x", style="plain")
                ax.grid(True, alpha=0.25)
                ax.legend(loc="center left", bbox_to_anchor=(1.02, 0.5), borderaxespad=0)
                name = OUTPUT.get((cat, kind))
                if not name:
                    continue
                fig.tight_layout()
                path = os.path.join(outdir, name)
                fig.savefig(path, dpi=120)
                plt.close(fig)
                made.append(path)
    return made


def main():
    raw_args = sys.argv[1:]
    outdir = "plots"
    if "-o" in raw_args:
        i = raw_args.index("-o")
        outdir = raw_args[i + 1]
        raw_args = raw_args[:i] + raw_args[i + 2:]
    args = [a for a in raw_args if not a.startswith("-")]
    os.makedirs(outdir, exist_ok=True)

    all_ops = {}
    for path in args:
        ops, swept = load(path)
        if not swept:
            print(f"{path}: пропускаю")
            continue
        for op, d in ops.items():
            if op not in all_ops:
                all_ops[op] = {"time": {}, "thrpt": {}}
            all_ops[op]["time"].update(d["time"])
            all_ops[op]["thrpt"].update(d["thrpt"])

    made = plot_lines(all_ops, outdir)
    print(f"{len(made)} png")
    for p in made:
        print("  " + p)


if __name__ == "__main__":
    main()
