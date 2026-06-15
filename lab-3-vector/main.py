import os
import time
import tempfile
import numpy as np
import faiss
import hnswlib
from gensim.models import KeyedVectors

NUM_QUERIES = 10_000
TOP_K = 100
SEED = 88

def load_corpus() -> np.ndarray:
    """Загружает корпус с диска, оставляя только существительные."""
    model = KeyedVectors.load_word2vec_format("data/word2vec-ruscorpora-300.gz", binary=True)
    noun_mask = np.array([str(word).endswith("_NOUN") for word in model.index_to_key])
    return model.vectors.astype(np.float32)[noun_mask]


def exact_knn(corpus: np.ndarray, query_idx: np.ndarray, k: int) -> np.ndarray:
    """Точные top-k соседи по евклидову расстоянию."""
    queries = corpus[query_idx]
    queries_norms = (queries * queries).sum(1, keepdims=True)
    corpus_norms = (corpus * corpus).sum(1)
    dists = queries_norms + corpus_norms - 2.0 * (queries @ corpus.T)
    dists[np.arange(len(query_idx)), query_idx] = np.inf
    return np.argsort(dists, axis=1)[:, :k]


def prepare() -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Возвращает vectors, query_indices, ground_truth"""
    random_generator = np.random.default_rng(SEED)
    vectors = load_corpus()
    query_idx = np.sort(random_generator.choice(len(vectors), NUM_QUERIES, replace=False))
    ground_truth = exact_knn(vectors, query_idx, TOP_K)
    return vectors, query_idx, ground_truth



def build_hnsw(vectors: np.ndarray, m: int, ef_construction: int, ef_query: int) -> hnswlib.Index:
    """Строит HNSW-индекс."""
    index = hnswlib.Index(space="l2", dim=vectors.shape[1])
    index.init_index(max_elements=len(vectors), ef_construction=ef_construction, M=m)
    index.add_items(vectors)
    index.set_ef(ef_query)
    return index


def build_lsh(vectors: np.ndarray, nbits: int) -> faiss.IndexLSH:
    """Строит LSH-индекс."""
    index = faiss.IndexLSH(vectors.shape[1], nbits)
    index.add(vectors)
    return index


def build_ivfpq(vectors: np.ndarray, nlist: int, m_pq: int, nprobe: int) -> faiss.IndexIVFPQ:
    """Строит IVF+PQ-индекс."""
    quantizer = faiss.IndexFlatL2(vectors.shape[1])
    index = faiss.IndexIVFPQ(quantizer, vectors.shape[1], nlist, m_pq)
    index.train(vectors)
    index.add(vectors)
    index.nprobe = nprobe
    return index


def recall_at_k(approx: list[int], ground_truth: np.ndarray, k: int) -> float:
    """Recall@k: доля общих элементов между приближённым и точным top-k."""
    return len(set(approx[:k]) & set(ground_truth[:k].tolist())) / k


def index_size_mb(index: faiss.Index | hnswlib.Index) -> float:
    """Размер индекса в MB через сериализацию."""
    if isinstance(index, faiss.Index):
        return faiss.serialize_index(index).nbytes / 1024 / 1024
    with tempfile.TemporaryDirectory() as tmp_dir:
        path = f"{tmp_dir}/idx.bin"
        index.save_index(path)
        return os.path.getsize(path) / 1024 / 1024


def eval_hnsw(
    index: hnswlib.Index,
    queries: np.ndarray,
    query_idx: np.ndarray,
    ground_truth: np.ndarray,
    k: int,
) -> tuple[float, float]:
    """Прогоняет все запросы через HNSW."""
    recalls: list[float] = []
    start_time = time.perf_counter()
    for i, query in enumerate(queries):
        labels, _ = index.knn_query(query.reshape(1, -1), k=k + 1)
        neighbours = [x for x in labels[0].tolist() if x != query_idx[i]][:k]
        recalls.append(recall_at_k(neighbours, ground_truth[i], k))
    qps = len(queries) / (time.perf_counter() - start_time)
    return float(np.mean(recalls)), qps


def eval_faiss(
    index: faiss.Index,
    queries: np.ndarray,
    query_idx: np.ndarray,
    ground_truth: np.ndarray,
    k: int,
) -> tuple[float, float]:
    """Прогоняет все запросы через FAISS-индекс."""
    recalls: list[float] = []
    start_time = time.perf_counter()
    for i, query in enumerate(queries):
        _, found = index.search(query.reshape(1, -1), k + 1)
        neighbours = [int(x) for x in found[0] if x != query_idx[i]][:k]
        recalls.append(recall_at_k(neighbours, ground_truth[i], k))
    qps = len(queries) / (time.perf_counter() - start_time)
    return float(np.mean(recalls)), qps
