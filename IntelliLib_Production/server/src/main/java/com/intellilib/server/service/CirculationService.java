package com.intellilib.server.service;

import com.intellilib.server.config.AppConfig.AppSettings;
import com.intellilib.server.model.ApiModels.*;
import com.intellilib.server.security.CryptoUtil;
import com.intellilib.server.search.SearchEngine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime; import java.util.List;

@Service
public class CirculationService {
    private final JdbcTemplate jdbc; private final AppSettings settings; private final NotificationService notify; private final AuditService audit; private final SimpMessagingTemplate ws; private final SearchEngine search;
    public CirculationService(JdbcTemplate j,AppSettings s,NotificationService n,AuditService a,SimpMessagingTemplate w,SearchEngine se){jdbc=j;settings=s;notify=n;audit=a;ws=w;search=se;}

    @Transactional
    public long request(long userId,long bookId){
        expireApprovedRequests();

        Integer available=jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_copies WHERE book_id=? AND status='AVAILABLE'",
                Integer.class,bookId);
        if(available==null||available<=0)throw new IllegalStateException("No copy is currently available");

        List<Long> activeLoan=jdbc.queryForList("""
                SELECT id FROM loans
                WHERE user_id=? AND book_id=? AND returned_at IS NULL AND status IN ('ISSUED','OVERDUE')
                """,Long.class,userId,bookId);
        if(!activeLoan.isEmpty())throw new IllegalStateException("You already have this book issued");

        List<Long> existing=jdbc.queryForList("""
                SELECT id FROM borrow_requests
                WHERE user_id=? AND book_id=? AND status IN ('REQUESTED','APPROVED')
                """,Long.class,userId,bookId);
        if(!existing.isEmpty())throw new IllegalStateException("You already have an active request for this book");

