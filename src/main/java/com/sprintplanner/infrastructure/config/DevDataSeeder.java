package com.sprintplanner.infrastructure.config;

import com.sprintplanner.application.TeamService;
import com.sprintplanner.domain.model.AppUser;
import com.sprintplanner.domain.port.out.DeveloperRepository;
import com.sprintplanner.domain.service.CapabilityInferenceService;
import com.sprintplanner.infrastructure.persistence.JpaUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds development data on startup when the "dev" profile is active.
 * Creates a dev admin user and three sample teams with developers.
 * Skips entirely if data for dev@sprint.local already exists.
 *
 * Sample epics for reference (paste into Planning page during testing):
 *
 * Team Orbit (FINTECH):
 *   Add IBAN validation | Validate format and checksum on transfer submission
 *   Reconciliation report | Daily EOD report for all settled transactions
 *
 * Team Atlas (E_COMMERCE):
 *   Checkout flow redesign | Simplify 3-step checkout to single page
 *   Product search | Add filters, sorting, and instant search
 *
 * Team Pulse (GENERIC/Mobile):
 *   Offline mode | Cache sprint data for offline review
 *   Push notifications | Notify devs of new task assignments
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final String SEED_EMAIL = "dev@sprint.local";
    private static final String SEED_PASSWORD = "dev123";

    private final JpaUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeamService teamService;
    private final DeveloperRepository developerRepository;

    public DevDataSeeder(
        JpaUserRepository userRepository,
        PasswordEncoder passwordEncoder,
        TeamService teamService,
        DeveloperRepository developerRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.teamService = teamService;
        this.developerRepository = developerRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail(SEED_EMAIL).isPresent()) {
            backfillMissingCapabilities();
            return;
        }

        UUID seedUserId = UUID.randomUUID();
        String role = userRepository.count() == 0 ? "ADMIN" : "USER";
        AppUser seedUser = new AppUser(
            seedUserId,
            SEED_EMAIL,
            "Dev User",
            role,
            passwordEncoder.encode(SEED_PASSWORD),
            Instant.now()
        );
        userRepository.save(seedUser);

        // Team Orbit — Fintech
        var orbit = teamService.createTeam("Team Orbit", "FINTECH", 10, null, seedUserId);
        teamService.addDeveloper(orbit.getId(), seedUserId, true, "Jordan Reed", "Senior Backend", List.of("backend"), 8, "SENIOR");
        teamService.addDeveloper(orbit.getId(), seedUserId, true, "Mina Hassan", "DevOps", List.of("devops"), 6, null);
        teamService.addDeveloper(orbit.getId(), seedUserId, true, "Satoshi Yamamoto", "Full Stack", List.of("backend", "frontend"), 10, "MID");
        teamService.addDeveloper(orbit.getId(), seedUserId, true, "Dana Kim", "QA Engineer", List.of("qa"), 5, null);

        // Team Atlas — E-Commerce
        var atlas = teamService.createTeam("Team Atlas", "E_COMMERCE", 14, null, seedUserId);
        teamService.addDeveloper(atlas.getId(), seedUserId, true, "Ava Chen", "Senior Backend", List.of("backend"), 9, "SENIOR");
        teamService.addDeveloper(atlas.getId(), seedUserId, true, "Marco Silva", "Frontend Engineer", List.of("frontend"), 6, null);
        teamService.addDeveloper(atlas.getId(), seedUserId, true, "Priya Nair", "QA Engineer", List.of("qa"), 4, null);

        // Team Pulse — Mobile/Generic
        var pulse = teamService.createTeam("Team Pulse", "GENERIC", 14, null, seedUserId);
        teamService.addDeveloper(pulse.getId(), seedUserId, true, "Elena Rodriguez", "Android Engineer", List.of("android"), 7, null);
        teamService.addDeveloper(pulse.getId(), seedUserId, true, "Kofi Mensah", "iOS Engineer", List.of("ios"), 6, null);
        teamService.addDeveloper(pulse.getId(), seedUserId, true, "Sarah Jenkins", "QA Engineer", List.of("qa"), 5, null);
        teamService.addDeveloper(pulse.getId(), seedUserId, true, "Alex Volkov", "Backend Engineer", List.of("backend"), 8, null);
    }

    private void backfillMissingCapabilities() {
        teamService.getTeams(null, true).forEach(team ->
            developerRepository.findByTeamId(team.getId()).forEach(dev -> {
                if (dev.getCapabilities().isEmpty()) {
                    List<String> inferred = CapabilityInferenceService.inferFromRole(dev.getRole());
                    if (!inferred.isEmpty()) {
                        dev.setCapabilities(inferred);
                        developerRepository.save(dev);
                    }
                }
            })
        );
    }
}
