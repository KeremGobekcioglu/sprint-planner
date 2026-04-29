package com.sprintplanner.infrastructure.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.sprintplanner.infrastructure.config.GeminiConfig;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiClientProvider {

    private static final List<String> MODEL_FALLBACK_CHAIN = List.of(
            "gemini-2.0-flash",
            "gemini-2.5-flash",
            "gemini-2.0-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-flash-latest"
    );

    private final Client client;

    public GeminiClientProvider(GeminiConfig geminiConfig) {
        this.client = Client.builder().apiKey(geminiConfig.getApiKey()).build();
    }

    public String generate(String prompt, float temperature) {
        Exception lastException = null;

        for (String model : MODEL_FALLBACK_CHAIN) {
            try {
                GenerateContentConfig config = GenerateContentConfig.builder()
                        .temperature(temperature)
                        .build();

                return client.models
                        .generateContent(model, prompt, config)
                        .text();

            } catch (Exception e) {
                lastException = e;
                // Only retry on 503 overload — fail fast on auth errors, bad requests etc.
                if (!isRetryable(e)) {
                    throw new RuntimeException("Non-retryable Gemini error on model " + model, e);
                }
                // brief pause before next model
                sleep(1000);
            }
        }

        throw new RuntimeException("All Gemini models exhausted. Last error: " + lastException.getMessage(), lastException);
    }

    private boolean isRetryable(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("503")
                || msg.contains("overloaded")
                || msg.contains("high demand")
                || msg.contains("temporarily unavailable")
                || msg.contains("UNAVAILABLE");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}