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
    public static final String MODEL_BREAKDOWN       = "gemini-3-flash-preview";
    public static final String MODEL_BREAKDOWN_RETRY = "gemini-2.5-flash-lite";
    public static final String MODEL_ESTIMATION      = "gemini-2.5-flash-lite";
    public static final String MODEL_DISTRIBUTION    = "gemini-2.5-flash";

    private static final Map<String, List<String>> FALLBACK_CHAINS = Map.of(
            MODEL_BREAKDOWN,    List.of("gemini-3-flash-preview", "gemini-2.5-flash"),
            MODEL_ESTIMATION,   List.of("gemini-2.5-flash-lite",  "gemini-2.5-flash"),
            MODEL_DISTRIBUTION, List.of("gemini-2.5-flash",       "gemini-2.5-flash-lite")
    );

    private final Client client;

    public GeminiClientProvider(GeminiConfig geminiConfig) {
        this.client = Client.builder().apiKey(geminiConfig.getApiKey()).build();
    }

    public String generate(String prompt, float temperature, String preferredModel) {
        List<String> chain = FALLBACK_CHAINS.getOrDefault(preferredModel, List.of(preferredModel));
        Exception lastException = null;

        for (String model : chain) {
            long start = System.currentTimeMillis();
            log.info("Gemini [{}] calling — prompt={}ch, temperature={}", model, prompt.length(), temperature);
            try {
                GenerateContentConfig config = GenerateContentConfig.builder()
                        .temperature(temperature)
                        .build();
                String result = client.models.generateContent(model, prompt, config).text();
                long elapsed = System.currentTimeMillis() - start;
                log.info("Gemini [{}] OK — {}ms | response={}ch", model, elapsed, result == null ? 0 : result.length());
                log.debug("Gemini [{}] full response:\n{}", model, result);
                return result;

            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                lastException = e;
                if (!isRetryable(e)) {
                    log.error("Gemini [{}] non-retryable error after {}ms: {}", model, elapsed, e.getMessage());
                    throw new RuntimeException("Non-retryable Gemini error on model " + model, e);
                }
                log.warn("Gemini [{}] retryable error after {}ms ({}), trying next model...", model, elapsed, e.getMessage());
                sleep(2000);
            }
        }

        throw new RuntimeException("All Gemini models exhausted. Last error: "
                + lastException.getMessage(), lastException);
    }

    private boolean isRetryable(Exception e) {
        if (e == null) return false;
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("429")
                || msg.contains("503")
                || msg.contains("overloaded")
                || msg.contains("high demand")
                || msg.contains("unavailable")
                || msg.contains("quota")
                || msg.contains("rate limit")
                || msg.contains("too many requests")
                || msg.contains("service unavailable")) {
            return true;
        }
        Throwable cause = e.getCause();
        if (cause instanceof Exception causeEx && cause != e) {
            return isRetryable(causeEx);
        }
        return false;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}