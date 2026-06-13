package com.ai.rts.core.service;

import com.ai.rts.core.ml.FeatureSpec;
import com.ai.rts.core.ml.OnnxModelRunner;
import com.ai.rts.core.model.RecommendationModels.FeatureVector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class ModelService {
    private static final Logger log = LoggerFactory.getLogger(ModelService.class);
    private final Timer timer;
    private final ResourceLoader resourceLoader;
    private final String modelPath;
    private final boolean modelEnabled;
    private OnnxModelRunner onnxRunner;

    public ModelService(
            MeterRegistry meterRegistry,
            ResourceLoader resourceLoader,
            @Value("${ai.rts.model.path:classpath:models/rts-v1.onnx}") String modelPath,
            @Value("${ai.rts.model.enabled:true}") boolean modelEnabled) {
        this.timer = meterRegistry.timer("ai_rts.model.inference");
        this.resourceLoader = resourceLoader;
        this.modelPath = modelPath;
        this.modelEnabled = modelEnabled;
    }

    @PostConstruct
    void loadModel() {
        if (!modelEnabled) {
            log.info("ONNX model disabled (ai.rts.model.enabled=false); heuristic scoring will be used");
            return;
        }
        try {
            Resource resource = resourceLoader.getResource(modelPath);
            if (!resource.exists()) {
                log.warn("ONNX model not found at {}; heuristic fallback engaged", modelPath);
                return;
            }
            onnxRunner = new OnnxModelRunner(resource);
            log.info("ONNX model ready at {} (featureSpec={})", modelPath, FeatureSpec.VERSION);
        } catch (Exception ex) {
            log.warn("Failed to load ONNX model from {}; heuristic fallback engaged", modelPath, ex);
            onnxRunner = null;
        }
    }

    @PreDestroy
    void closeModel() throws Exception {
        if (onnxRunner != null) {
            onnxRunner.close();
        }
    }

    public Map<String, Double> score(List<FeatureVector> vectors) {
        return timer.record(() -> {
            if (onnxRunner != null) {
                try {
                    return onnxRunner.score(vectors);
                } catch (Exception ex) {
                    log.warn("ONNX inference failed; heuristic fallback engaged", ex);
                }
            }
            return heuristicScore(vectors);
        });
    }

    boolean isOnnxActive() {
        return onnxRunner != null;
    }

    private Map<String, Double> heuristicScore(List<FeatureVector> vectors) {
        Map<String, Double> fallback = new LinkedHashMap<>();
        for (FeatureVector vector : vectors) {
            double[] f = vector.values();
            double heuristic = 0.30 * f[FeatureSpec.RECENCY_FAIL_RATE]
                    + 0.20 * f[FeatureSpec.FLAKINESS]
                    + 0.15 * f[FeatureSpec.PACKAGE_OVERLAP]
                    + 0.15 * f[FeatureSpec.TAG_CRITICAL]
                    + 0.10 * f[FeatureSpec.INTEGRATION_WEIGHT]
                    + 0.10 * Math.min(1.0, f[FeatureSpec.HISTORY_COUNT] / 10.0);
            fallback.put(vector.testId(), Math.min(0.999, heuristic));
        }
        log.info("Heuristic scoring used for {} tests (spec={})", vectors.size(), FeatureSpec.VERSION);
        return fallback;
    }
}
