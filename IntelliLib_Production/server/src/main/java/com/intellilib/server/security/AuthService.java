package com.intellilib.server.security;

import com.intellilib.server.config.AppConfig.AppSettings;
import com.intellilib.server.model.ApiModels.LoginResponse;
import com.intellilib.server.model.ApiModels.SessionUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder;
    private final AppSettings settings;

    public AuthService(JdbcTemplate jdbc, BCryptPasswordEncoder encoder, AppSettings settings) {
        this.jdbc = jdbc; this.encoder = encoder; this.settings = settings;
    }

    @Transactional
    public LoginResponse loginCard(String cardToken, String workstationId) {
        String code = CryptoUtil.normalizeScannedCode(cardToken);
        if (code.isBlank()) throw new IllegalArgumentException("No ID code was detected. Please scan the college ID again.");
        String hash = CryptoUtil.hmacSha256(settings.cardPepper(), code);
        List<SessionUser> users = jdbc.query("SELECT * FROM users WHERE card_hash=? AND active=TRUE AND role='STUDENT'", this::mapUser, hash);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("This college ID is not enrolled in IntelliLib yet, or the student account is disabled. Ask the librarian to register the card first.");
        }
        return createSession(users.getFirst(), workstationId);
    }

    @Transactional
    public LoginResponse loginAdmin(String email, String password, String workstationId) {
        List<AdminRow> rows = jdbc.query("SELECT * FROM users WHERE lower(email)=lower(?) AND active=TRUE AND role='ADMIN'",
                (rs, n) -> new AdminRow(mapUser(rs, n), rs.getString("password_hash")), email);
        if (rows.isEmpty() || rows.getFirst().hash == null || !encoder.matches(password, rows.getFirst().hash))
            throw new IllegalArgumentException("Invalid administrator credentials");
        return createSession(rows.getFirst().user, workstationId);
    }

    public SessionUser require(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new SecurityException("Sign in required");
        String hash = CryptoUtil.sha256(rawToken);
        List<SessionUser> users = jdbc.query("""
                SELECT u.* FROM sessions s JOIN users u ON u.id=s.user_id
                WHERE s.token_hash=? AND s.expires_at>NOW() AND u.active=TRUE
                """, this::mapUser, hash);
        if (users.isEmpty()) throw new SecurityException("Session expired");
        return users.getFirst();
    }

    public SessionUser requireAdmin(String token) {
        SessionUser user = require(token);
        if (!"ADMIN".equals(user.role())) throw new SecurityException("Administrator access required");
        return user;
    }

    public SessionUser requireStudent(String token) {
        SessionUser user = require(token);
        if (!"STUDENT".equals(user.role())) throw new SecurityException("Student access required");
        return user;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) jdbc.update("DELETE FROM sessions WHERE token_hash=?", CryptoUtil.sha256(rawToken));
    }

    private LoginResponse createSession(SessionUser user, String workstationId) {
        String token = CryptoUtil.randomToken(32);
        OffsetDateTime expires = OffsetDateTime.now().plusHours("ADMIN".equals(user.role()) ? 8 : 2);
        jdbc.update("INSERT INTO sessions(token_hash,user_id,workstation_id,expires_at) VALUES(?,?,?,?)",
                CryptoUtil.sha256(token), user.id(), workstationId, expires);
        jdbc.update("DELETE FROM sessions WHERE expires_at<NOW()");
        return new LoginResponse(token, expires, user);
    }

    private SessionUser mapUser(ResultSet rs, int row) throws java.sql.SQLException {
        return new SessionUser(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("student_code"),
                rs.getString("department"), rs.getString("study_year"), rs.getString("role"));
    }

    private record AdminRow(SessionUser user, String hash) {}
}
