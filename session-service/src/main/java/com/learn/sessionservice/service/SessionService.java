package com.learn.sessionservice.service;

import com.learn.sessionservice.entity.Session;
import com.learn.sessionservice.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class SessionService {

  private final SessionRepository sessionRepository;
  private static final long SESSION_TTL_SECONDS = 3600; // 1 hour

  public SessionService(SessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  public Session createSession(String userId, String data) {
    String sessionId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    Session session = new Session(
        sessionId,
        userId,
        data,
        now,
        now,
        now.getEpochSecond() + SESSION_TTL_SECONDS);

    sessionRepository.save(session);
    return session;
  }

  public Session getSession(String sessionId) {
    Session session = sessionRepository.findById(sessionId);
    if (session != null) {
      // Update last accessed
      session.setLastAccessedAt(Instant.now());
      // Extend TTL
      session.setTtl(Instant.now().getEpochSecond() + SESSION_TTL_SECONDS);
      sessionRepository.save(session);
    }
    return session;
  }

  public void deleteSession(String sessionId) {
    sessionRepository.delete(sessionId);
  }
}
