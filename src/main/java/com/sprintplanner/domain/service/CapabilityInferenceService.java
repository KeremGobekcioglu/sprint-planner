package com.sprintplanner.domain.service;

import java.util.ArrayList;
import java.util.List;

public class CapabilityInferenceService {

    private CapabilityInferenceService() {}

    public static List<String> inferFromRole(String roleLabel) {
        if (roleLabel == null || roleLabel.isBlank()) {
            return List.of();
        }
        String normalized = roleLabel.toLowerCase();
        List<String> capabilities = new ArrayList<>();
        if (normalized.contains("backend")) capabilities.add("backend");
        if (normalized.contains("frontend") || normalized.contains("front end")) capabilities.add("frontend");
        if (normalized.contains("fullstack") || normalized.contains("full stack")) {
            if (!capabilities.contains("backend")) capabilities.add("backend");
            if (!capabilities.contains("frontend")) capabilities.add("frontend");
        }
        if (normalized.contains("qa") || normalized.contains("test")) capabilities.add("qa");
        if (normalized.contains("devops") || normalized.contains("sre")) capabilities.add("devops");
        if (normalized.contains("android")) capabilities.add("android");
        if (normalized.contains("ios")) capabilities.add("ios");
        if (normalized.contains("mobile")) {
            if (!capabilities.contains("android")) capabilities.add("android");
            if (!capabilities.contains("ios")) capabilities.add("ios");
        }
        return capabilities;
    }
}
