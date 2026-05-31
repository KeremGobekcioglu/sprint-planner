package com.sprintplanner.web.dto;

import java.util.List;

public record UpdateDeveloperRequest(String name, String role, List<String> capabilities) {}
