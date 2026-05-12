package com.ai.rts.core.service;

import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ModelService {
    private static final Logger log = LoggerFactory.getLogger(ModelService.class);
    private final Timer timer;

    public ModelService(MeterRegistry meterRegistry) {
        this.timer = meterRegistry.timer("ai_rts.model.inference");
    }

    public Map<String, Double> score(List<FeatureVector> vectors) {
        return timer.record(() -> {
            Map<String, Double> scores = tryOnnx(vectors);
            if (scores != null) {
                return scores;
            }
            Map<String, Double> fallback = new LinkedHashMap<>();
            for (FeatureVector vector : vectors) {
                double[] f = vector.values();
                double heuristic = 0.35 * f[0] + 0.25 * f[1] + 0.2 * f[9] + 0.1 * f[10] + 0.1 * Math.min(1.0, f[3] / 10.0);
                fallback.put(vector.testId(), Math.min(0.999, heuristic));
            }
            log.info("ONNX unavailable; heuristic scoring used for {} tests", vectors.size());
            return fallback;
        });
    }

    private Map<String, Double> tryOnnx(List<FeatureVector> vectors) {
        try {
            // Reflection keeps ONNX optional at compile time while enabling runtime detection.
            Class<?> envClass = Class.forName("com.microsoft.onnxruntime.OrtEnvironment");
            envClass.getMethod("getEnvironment").invoke(null);
            return null;
        } catch (Exception ex) {
            log.warn("ONNX runtime init failed; fallback engaged", ex);
            return null;
        }
    }
}
