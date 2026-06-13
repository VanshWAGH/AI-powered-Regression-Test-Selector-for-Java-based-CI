#!/usr/bin/env python3
"""
Train a lightweight logistic-regression TSP model and export to ONNX for Java inference.

Usage (from repo root):
  pip install -r ai-rts/model/requirements.txt
  python ai-rts/model/train_and_export.py

Outputs:
  ai-rts/api/src/main/resources/models/rts-v1.onnx
  ai-rts/core/src/test/resources/models/rts-v1.onnx
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from sklearn.linear_model import LogisticRegression
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

FEATURE_COUNT = 12
FEATURE_SPEC_VERSION = "v1"
SEED = 42


def synthesize_training_data(n_samples: int = 4000) -> tuple[np.ndarray, np.ndarray]:
    rng = np.random.default_rng(SEED)
    x = rng.random((n_samples, FEATURE_COUNT), dtype=np.float32)

    # Align labels with Java heuristic weights so the bundled model is sensible before real CI data exists.
    score = (
        0.30 * x[:, 0]
        + 0.20 * x[:, 1]
        + 0.15 * x[:, 11]
        + 0.15 * x[:, 9]
        + 0.10 * x[:, 10]
        + 0.10 * np.minimum(1.0, x[:, 3] / 10.0)
    )
    noise = rng.normal(0, 0.08, size=n_samples)
    y = (score + noise > 0.42).astype(np.int64)
    return x, y


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    api_model_dir = root / "api" / "src" / "main" / "resources" / "models"
    test_model_dir = root / "core" / "src" / "test" / "resources" / "models"
    api_model_dir.mkdir(parents=True, exist_ok=True)
    test_model_dir.mkdir(parents=True, exist_ok=True)

    x, y = synthesize_training_data()
    clf = LogisticRegression(max_iter=500, class_weight="balanced", random_state=SEED)
    clf.fit(x, y)

    initial_type = [("input", FloatTensorType([None, FEATURE_COUNT]))]
    onnx_model = convert_sklearn(
        clf,
        initial_types=initial_type,
        target_opset=12,
        options={id(clf): {"zipmap": False}},
    )

    out_api = api_model_dir / "rts-v1.onnx"
    out_test = test_model_dir / "rts-v1.onnx"
    out_api.write_bytes(onnx_model.SerializeToString())
    out_test.write_bytes(onnx_model.SerializeToString())

    meta = {
        "featureSpecVersion": FEATURE_SPEC_VERSION,
        "featureCount": FEATURE_COUNT,
        "trainingSamples": int(x.shape[0]),
        "positiveRate": float(y.mean()),
    }
    (api_model_dir / "rts-v1.meta.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")

    print("Wrote ONNX model (" + str(out_api.stat().st_size) + " bytes)")
    print(json.dumps(meta, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
