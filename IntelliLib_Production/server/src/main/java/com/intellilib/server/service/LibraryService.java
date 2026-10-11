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

import java.io.BufferedReader;
import java.io.IOException;
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
        if (!rows.isEmpty()) return rows;

        // A brand-new real library has no issue history yet. Show recently imported
        // available resources until genuine circulation data exists; never fabricate counts.
        return jdbc.query("""
            SELECT b.*, COUNT(DISTINCT c.id) total_copies,
                   COUNT(DISTINCT c.id) FILTER(WHERE c.status='AVAILABLE') available_copies,
                   0 borrow_count
            FROM books b
            LEFT JOIN book_copies c ON c.book_id=b.id
            WHERE b.active=TRUE
            GROUP BY b.id
            HAVING COUNT(DISTINCT c.id) FILTER(WHERE c.status='AVAILABLE') > 0
            ORDER BY b.created_at DESC, b.title
            LIMIT ?
            """, (r,n) -> book(r,0), safeLimit);
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
        int skipped = 0;
        List<String> warnings = new ArrayList<>();

        // Parse first, then talk to PostgreSQL in batches. Doing one SELECT/INSERT per CSV row
        // is too slow for a cloud database and can make Render return a 502 before the request finishes.
        LinkedHashMap<String, ImportBookRow> logicalBooks = new LinkedHashMap<>();
        List<ImportCopyRow> copies = new ArrayList<>();

        try (var reader = csvReader(file);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .setIgnoreHeaderCase(true)
                     .setIgnoreSurroundingSpaces(true)
                     .build()
                     .parse(reader)) {

            int rowNumber = 1;
            for (CSVRecord r : parser) {
                rowNumber++;
                String title = value(r, "title");
                String author = value(r, "author");
                String isbn = blank(value(r, "isbn"));

                if (title.isBlank() || author.isBlank()) {
                    skipped++;
                    warnings.add("Row " + rowNumber + ": title and author are required");
                    continue;
                }

                ImportBookRow book = new ImportBookRow(
                        title,
                        author,
                        isbn,
                        safe(value(r, "category"), "General"),
                        blank(value(r, "keywords")),
                        safe(value(r, "resource_type"), "BOOK"),
                        blank(value(r, "publisher")),
                        firstNonNull(parseInt(value(r, "publication_year")), parseInt(value(r, "year"))),
                        blank(value(r, "description")),
                        safe(value(r, "language"), "English"),
                        Optional.ofNullable(parseDouble(value(r, "rating"))).orElse(0d)
                );

                String key = importBookKey(book);
                logicalBooks.put(key, book); // repeated ISBN/title rows are the same logical book

                String barcode = value(r, "barcode");
                if (barcode.isBlank()) barcode = value(r, "copy_barcode");
                barcode = barcode.trim();
                if (!barcode.isBlank()) copies.add(new ImportCopyRow(key, barcode));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not import books CSV: " + e.getMessage(), e);
        }

        if (logicalBooks.isEmpty()) {
            audit.log(adminId, workstation, "BOOKS_IMPORTED", "CSV", null,
                    "inserted=0, updated=0, skipped=" + skipped);
            return new ImportResult(0, 0, skipped, warnings.stream().limit(50).toList());
        }

        // Snapshot the current catalogue once. This turns thousands of cloud round-trips
        // into a handful of database operations.
        Map<String, Long> existingByKey = loadBookIdsByImportKey();
        List<ImportBookRow> toInsert = new ArrayList<>();
        List<BookUpdateRow> toUpdate = new ArrayList<>();

        for (var entry : logicalBooks.entrySet()) {
            Long id = existingByKey.get(entry.getKey());
            if (id == null) toInsert.add(entry.getValue());
            else toUpdate.add(new BookUpdateRow(id, entry.getValue()));
        }

        if (!toInsert.isEmpty()) {
            List<Object[]> args = new ArrayList<>(toInsert.size());
            for (ImportBookRow b : toInsert) {
                args.add(new Object[]{
                        b.title(), b.author(), b.isbn(), b.category(), b.keywords(), b.resourceType(),
                        b.publisher(), b.publicationYear(), b.description(), b.language(), b.rating()
                });
            }
            jdbc.batchUpdate("""
                    INSERT INTO books(title,author,isbn,category,keywords,resource_type,publisher,publication_year,description,language,rating)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    """, args);
        }

        if (!toUpdate.isEmpty()) {
            List<Object[]> args = new ArrayList<>(toUpdate.size());
            for (BookUpdateRow x : toUpdate) {
                ImportBookRow b = x.book();
                args.add(new Object[]{
                        b.title(), b.author(), b.category(), b.keywords(), b.resourceType(), b.publisher(),
                        b.publicationYear(), b.description(), b.language(), b.rating(), x.id()
                });
            }
            jdbc.batchUpdate("""
                    UPDATE books
                    SET title=?,author=?,category=?,keywords=?,resource_type=?,publisher=?,publication_year=?,
                        description=?,language=?,rating=?,active=TRUE,updated_at=NOW()
                    WHERE id=?
                    """, args);
        }

        // Refresh IDs once after inserts so copies can be attached without per-row lookups.
        Map<String, Long> idsByKey = loadBookIdsByImportKey();
        Set<String> existingBarcodes = new HashSet<>(jdbc.queryForList(
                "SELECT barcode FROM book_copies", String.class));
        Set<String> seenBarcodes = new HashSet<>();
        List<Object[]> copyArgs = new ArrayList<>();

        for (ImportCopyRow copy : copies) {
            if (!seenBarcodes.add(copy.barcode())) continue;
            if (existingBarcodes.contains(copy.barcode())) continue;
            Long bookId = idsByKey.get(copy.bookKey());
            if (bookId == null) {
                skipped++;
                warnings.add("Copy barcode could not be linked to a book: " + copy.barcode());
                continue;
            }
            copyArgs.add(new Object[]{bookId, copy.barcode()});
        }

        if (!copyArgs.isEmpty()) {
            jdbc.batchUpdate(
                    "INSERT INTO book_copies(book_id,barcode,status) VALUES(?,?,'AVAILABLE')",
                    copyArgs
            );
        }

        int inserted = toInsert.size();
        int updated = toUpdate.size();

        audit.log(adminId, workstation, "BOOKS_IMPORTED", "CSV", null,
                "inserted=" + inserted + ", updated=" + updated + ", skipped=" + skipped +
                        ", copies_added=" + copyArgs.size());

        // Rebuild once after the whole import, never once per book.
        search.rebuild();
        return new ImportResult(inserted, updated, skipped, warnings.stream().limit(50).toList());
    }

    private Map<String, Long> loadBookIdsByImportKey() {
        Map<String, Long> out = new HashMap<>();
        jdbc.query("SELECT id,title,author,isbn FROM books", rs -> {
            String isbn = blank(rs.getString("isbn"));
            String key = isbn != null
                    ? "isbn:" + isbn.toLowerCase(Locale.ROOT)
                    : "title:" + normalizedImportText(rs.getString("title")) + "|author:" + normalizedImportText(rs.getString("author"));
            out.putIfAbsent(key, rs.getLong("id"));
        });
        return out;
    }

    private static String importBookKey(ImportBookRow b) {
        return b.isbn() != null
                ? "isbn:" + b.isbn().toLowerCase(Locale.ROOT)
                : "title:" + normalizedImportText(b.title()) + "|author:" + normalizedImportText(b.author());
    }

    private static String normalizedImportText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static <T> T firstNonNull(T a, T b) { return a != null ? a : b; }

    private record ImportBookRow(
            String title, String author, String isbn, String category, String keywords,
            String resourceType, String publisher, Integer publicationYear, String description,
            String language, Double rating) {}

    private record ImportCopyRow(String bookKey, String barcode) {}
    private record BookUpdateRow(long id, ImportBookRow book) {}

    @Transactional
    public ImportResult importCopies(MultipartFile file, long adminId, String workstation) {
        int inserted=0, updated=0, skipped=0;
        List<String>warnings=new ArrayList<>();
        try(var reader=csvReader(file);
            CSVParser parser=CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .setIgnoreHeaderCase(true)
                    .setIgnoreSurroundingSpaces(true)
                    .build()
                    .parse(reader)){
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
    private static BufferedReader csvReader(MultipartFile file) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        reader.mark(1);
        int first = reader.read();
        if (first != 0xFEFF) reader.reset(); // tolerate UTF-8 BOM written by Excel/Windows tools
        return reader;
    }

    private static String bookKey(String title,String author){
        return safe(title,"").toLowerCase(Locale.ROOT)+"\u0000"+safe(author,"").toLowerCase(Locale.ROOT);
    }
    private static String blank(String s){return s==null||s.isBlank()?null:s.trim();}
    private static String safe(String s,String d){return s==null||s.isBlank()?d:s.trim();}
    private static String value(CSVRecord r,String n){try{return r.isMapped(n)?r.get(n).trim():"";}catch(Exception e){return "";}}
    private static Integer parseInt(String s){try{return s==null||s.isBlank()?null:Integer.parseInt(s.trim());}catch(Exception e){return null;}}
    private static Double parseDouble(String s){try{return s==null||s.isBlank()?null:Double.parseDouble(s.trim());}catch(Exception e){return null;}}
}