        Long id=jdbc.queryForObject(
                "INSERT INTO borrow_requests(user_id,book_id) VALUES(?,?) RETURNING id",
                Long.class,userId,bookId);
        ws.convertAndSend("/topic/admin/requests",java.util.Map.of("type","new_request","requestId",id));
        return id;
    }
    public List<BorrowRequestView> myRequests(long userId){
        expireApprovedRequests();
        return jdbc.query("""
                SELECT r.*,b.title,b.author
                FROM borrow_requests r JOIN books b ON b.id=r.book_id
                WHERE r.user_id=?
                ORDER BY r.requested_at DESC
                """,(x,n)->new BorrowRequestView(
                        x.getLong("id"),x.getLong("book_id"),x.getString("title"),x.getString("author"),
                        x.getString("status"),x.getObject("requested_at",OffsetDateTime.class),
                        x.getObject("decision_at",OffsetDateTime.class),x.getObject("expires_at",OffsetDateTime.class)
                ),userId);
    }
    public List<LoanView> myLoans(long userId){return jdbc.query("SELECT l.*,b.title,b.author,c.barcode FROM loans l JOIN books b ON b.id=l.book_id JOIN book_copies c ON c.id=l.copy_id WHERE l.user_id=? ORDER BY l.issued_at DESC",(x,n)->new LoanView(x.getLong("id"),x.getLong("book_id"),x.getString("title"),x.getString("author"),x.getString("barcode"),overdueStatus(x.getString("status"),x.getObject("due_at",OffsetDateTime.class),x.getObject("returned_at",OffsetDateTime.class)),x.getObject("issued_at",OffsetDateTime.class),x.getObject("due_at",OffsetDateTime.class),x.getObject("returned_at",OffsetDateTime.class)),userId);}
    public List<AdminRequestView> activeRequests(){
        expireApprovedRequests();
        return jdbc.query("""
                SELECT r.id,r.user_id,u.student_code,u.name user_name,r.book_id,b.title,b.author,r.status,
                       r.requested_at,r.expires_at,
                       COUNT(c.id) FILTER(WHERE c.status='AVAILABLE') available
                FROM borrow_requests r
                JOIN users u ON u.id=r.user_id
                JOIN books b ON b.id=r.book_id
                LEFT JOIN book_copies c ON c.book_id=b.id
                WHERE r.status IN ('REQUESTED','APPROVED')
                GROUP BY r.id,u.student_code,u.name,b.title,b.author
                ORDER BY CASE WHEN r.status='APPROVED' THEN 0 ELSE 1 END,
                         COALESCE(r.decision_at,r.requested_at),r.requested_at
                """,(x,n)->new AdminRequestView(
                        x.getLong("id"),x.getLong("user_id"),x.getString("student_code"),x.getString("user_name"),
                        x.getLong("book_id"),x.getString("title"),x.getString("author"),x.getString("status"),
                        x.getObject("requested_at",OffsetDateTime.class),x.getObject("expires_at",OffsetDateTime.class),
                        x.getInt("available")));
    }

    @Transactional
    public void approve(long requestId,long adminId,String workstation,String note){
        expireApprovedRequests();
        RequestRow r=requestForUpdate(requestId);
        if(!"REQUESTED".equals(r.status))throw new IllegalStateException("Request is not pending");

        // Serialize approvals per book so two admin PCs cannot reserve the same last copy.
        jdbc.queryForObject("SELECT id FROM books WHERE id=? FOR UPDATE",Long.class,r.bookId);

        Integer available=jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_copies WHERE book_id=? AND status='AVAILABLE'",
                Integer.class,r.bookId);
        Integer reserved=jdbc.queryForObject("""
                SELECT COUNT(*) FROM borrow_requests
                WHERE book_id=? AND status='APPROVED' AND (expires_at IS NULL OR expires_at>NOW())
                """,Integer.class,r.bookId);
        if(available==null||available-(reserved==null?0:reserved)<1)
            throw new IllegalStateException("No unreserved copy is available for another approval");

        OffsetDateTime expiry=OffsetDateTime.now().plusDays(2);
        jdbc.update("""
                UPDATE borrow_requests
                SET status='APPROVED',decision_at=NOW(),decision_by=?,expires_at=?,note=?
                WHERE id=?
                """,adminId,expiry,note,requestId);
        notify.send(r.userId,"Borrow request approved",
                "Your book is ready for collection. Please bring your ID card to the library desk before "+expiry.toLocalDate()+".");
        audit.log(adminId,workstation,"REQUEST_APPROVED","BORROW_REQUEST",requestId,null);
    }
    @Transactional public void reject(long requestId,long adminId,String workstation,String note){ RequestRow r=requestForUpdate(requestId); if(!"REQUESTED".equals(r.status))throw new IllegalStateException("Request is not pending"); jdbc.update("UPDATE borrow_requests SET status='REJECTED',decision_at=NOW(),decision_by=?,note=? WHERE id=?",adminId,note,requestId); notify.send(r.userId,"Borrow request not approved",note==null||note.isBlank()?"Please contact the library desk if you need help.":note); audit.log(adminId,workstation,"REQUEST_REJECTED","BORROW_REQUEST",requestId,note); }

    @Transactional
    public CirculationResult provideApprovedRequest(long requestId,String cardToken,String barcode,long adminId,String workstation){
        expireApprovedRequests();
        RequestRow req=requestForUpdate(requestId);
        if(!"APPROVED".equals(req.status))throw new IllegalStateException("This request is not approved for collection");
        if(req.expiresAt!=null && !req.expiresAt.isAfter(OffsetDateTime.now()))
            throw new IllegalStateException("This approval has expired. Approve a new request before providing the book.");

        Student student=studentByCard(cardToken);
        if(student.id!=req.userId)
            throw new IllegalStateException("The scanned student ID does not match this approved request");

        Copy copy=copyForUpdate(barcode);
        if(copy.bookId!=req.bookId)
            throw new IllegalStateException("The scanned physical copy is not the approved book");
        if(!"AVAILABLE".equals(copy.status))
            throw new IllegalStateException("This physical copy is not available");

        List<Long> existingLoan=jdbc.queryForList("""
                SELECT id FROM loans
                WHERE user_id=? AND book_id=? AND returned_at IS NULL AND status IN ('ISSUED','OVERDUE')
                """,Long.class,student.id,copy.bookId);
        if(!existingLoan.isEmpty())
            throw new IllegalStateException("This student already has this book issued");

        OffsetDateTime due=OffsetDateTime.now().plusDays(settings.defaultDueDays());
        jdbc.update("UPDATE book_copies SET status='ISSUED' WHERE id=?",copy.id);
        jdbc.update("UPDATE borrow_requests SET status='ISSUED' WHERE id=?",req.id);
        jdbc.update("""
                INSERT INTO loans(user_id,book_id,copy_id,request_id,issued_by,due_at)
                VALUES(?,?,?,?,?,?)
                """,student.id,copy.bookId,copy.id,req.id,adminId,due);

        String title=jdbc.queryForObject("SELECT title FROM books WHERE id=?",String.class,copy.bookId);
        notify.send(student.id,"Book collected","\""+title+"\" was issued successfully. Due "+due.toLocalDate()+".");
        audit.log(adminId,workstation,"BOOK_PROVIDED","BORROW_REQUEST",req.id,
                "Student "+student.studentCode+", copy "+copy.barcode);
        search.refreshAvailability(copy.bookId);
        return new CirculationResult("ISSUED","Book provided successfully",student.name,title,copy.barcode,due);
    }

    public ReturnStudentView inspectReturns(String cardToken){
        Student student=studentByCard(cardToken);
        String department=jdbc.queryForObject("SELECT department FROM users WHERE id=?",String.class,student.id);
        List<LoanView> active=jdbc.query("""
                SELECT l.*,b.title,b.author,c.barcode
                FROM loans l
                JOIN books b ON b.id=l.book_id
                JOIN book_copies c ON c.id=l.copy_id
                WHERE l.user_id=? AND l.returned_at IS NULL AND l.status IN ('ISSUED','OVERDUE')
                ORDER BY l.due_at,l.issued_at
                """,(x,n)->new LoanView(
                        x.getLong("id"),x.getLong("book_id"),x.getString("title"),x.getString("author"),
                        x.getString("barcode"),overdueStatus(x.getString("status"),
                                x.getObject("due_at",OffsetDateTime.class),x.getObject("returned_at",OffsetDateTime.class)),
                        x.getObject("issued_at",OffsetDateTime.class),x.getObject("due_at",OffsetDateTime.class),
                        x.getObject("returned_at",OffsetDateTime.class)
                ),student.id);
        return new ReturnStudentView(student.id,student.studentCode,student.name,department,active);
    }

    @Transactional
    public CirculationResult returnLoan(String cardToken,long loanId,long adminId,String workstation){
        Student student=studentByCard(cardToken);
        List<LoanReturnRow> rows=jdbc.query("""
                SELECT id,user_id,book_id,copy_id,request_id,status,returned_at
                FROM loans WHERE id=? FOR UPDATE
                """,(r,n)->new LoanReturnRow(
                        r.getLong("id"),r.getLong("user_id"),r.getLong("book_id"),r.getLong("copy_id"),
                        (Long)r.getObject("request_id"),r.getString("status"),
                        r.getObject("returned_at",OffsetDateTime.class)
                ),loanId);
        if(rows.isEmpty())throw new IllegalArgumentException("Loan not found");

        LoanReturnRow loan=rows.getFirst();
        if(loan.userId!=student.id)throw new IllegalStateException("This loan does not belong to the scanned student");
        if(loan.returnedAt!=null || !("ISSUED".equals(loan.status)||"OVERDUE".equals(loan.status)))
            throw new IllegalStateException("This book has already been returned");

        Copy copy=copyForUpdateById(loan.copyId);
        jdbc.update("UPDATE loans SET status='RETURNED',returned_at=NOW(),returned_by=? WHERE id=?",adminId,loan.id);
        jdbc.update("UPDATE book_copies SET status='AVAILABLE' WHERE id=?",copy.id);
        if(loan.requestId!=null)
            jdbc.update("UPDATE borrow_requests SET status='RETURNED' WHERE id=? AND status='ISSUED'",loan.requestId);

        String title=jdbc.queryForObject("SELECT title FROM books WHERE id=?",String.class,loan.bookId);
        notify.send(student.id,"Book returned","\""+title+"\" was returned successfully. Thank you.");
        audit.log(adminId,workstation,"BOOK_RETURNED","LOAN",loan.id,
                "Student "+student.studentCode+", copy "+copy.barcode);
        search.refreshAvailability(loan.bookId);
        return new CirculationResult("RETURNED","Book returned successfully",student.name,title,copy.barcode,null);
    }

    public StudentScanResult inspectStudent(String cardToken){
        Student student=studentByCard(cardToken);
        String department=jdbc.queryForObject("SELECT department FROM users WHERE id=?",String.class,student.id);
        List<ApprovedRequestMini> approved=jdbc.query("""
                SELECT r.id request_id,r.book_id,b.title,b.author,r.expires_at
                FROM borrow_requests r JOIN books b ON b.id=r.book_id
                WHERE r.user_id=? AND r.status='APPROVED' AND (r.expires_at IS NULL OR r.expires_at>NOW())
                ORDER BY r.decision_at
                """,(r,n)->new ApprovedRequestMini(r.getLong("request_id"),r.getLong("book_id"),r.getString("title"),r.getString("author"),r.getObject("expires_at",OffsetDateTime.class)),student.id);
        return new StudentScanResult(student.id,student.studentCode,student.name,department,approved);
    }

    public BookScanResult inspectBook(String barcode){
        List<Copy> copies=jdbc.query("SELECT id,book_id,barcode,status FROM book_copies WHERE barcode=?",(r,n)->new Copy(r.getLong("id"),r.getLong("book_id"),r.getString("barcode"),r.getString("status")),barcode==null?"":barcode.trim());
        if(copies.isEmpty())throw new IllegalArgumentException("Book barcode not recognized");
        Copy copy=copies.getFirst();
        return jdbc.queryForObject("SELECT title,author,isbn FROM books WHERE id=?",(r,n)->new BookScanResult(copy.bookId,r.getString("title"),r.getString("author"),r.getString("isbn"),copy.barcode,copy.status),copy.bookId);
    }

    @Transactional public CirculationResult issue(String cardToken,String barcode,long adminId,String workstation){ Student student=studentByCard(cardToken); Copy copy=copyForUpdate(barcode); if(!"AVAILABLE".equals(copy.status))throw new IllegalStateException("This copy is not available"); List<RequestRow> reqs=jdbc.query("SELECT id,user_id,book_id,status,expires_at FROM borrow_requests WHERE user_id=? AND book_id=? AND status='APPROVED' AND (expires_at IS NULL OR expires_at>NOW()) ORDER BY decision_at LIMIT 1 FOR UPDATE",(r,n)->new RequestRow(r.getLong("id"),r.getLong("user_id"),r.getLong("book_id"),r.getString("status"),r.getObject("expires_at",OffsetDateTime.class)),student.id,copy.bookId); if(reqs.isEmpty())throw new IllegalStateException("No approved request found for this student and book"); RequestRow req=reqs.getFirst(); OffsetDateTime due=OffsetDateTime.now().plusDays(settings.defaultDueDays()); jdbc.update("UPDATE book_copies SET status='ISSUED' WHERE id=?",copy.id); jdbc.update("UPDATE borrow_requests SET status='ISSUED' WHERE id=?",req.id); jdbc.update("INSERT INTO loans(user_id,book_id,copy_id,request_id,issued_by,due_at) VALUES(?,?,?,?,?,?)",student.id,copy.bookId,copy.id,req.id,adminId,due); String title=jdbc.queryForObject("SELECT title FROM books WHERE id=?",String.class,copy.bookId); notify.send(student.id,"Book collected","\""+title+"\" was issued successfully. Due "+due.toLocalDate()+"."); audit.log(adminId,workstation,"BOOK_ISSUED","BOOK_COPY",copy.id,"Student "+student.studentCode); search.refreshAvailability(copy.bookId); return new CirculationResult("ISSUED","Book issued successfully",student.name,title,copy.barcode,due); }

    @Transactional
    public CirculationResult returned(String cardToken,String barcode,long adminId,String workstation){
        Student student=studentByCard(cardToken);
        Copy copy=copyForUpdate(barcode);

        List<ReturnLoan> loans=jdbc.query("""
                SELECT id,request_id FROM loans
                WHERE user_id=? AND copy_id=? AND status IN ('ISSUED','OVERDUE') AND returned_at IS NULL
                FOR UPDATE
                """,(r,n)->new ReturnLoan(r.getLong("id"),(Long)r.getObject("request_id")),student.id,copy.id);
        if(loans.isEmpty())throw new IllegalStateException("This book is not currently issued to this student");

        ReturnLoan loan=loans.getFirst();
        jdbc.update("UPDATE loans SET status='RETURNED',returned_at=NOW(),returned_by=? WHERE id=?",adminId,loan.id);
        jdbc.update("UPDATE book_copies SET status='AVAILABLE' WHERE id=?",copy.id);
        if(loan.requestId!=null)
            jdbc.update("UPDATE borrow_requests SET status='RETURNED' WHERE id=? AND status='ISSUED'",loan.requestId);

        String title=jdbc.queryForObject("SELECT title FROM books WHERE id=?",String.class,copy.bookId);
        notify.send(student.id,"Book returned","\""+title+"\" was returned successfully. Thank you.");
        audit.log(adminId,workstation,"BOOK_RETURNED","BOOK_COPY",copy.id,"Student "+student.studentCode);
        search.refreshAvailability(copy.bookId);
        return new CirculationResult("RETURNED","Book returned successfully",student.name,title,copy.barcode,null);
    }

    private void expireApprovedRequests(){
        jdbc.update("""
                UPDATE borrow_requests
                SET status='EXPIRED'
                WHERE status='APPROVED' AND expires_at IS NOT NULL AND expires_at<=NOW()
                """);
    }

    private RequestRow requestForUpdate(long id){List<RequestRow> a=jdbc.query("SELECT id,user_id,book_id,status,expires_at FROM borrow_requests WHERE id=? FOR UPDATE",(r,n)->new RequestRow(r.getLong("id"),r.getLong("user_id"),r.getLong("book_id"),r.getString("status"),r.getObject("expires_at",OffsetDateTime.class)),id); if(a.isEmpty())throw new IllegalArgumentException("Request not found"); return a.getFirst();}
    private Student studentByCard(String token){String code=CryptoUtil.normalizeScannedCode(token); if(code.isBlank())throw new IllegalArgumentException("No student ID code was detected"); String h=CryptoUtil.hmacSha256(settings.cardPepper(),code); List<Student>a=jdbc.query("SELECT id,student_code,name FROM users WHERE card_hash=? AND role='STUDENT' AND active=TRUE",(r,n)->new Student(r.getLong("id"),r.getString("student_code"),r.getString("name")),h); if(a.isEmpty())throw new IllegalArgumentException("This student ID is not enrolled or the account is disabled"); return a.getFirst();}
    private Copy copyForUpdate(String barcode){List<Copy>a=jdbc.query("SELECT id,book_id,barcode,status FROM book_copies WHERE barcode=? FOR UPDATE",(r,n)->new Copy(r.getLong("id"),r.getLong("book_id"),r.getString("barcode"),r.getString("status")),barcode==null?"":barcode.trim()); if(a.isEmpty())throw new IllegalArgumentException("Book barcode not recognized"); return a.getFirst();}
    private Copy copyForUpdateById(long copyId){List<Copy>a=jdbc.query("SELECT id,book_id,barcode,status FROM book_copies WHERE id=? FOR UPDATE",(r,n)->new Copy(r.getLong("id"),r.getLong("book_id"),r.getString("barcode"),r.getString("status")),copyId); if(a.isEmpty())throw new IllegalArgumentException("Physical copy not found"); return a.getFirst();}
    private static String overdueStatus(String status,OffsetDateTime due,OffsetDateTime returned){ return returned==null&&"ISSUED".equals(status)&&due!=null&&due.isBefore(OffsetDateTime.now())?"OVERDUE":status; }
    private record RequestRow(long id,long userId,long bookId,String status,OffsetDateTime expiresAt){}
    private record Student(long id,String studentCode,String name){}
    private record Copy(long id,long bookId,String barcode,String status){}
    private record ReturnLoan(long id,Long requestId){}
    private record LoanReturnRow(long id,long userId,long bookId,long copyId,Long requestId,String status,OffsetDateTime returnedAt){}
}
