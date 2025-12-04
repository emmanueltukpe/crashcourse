package com.learn.sessionservice.controller;

import com.learn.sessionservice.entity.Session;
import com.learn.sessionservice.service.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

  private final SessionService sessionService;

  public SessionController(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @PostMapping
  public ResponseEntity<Session> createSession(@RequestBody Map<String, String> payload) {
    String userId = payload.get("userId");
    String data = payload.get("data");
    return ResponseEntity.ok(sessionService.createSession(userId, data));
  }

  @GetMapping("/{sessionId}")
  public ResponseEntity<Session> getSession(@PathVariable String sessionId) {
    Session session = sessionService.getSession(sessionId);
    if (session == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(session);
  }

  @DeleteMapping("/{sessionId}")
  public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
    sessionService.deleteSession(sessionId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("Session Service is healthy");
  }
}
