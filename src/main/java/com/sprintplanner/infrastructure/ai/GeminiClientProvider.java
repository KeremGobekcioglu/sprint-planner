package com.sprintplanner.infrastructure.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.sprintplanner.infrastructure.config.GeminiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GeminiClientProvider {
    private static final Logger log = LoggerFactory.getLogger(GeminiClientProvider.class);

    // 1. Keep your model identifiers
    public static final String MODEL_BREAKDOWN    = "gemini-2-flash";
    public static final String MODEL_ESTIMATION   = "gemini-2.5-flash-lite";
    public static final String MODEL_DISTRIBUTION = "gemini-2.5-flash-lite";

    // 2. Change the Map keys to "Category Names" instead of the model values
    private static final Map<String, List<String>> FALLBACK_CHAINS = Map.of(
            "BREAKDOWN",    List.of("gemini-3-flash", "gemini-2.5-flash"),
            "ESTIMATION",   List.of("gemini-2.5-flash-lite", "gemini-3-flash"),
            "DISTRIBUTION", List.of("gemini-2.5-flash-lite", "gemini-2.0-flash-lite")
    );

    private final Client client;

    public GeminiClientProvider(GeminiConfig geminiConfig) {
        this.client = Client.builder().apiKey(geminiConfig.getApiKey()).build();
    }

    public String generate(String prompt, float temperature, String preferredModel) {
        List<String> chain = FALLBACK_CHAINS.getOrDefault(preferredModel, List.of(preferredModel));
        Exception lastException = null;

        for (String model : chain) {
            try {
                GenerateContentConfig config = GenerateContentConfig.builder()
                        .temperature(temperature)
                        .build();
                String result = client.models.generateContent(model, prompt, config).text();
                log.debug("=== GEMINI [{}] ===\nPROMPT:\n{}\nRESPONSE:\n{}", model, prompt, result);
                return result;

            } catch (Exception e) {
                lastException = e;
                if (!isRetryable(e)) {
                    throw new RuntimeException("Non-retryable Gemini error on model " + model, e);
                }
                sleep(2000);
            }
        }

        throw new RuntimeException("All Gemini models exhausted. Last error: "
                + lastException.getMessage(), lastException);
    }

    private boolean isRetryable(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("503")
                || msg.contains("429")
                || msg.contains("overloaded")
                || msg.contains("high demand")
                || msg.contains("UNAVAILABLE")
                || msg.contains("quota");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}