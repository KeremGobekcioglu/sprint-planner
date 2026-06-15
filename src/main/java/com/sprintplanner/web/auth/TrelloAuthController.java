package com.sprintplanner.web.auth;

import com.sprintplanner.domain.model.AppUser;
import com.sprintplanner.infrastructure.persistence.JpaUserRepository;
import com.sprintplanner.web.SecurityUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/trello")
public class TrelloAuthController {

    private final JpaUserRepository userRepository;

    public TrelloAuthController(JpaUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/token")
    public ResponseEntity<Void> saveToken(@RequestBody TrelloTokenRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token must not be blank");
        }
        AppUser user = loadCurrentUser();
        user.setTrelloToken(request.token());
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        AppUser user = loadCurrentUser();
        boolean connected = user.getTrelloToken() != null && !user.getTrelloToken().isBlank();
        Map<String, Object> body = new HashMap<>();
        body.put("connected", connected);
        body.put("username", null);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/token")
    public ResponseEntity<Void> deleteToken() {
        AppUser user = loadCurrentUser();
        user.setTrelloToken(null);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    private AppUser loadCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
