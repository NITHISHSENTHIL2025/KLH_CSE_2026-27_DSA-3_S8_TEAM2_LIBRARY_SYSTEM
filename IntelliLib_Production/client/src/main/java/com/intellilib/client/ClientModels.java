package com.intellilib.client;

import java.time.OffsetDateTime;
import java.util.List;

public final class ClientModels {
    private ClientModels() {}
    public record SessionUser(long id,String name,String email,String studentCode,String department,String studyYear,String role){}
    public record LoginResponse(String sessionToken,OffsetDateTime expiresAt,SessionUser user){}
    public record Book(long id,String title,String author,String isbn,String category,String resourceType,int publicationYear,String publisher,String keywords,String description,String language,double rating,int totalCopies,int availableCopies,double relevance){}
    public record SearchResponse(List<Book> books,long durationMs,int total){}
    public record Suggestion(String text,String kind,Long bookId){}
    public record BorrowRequestView(long id,long bookId,String bookTitle,String author,String status,OffsetDateTime requestedAt,OffsetDateTime decisionAt,OffsetDateTime expiresAt){}
    public record LoanView(long id,long bookId,String title,String author,String barcode,String status,OffsetDateTime issuedAt,OffsetDateTime dueAt,OffsetDateTime returnedAt){}
    public record NotificationView(long id,String title,String message,OffsetDateTime createdAt,boolean read){}
    public record AdminRequestView(long id,long userId,String studentCode,String userName,long bookId,String bookTitle,String author,String status,OffsetDateTime requestedAt,int availableCopies){}
    public record UserView(long id,String studentCode,String name,String email,String department,String studyYear,boolean active,OffsetDateTime createdAt){}
    public record CreatedUser(long id,String studentCode,String name){}
    public record Dashboard(long books,long copies,long availableCopies,long students,long activeLoans,long pendingRequests,long overdueLoans){}
    public record CirculationResult(String status,String message,String studentName,String bookTitle,String bookBarcode,OffsetDateTime dueAt){}
    public record AdminLoanView(long id,String studentCode,String studentName,String bookTitle,String barcode,String status,OffsetDateTime issuedAt,OffsetDateTime dueAt,OffsetDateTime returnedAt){}
    public record AuditView(long id,String actorName,String action,String entityType,String entityId,String details,String workstationId,OffsetDateTime createdAt){}
    public record ImportResult(int inserted,int updated,int skipped,List<String> warnings){}
    public record ApprovedRequestMini(long requestId,long bookId,String title,String author,OffsetDateTime expiresAt){}
    public record StudentScanResult(long userId,String studentCode,String name,String department,List<ApprovedRequestMini> approvedRequests){}
    public record BookScanResult(long bookId,String title,String author,String isbn,String barcode,String status){}
}
