package com.sprintplanner.web;

import com.sprintplanner.domain.port.out.AdminResetPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminResetPort adminResetPort;

    public AdminController(AdminResetPort adminResetPort) {
        this.adminResetPort = adminResetPort;
    }

    @DeleteMapping("/reset")
    public ResponseEntity<String> resetAllSprintData() {
        if (!SecurityUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
        }
        adminResetPort.resetAllSprintData();
        return ResponseEntity.ok("Reset complete. All sprint data deleted.");
    }
}
