package com.intellilib.server.service;

import com.intellilib.server.config.AppConfig.AppSettings;
import com.intellilib.server.model.ApiModels.*;
import com.intellilib.server.security.CryptoUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final AppSettings settings;
    private final BCryptPasswordEncoder encoder;
    private final AuditService audit;

    public AdminService(JdbcTemplate jdbc, AppSettings settings, BCryptPasswordEncoder encoder, AuditService audit) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional
    public CreatedUser createStudent(NewUserRequest r, long adminId, String workstation) {
        require(r.studentCode(), "Student ID");
        require(r.name(), "Name");
        String studentCode = CryptoUtil.normalizeScannedCode(r.studentCode());
        String cardCode = CryptoUtil.normalizeScannedCode(r.cardCode());
        // KLH student cards encode the student ID in the QR. When an imported/manual record
        // does not provide a separate card code, use the real student ID as the card value.
        if (cardCode.isBlank()) cardCode = studentCode;
        String cardHash = CryptoUtil.hmacSha256(settings.cardPepper(), cardCode);
        if(!jdbc.queryForList("SELECT id FROM users WHERE student_code=?",Long.class,studentCode).isEmpty()) throw new IllegalArgumentException("Student ID already exists");
        if(!jdbc.queryForList("SELECT id FROM users WHERE card_hash=?",Long.class,cardHash).isEmpty()) throw new IllegalArgumentException("This ID card is already registered");
        Long id = jdbc.queryForObject("""
                INSERT INTO users(student_code,name,email,department,study_year,role,card_hash,active)
                VALUES(?,?,?,?,?,'STUDENT',?,TRUE) RETURNING id
                """, Long.class,
                studentCode, r.name().trim(), blank(r.email()), blank(r.department()), blank(r.studyYear()), cardHash);
        audit.log(adminId, workstation, "STUDENT_CREATED", "USER", id, studentCode);
        return new CreatedUser(id, studentCode, r.name().trim());
    }

    public List<UserView> users() {
        return jdbc.query("SELECT * FROM users WHERE role='STUDENT' ORDER BY name",
                (r,n) -> new UserView(r.getLong("id"), r.getString("student_code"), r.getString("name"),
                        r.getString("email"), r.getString("department"), r.getString("study_year"),
                        r.getBoolean("active"), r.getObject("created_at", OffsetDateTime.class)));
    }

    @Transactional
    public void setActive(long id, boolean active, long adminId, String workstation) {
        int changed = jdbc.update("UPDATE users SET active=? WHERE id=? AND role='STUDENT'", active, id);
        if (changed == 0) throw new IllegalArgumentException("Student not found");
        audit.log(adminId, workstation, active ? "STUDENT_ACTIVATED" : "STUDENT_DEACTIVATED", "USER", id, null);
    }

    @Transactional
    public ImportResult importStudents(MultipartFile file, long adminId, String workstation) {
        int inserted = 0, updated = 0, skipped = 0;
        List<String> warnings = new ArrayList<>();
        try (var reader = csvReader(file);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .setIgnoreHeaderCase(true)
                     .setIgnoreSurroundingSpaces(true)
                     .build()
                     .parse(reader)) {
            int row = 1;
            for (CSVRecord r : parser) {
                row++;
                String code = CryptoUtil.normalizeScannedCode(value(r,"student_code"));
                String name = value(r,"name");
                String card = CryptoUtil.normalizeScannedCode(value(r,"card_code"));
                if (code.isBlank() || name.isBlank()) {
                    skipped++;
                    warnings.add("Row " + row + ": student_code and name are required");
                    continue;
                }
                // For colleges where the QR payload is the student ID, card_code may be omitted.
                if (card.isBlank()) card = code;
                String email = value(r,"email");
                String department = value(r,"department");
                String year = value(r,"study_year");
                boolean active = !"false".equalsIgnoreCase(value(r,"active"));
                String cardHash = CryptoUtil.hmacSha256(settings.cardPepper(), card);
                Long existing = jdbc.query("SELECT id FROM users WHERE student_code=? AND role='STUDENT'", rs -> rs.next() ? rs.getLong(1) : null, code);
                Long cardOwner = jdbc.query("SELECT id FROM users WHERE card_hash=?", rs -> rs.next() ? rs.getLong(1) : null, cardHash);
                if(cardOwner!=null && (existing==null || !cardOwner.equals(existing))){skipped++;warnings.add("Row "+row+": card_code is already assigned to another student");continue;}
                if (existing == null) {
                    jdbc.update("""
                            INSERT INTO users(student_code,name,email,department,study_year,role,card_hash,active)
                            VALUES(?,?,?,?,?,'STUDENT',?,?)
                            """, code, name, blank(email), blank(department), blank(year), cardHash, active);
                    inserted++;
                } else {
                    jdbc.update("""
                            UPDATE users SET name=?,email=?,department=?,study_year=?,card_hash=?,active=? WHERE id=?
                            """, name, blank(email), blank(department), blank(year), cardHash, active, existing);
                    updated++;
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not import students CSV: " + e.getMessage(), e);
        }
        audit.log(adminId, workstation, "STUDENTS_IMPORTED", "CSV", null,
                "inserted="+inserted+", updated="+updated+", skipped="+skipped);
        return new ImportResult(inserted, updated, skipped, warnings.stream().limit(50).toList());
    }

    public Dashboard dashboard() {
        return new Dashboard(
                q("SELECT COUNT(*) FROM books WHERE active=TRUE"),
                q("SELECT COUNT(*) FROM book_copies WHERE status<>'ARCHIVED'"),
                q("SELECT COUNT(*) FROM book_copies WHERE status='AVAILABLE'"),
                q("SELECT COUNT(*) FROM users WHERE role='STUDENT' AND active=TRUE"),
                q("SELECT COUNT(*) FROM loans WHERE status IN ('ISSUED','OVERDUE') AND returned_at IS NULL"),
                q("SELECT COUNT(*) FROM borrow_requests WHERE status='REQUESTED'"),
                q("SELECT COUNT(*) FROM loans WHERE returned_at IS NULL AND due_at<NOW()")
        );
    }

    public List<AdminLoanView> loans(int limit) {
        return jdbc.query("""
                SELECT l.id,u.student_code,u.name student_name,b.title,c.barcode,l.status,l.issued_at,l.due_at,l.returned_at
                FROM loans l JOIN users u ON u.id=l.user_id JOIN books b ON b.id=l.book_id JOIN book_copies c ON c.id=l.copy_id
                ORDER BY l.issued_at DESC LIMIT ?
                """, (r,n) -> new AdminLoanView(r.getLong("id"),r.getString("student_code"),r.getString("student_name"),
                        r.getString("title"),r.getString("barcode"),effectiveLoanStatus(r.getString("status"),r.getObject("due_at",OffsetDateTime.class),r.getObject("returned_at",OffsetDateTime.class)),
                        r.getObject("issued_at",OffsetDateTime.class),r.getObject("due_at",OffsetDateTime.class),r.getObject("returned_at",OffsetDateTime.class)), Math.max(1,Math.min(500,limit)));
    }

    public List<AuditView> activity(int limit) {
        return jdbc.query("""
                SELECT a.*,u.name actor_name FROM audit_log a LEFT JOIN users u ON u.id=a.actor_user_id
                ORDER BY a.created_at DESC LIMIT ?
                """, (r,n) -> new AuditView(r.getLong("id"),r.getString("actor_name"),r.getString("action"),
                        r.getString("entity_type"),r.getString("entity_id"),r.getString("details"),
                        r.getString("workstation_id"),r.getObject("created_at",OffsetDateTime.class)), Math.max(1,Math.min(500,limit)));
    }

    @Transactional
    public void bootstrapAdmin() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN'", Long.class);
        if (count != null && count == 0) {
            jdbc.update("INSERT INTO users(name,email,role,password_hash) VALUES('IntelliLib Administrator',?,'ADMIN',?)",
                    settings.adminEmail(), encoder.encode(settings.adminPassword()));
        }
    }

    private long q(String sql) {
        Long x = jdbc.queryForObject(sql, Long.class);
        return x == null ? 0 : x;
    }

    private static String effectiveLoanStatus(String status, OffsetDateTime due, OffsetDateTime returned) {
        return returned == null && due != null && due.isBefore(OffsetDateTime.now()) ? "OVERDUE" : status;
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    private static BufferedReader csvReader(MultipartFile file) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        reader.mark(1);
        int first = reader.read();
        if (first != 0xFEFF) reader.reset();
        return reader;
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String value(CSVRecord r, String name) { try { return r.isMapped(name) ? r.get(name).trim() : ""; } catch(Exception e) { return ""; } }
}
