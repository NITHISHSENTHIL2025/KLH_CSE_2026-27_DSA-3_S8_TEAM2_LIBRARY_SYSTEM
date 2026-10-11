package com.intellilib.server.service;

import com.intellilib.server.model.ApiModels.*;
import com.intellilib.server.search.SearchEngine;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class LibraryService {
    private final JdbcTemplate jdbc;
    private final SearchEngine search;
    private final AuditService audit;

    public LibraryService(JdbcTemplate jdbc, SearchEngine search, AuditService audit) {
        this.jdbc = jdbc;
        this.search = search;
        this.audit = audit;
    }

    /** Real popularity: successful issue records only. If there is no history yet, show recently-added resources. */
    public List<BookSummary> popular(int limit) {
        int safeLimit = Math.max(1, Math.min(30, limit));
        List<BookSummary> rows = jdbc.query("""
            SELECT b.*, COUNT(DISTINCT c.id) total_copies,
                   COUNT(DISTINCT c.id) FILTER(WHERE c.status='AVAILABLE') available_copies,
                   COUNT(DISTINCT l.id) borrow_count
            FROM books b
            LEFT JOIN book_copies c ON c.book_id=b.id
            LEFT JOIN loans l ON l.book_id=b.id
            WHERE b.active=TRUE
            GROUP BY b.id
            HAVING COUNT(DISTINCT l.id) > 0
            ORDER BY borrow_count DESC, b.title
            LIMIT ?
            """, (r,n) -> book(r,0), safeLimit);
        return rows;
    }

    /** Recommendations use only this student's own loans/views plus real library metadata. */
    public List<BookSummary> recommended(long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(30, limit));
        List<String> categories = jdbc.queryForList("""
            SELECT category FROM (
              SELECT b.category, COUNT(*) score
              FROM (
                SELECT book_id FROM loans WHERE user_id=?
                UNION ALL
                SELECT book_id FROM book_views WHERE user_id=? AND viewed_at > NOW()-INTERVAL '90 days'
              ) x JOIN books b ON b.id=x.book_id
              WHERE b.category IS NOT NULL AND b.category<>''
              GROUP BY b.category ORDER BY score DESC LIMIT 5
            ) q
            """, String.class, userId, userId);
        if (categories.isEmpty()) return popular(safeLimit);
        String placeholders = String.join(",", Collections.nCopies(categories.size(), "?"));
        List<Object> args = new ArrayList<>(categories);
        args.add(userId);
        args.add(safeLimit);
        String sql = "SELECT b.*, COUNT(DISTINCT c.id) total_copies, " +
                "COUNT(DISTINCT c.id) FILTER(WHERE c.status=\'AVAILABLE\') available_copies, 0 borrow_count " +
                "FROM books b LEFT JOIN book_copies c ON c.book_id=b.id " +
                "WHERE b.active=TRUE AND b.category IN (" + placeholders + ") " +
                "AND b.id NOT IN (SELECT book_id FROM loans WHERE user_id=?) " +
                "GROUP BY b.id ORDER BY COUNT(DISTINCT c.id) FILTER(WHERE c.status=\'AVAILABLE\') DESC,b.title LIMIT ?";
        return jdbc.query(sql, (r,n) -> book(r,0), args.toArray());
    }

    public void recordView(long userId, long bookId) {
        jdbc.update("INSERT INTO book_views(user_id,book_id) VALUES(?,?)", userId, bookId);
    }

    @Transactional
    public long createBook(NewBookRequest b, long adminId, String workstation) {
        if (b.title()==null || b.title().isBlank() || b.author()==null || b.author().isBlank())
            throw new IllegalArgumentException("Title and author are required");
        String isbn = blank(b.isbn());
        if (isbn != null && !jdbc.queryForList("SELECT id FROM books WHERE isbn=?", Long.class, isbn).isEmpty())
            throw new IllegalArgumentException("A resource with this ISBN already exists");
        Long id = jdbc.queryForObject("""
            INSERT INTO books(title,author,isbn,category,keywords,resource_type,publisher,publication_year,description,language,rating)
            VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id
            """, Long.class, b.title().trim(), b.author().trim(), isbn, safe(b.category(),"General"), blank(b.keywords()),
                safe(b.resourceType(),"BOOK"), blank(b.publisher()), b.publicationYear(), blank(b.description()), safe(b.language(),"English"),
                b.rating()==null?0:Math.max(0,Math.min(5,b.rating())));
        audit.log(adminId, workstation, "BOOK_CREATED", "BOOK", id, b.title());
        search.rebuild();
        return id;
    }

    @Transactional
    public String addCopy(long bookId, String barcode, long adminId, String workstation) {
        if (barcode == null || barcode.isBlank()) throw new IllegalArgumentException("Book barcode is required");
        Long exists = jdbc.query("SELECT id FROM books WHERE id=? AND active=TRUE", rs -> rs.next()?rs.getLong(1):null, bookId);
        if (exists == null) throw new IllegalArgumentException("Book not found");
        String normalized = barcode.trim();
        if (!jdbc.queryForList("SELECT id FROM book_copies WHERE barcode=?", Long.class, normalized).isEmpty())
            throw new IllegalArgumentException("This physical barcode is already registered");
        jdbc.update("INSERT INTO book_copies(book_id,barcode,status) VALUES(?,?,'AVAILABLE')", bookId, normalized);
        audit.log(adminId, workstation, "BOOK_COPY_ADDED", "BOOK", bookId, normalized);
        search.rebuild();
        return normalized;
    }

    @Transactional
    public ImportResult importBooks(MultipartFile file, long adminId, String workstation) {
        int inserted=0, updated=0, skipped=0;
        List<String> warnings = new ArrayList<>();
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            int row=1;
            for (CSVRecord r: parser) {
                row++;
                String title=value(r,"title"), author=value(r,"author"), isbn=blank(value(r,"isbn"));
                if (title.isBlank() || author.isBlank()) { skipped++; warnings.add("Row "+row+": title and author are required"); continue; }
                String category=safe(value(r,"category"),"General");
                String keywords=blank(value(r,"keywords"));
                String type=safe(value(r,"resource_type"),"BOOK");
                String publisher=blank(value(r,"publisher"));
                Integer year=parseInt(value(r,"publication_year")); if(year==null) year=parseInt(value(r,"year"));
                String description=blank(value(r,"description"));
                String language=safe(value(r,"language"),"English");
                Double rating=parseDouble(value(r,"rating")); if(rating==null) rating=0d;
                Long existing = isbn==null?null:jdbc.query("SELECT id FROM books WHERE isbn=?", rs->rs.next()?rs.getLong(1):null, isbn);
                Long bookId;
                if (existing==null) {
                    bookId=jdbc.queryForObject("""
                        INSERT INTO books(title,author,isbn,category,keywords,resource_type,publisher,publication_year,description,language,rating)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                        """,Long.class,title,author,isbn,category,keywords,type,publisher,year,description,language,rating);
                    inserted++;
                } else {
                    bookId=existing;
                    jdbc.update("""
                        UPDATE books SET title=?,author=?,category=?,keywords=?,resource_type=?,publisher=?,publication_year=?,description=?,language=?,rating=?,active=TRUE,updated_at=NOW()
                        WHERE id=?
                        """,title,author,category,keywords,type,publisher,year,description,language,rating,existing);
                    updated++;
                }
                String barcode=value(r,"barcode"); if(barcode.isBlank()) barcode=value(r,"copy_barcode");
                if(!barcode.isBlank() && jdbc.queryForList("SELECT id FROM book_copies WHERE barcode=?",Long.class,barcode).isEmpty())
                    jdbc.update("INSERT INTO book_copies(book_id,barcode,status) VALUES(?,?,'AVAILABLE')",bookId,barcode);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not import books CSV: "+e.getMessage(),e);
        }
        audit.log(adminId,workstation,"BOOKS_IMPORTED","CSV",null,"inserted="+inserted+", updated="+updated+", skipped="+skipped);
        search.rebuild();
        return new ImportResult(inserted,updated,skipped,warnings.stream().limit(50).toList());
    }

    @Transactional
    public ImportResult importCopies(MultipartFile file, long adminId, String workstation) {
        int inserted=0, updated=0, skipped=0;
        List<String>warnings=new ArrayList<>();
        try(var reader=new InputStreamReader(file.getInputStream(),StandardCharsets.UTF_8);
            CSVParser parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)){
            int row=1;
            for(CSVRecord r:parser){
                row++;
                String isbn=blank(value(r,"isbn")); String barcode=value(r,"barcode");
                if(isbn==null||barcode.isBlank()){skipped++;warnings.add("Row "+row+": isbn and barcode are required");continue;}
                Long bookId=jdbc.query("SELECT id FROM books WHERE isbn=?",rs->rs.next()?rs.getLong(1):null,isbn);
                if(bookId==null){skipped++;warnings.add("Row "+row+": ISBN not found: "+isbn);continue;}
                Long copyId=jdbc.query("SELECT id FROM book_copies WHERE barcode=?",rs->rs.next()?rs.getLong(1):null,barcode);
                if(copyId==null){jdbc.update("INSERT INTO book_copies(book_id,barcode,status) VALUES(?,?,'AVAILABLE')",bookId,barcode);inserted++;}
                else {int changed=jdbc.update("UPDATE book_copies SET book_id=? WHERE id=? AND status='AVAILABLE'",bookId,copyId);if(changed>0)updated++;else{skipped++;warnings.add("Row "+row+": barcode is currently issued/lost/archived and was not reassigned: "+barcode);}}
            }
        }catch(Exception e){throw new IllegalArgumentException("Could not import copies CSV: "+e.getMessage(),e);}
        audit.log(adminId,workstation,"BOOK_COPIES_IMPORTED","CSV",null,"inserted="+inserted+", updated="+updated+", skipped="+skipped);
        search.rebuild();
        return new ImportResult(inserted,updated,skipped,warnings.stream().limit(50).toList());
    }

    public String exportCsv() {
    try {
        StringWriter out = new StringWriter();

        var printer = CSVFormat.DEFAULT.builder()
                .setHeader(
                        "title",
                        "author",
                        "isbn",
                        "category",
                        "keywords",
                        "resource_type",
                        "publisher",
                        "publication_year",
                        "description",
                        "language",
                        "rating"
                )
                .build()
                .print(out);

        jdbc.query(
                "SELECT * FROM books WHERE active=TRUE ORDER BY title",
                rs -> {
                    try {
                        printer.printRecord(
                                rs.getString("title"),
                                rs.getString("author"),
                                rs.getString("isbn"),
                                rs.getString("category"),
                                rs.getString("keywords"),
                                rs.getString("resource_type"),
                                rs.getString("publisher"),
                                rs.getObject("publication_year"),
                                rs.getString("description"),
                                rs.getString("language"),
                                rs.getDouble("rating")
                        );
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(
                                "Could not write books CSV",
                                e
                        );
                    }
                }
        );

        printer.flush();
        return out.toString();

    } catch (Exception e) {
        throw new IllegalStateException(
                "Could not export books",
                e
        );
    }
}

    private static BookSummary book(java.sql.ResultSet r,double relevance)throws java.sql.SQLException{
        return new BookSummary(r.getLong("id"),r.getString("title"),r.getString("author"),r.getString("isbn"),r.getString("category"),
                r.getString("resource_type"),r.getObject("publication_year")==null?0:r.getInt("publication_year"),r.getString("publisher"),
                r.getString("keywords"),r.getString("description"),r.getString("language"),r.getDouble("rating"),
                r.getInt("total_copies"),r.getInt("available_copies"),relevance);
    }
    private static String blank(String s){return s==null||s.isBlank()?null:s.trim();}
    private static String safe(String s,String d){return s==null||s.isBlank()?d:s.trim();}
    private static String value(CSVRecord r,String n){try{return r.isMapped(n)?r.get(n).trim():"";}catch(Exception e){return "";}}
    private static Integer parseInt(String s){try{return s==null||s.isBlank()?null:Integer.parseInt(s.trim());}catch(Exception e){return null;}}
    private static Double parseDouble(String s){try{return s==null||s.isBlank()?null:Double.parseDouble(s.trim());}catch(Exception e){return null;}}
}
