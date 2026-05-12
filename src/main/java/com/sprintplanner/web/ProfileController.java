package com.sprintplanner.web;

import com.sprintplanner.application.ProfileService;
import com.sprintplanner.web.dto.ProfileResponse;
import com.sprintplanner.web.dto.ProfileStatsResponse;
import com.sprintplanner.web.dto.UpdateProfileRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ProfileResponse profile = profileService.getProfile(currentUserId);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/stats")
    public ResponseEntity<ProfileStatsResponse> getStats() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ProfileStatsResponse stats = profileService.getStats(currentUserId);
        return ResponseEntity.ok(stats);
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> updateProfile(@RequestBody UpdateProfileRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        profileService.updateName(currentUserId, request.name());
        ProfileResponse profile = profileService.getProfile(currentUserId);
        return ResponseEntity.ok(profile);
    }
}


