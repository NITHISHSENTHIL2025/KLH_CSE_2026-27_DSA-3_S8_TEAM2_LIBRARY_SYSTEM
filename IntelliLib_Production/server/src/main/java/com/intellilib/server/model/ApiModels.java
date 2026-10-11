package com.intellilib.server.model;

import java.time.OffsetDateTime;
import java.util.List;

public final class ApiModels {
    private ApiModels() {}

    public record SessionUser(long id, String name, String email, String studentCode, String department, String studyYear, String role) {}
    public record LoginResponse(String sessionToken, OffsetDateTime expiresAt, SessionUser user) {}
    public record CardLoginRequest(String cardToken, String workstationId) {}
    public record AdminLoginRequest(String email, String password, String workstationId) {}

    public record BookSummary(long id, String title, String author, String isbn, String category, String resourceType,
                              int publicationYear, String publisher, String keywords, String description, String language,
                              double rating, int totalCopies, int availableCopies, double relevance) {}
    public record SearchResponse(List<BookSummary> books, long durationMs, int total) {}
    public record Suggestion(String text, String kind, Long bookId) {}

    public record BorrowRequestView(long id, long bookId, String bookTitle, String author, String status,
                                    OffsetDateTime requestedAt, OffsetDateTime decisionAt, OffsetDateTime expiresAt) {}
    public record LoanView(long id, long bookId, String title, String author, String barcode, String status,
                           OffsetDateTime issuedAt, OffsetDateTime dueAt, OffsetDateTime returnedAt) {}
    public record NotificationView(long id, String title, String message, OffsetDateTime createdAt, boolean read) {}

    public record AdminRequestView(long id, long userId, String studentCode, String userName, long bookId, String bookTitle,
                                   String author, String status, OffsetDateTime requestedAt, OffsetDateTime expiresAt,
                                   int availableCopies) {}
    public record DecisionRequest(String note) {}
    public record IssueRequest(String studentCardToken, String bookBarcode, String workstationId) {}
    public record ProvideBookRequest(String studentCardToken, String bookBarcode, String workstationId) {}
    public record ReturnRequest(String studentCardToken, String bookBarcode, String workstationId) {}
    public record ReturnByLoanRequest(String studentCardToken, String workstationId) {}
    public record CirculationResult(String status, String message, String studentName, String bookTitle,
                                    String bookBarcode, OffsetDateTime dueAt) {}

    public record NewUserRequest(String studentCode, String name, String email, String department, String studyYear, String cardCode) {}
    public record CreatedUser(long id, String studentCode, String name) {}
    public record UserView(long id, String studentCode, String name, String email, String department, String studyYear,
                           boolean active, OffsetDateTime createdAt) {}
    public record NewBookRequest(String title, String author, String isbn, String category, String keywords,
                                 String resourceType, String publisher, Integer publicationYear, String description,
                                 String language, Double rating, Integer copies) {}
    public record AddCopyRequest(String barcode) {}
    public record AddCopyResult(long bookId, String title, String barcode) {}
    public record Dashboard(long books, long copies, long availableCopies, long students, long activeLoans,
                            long pendingRequests, long overdueLoans) {}
    public record AdminLoanView(long id, String studentCode, String studentName, String bookTitle, String barcode,
                                String status, OffsetDateTime issuedAt, OffsetDateTime dueAt, OffsetDateTime returnedAt) {}
    public record AuditView(long id, String actorName, String action, String entityType, String entityId,
                            String details, String workstationId, OffsetDateTime createdAt) {}
    public record ImportResult(int inserted, int updated, int skipped, List<String> warnings) {}
    public record ApprovedRequestMini(long requestId,long bookId,String title,String author,OffsetDateTime expiresAt) {}
    public record StudentScanResult(long userId,String studentCode,String name,String department,List<ApprovedRequestMini> approvedRequests) {}
    public record ReturnStudentView(long userId,String studentCode,String name,String department,List<LoanView> activeLoans) {}
    public record BookScanResult(long bookId,String title,String author,String isbn,String barcode,String status) {}
}
