package com.intellilib.server.web;

import com.intellilib.server.model.ApiModels.*;
import com.intellilib.server.security.*;
import com.intellilib.server.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuthService auth;
    private final AdminService admin;
    private final CirculationService circulation;
    private final LibraryService library;

    public AdminController(AuthService auth, AdminService admin, CirculationService circulation, LibraryService library) {
        this.auth=auth; this.admin=admin; this.circulation=circulation; this.library=library;
    }
    private SessionUser me(HttpServletRequest r){return auth.requireAdmin(AuthHeader.token(r));}

    @GetMapping("/dashboard") public Dashboard dashboard(HttpServletRequest r){me(r);return admin.dashboard();}
    @GetMapping("/requests") public List<AdminRequestView> requests(HttpServletRequest r){me(r);return circulation.activeRequests();}
    @PostMapping("/requests/{id}/approve") public Map<String,Object> approve(@PathVariable long id,@RequestBody(required=false) DecisionRequest d,HttpServletRequest r){SessionUser u=me(r);circulation.approve(id,u.id(),r.getHeader("X-Workstation-Id"),d==null?null:d.note());return Map.of("ok",true);}
    @PostMapping("/requests/{id}/reject") public Map<String,Object> reject(@PathVariable long id,@RequestBody(required=false) DecisionRequest d,HttpServletRequest r){SessionUser u=me(r);circulation.reject(id,u.id(),r.getHeader("X-Workstation-Id"),d==null?null:d.note());return Map.of("ok",true);}
    @PostMapping("/requests/{id}/provide") public CirculationResult provide(@PathVariable long id,@RequestBody ProvideBookRequest q,HttpServletRequest r){SessionUser u=me(r);return circulation.provideApprovedRequest(id,q.studentCardToken(),q.bookBarcode(),u.id(),q.workstationId());}
    @PostMapping("/scan/student") public StudentScanResult inspectStudent(@RequestBody Map<String,String> q,HttpServletRequest r){me(r);return circulation.inspectStudent(q.get("cardCode"));}
    @PostMapping("/return/student") public ReturnStudentView inspectReturns(@RequestBody Map<String,String> q,HttpServletRequest r){me(r);return circulation.inspectReturns(q.get("cardCode"));}
    @PostMapping("/return/loan/{loanId}") public CirculationResult returnLoan(@PathVariable long loanId,@RequestBody ReturnByLoanRequest q,HttpServletRequest r){SessionUser u=me(r);return circulation.returnLoan(q.studentCardToken(),loanId,u.id(),q.workstationId());}
    @PostMapping("/scan/book") public BookScanResult inspectBook(@RequestBody Map<String,String> q,HttpServletRequest r){me(r);return circulation.inspectBook(q.get("barcode"));}
    @PostMapping("/issue") public CirculationResult issue(@RequestBody IssueRequest q,HttpServletRequest r){SessionUser u=me(r);return circulation.issue(q.studentCardToken(),q.bookBarcode(),u.id(),q.workstationId());}
    @PostMapping("/return") public CirculationResult returned(@RequestBody ReturnRequest q,HttpServletRequest r){SessionUser u=me(r);return circulation.returned(q.studentCardToken(),q.bookBarcode(),u.id(),q.workstationId());}

    @GetMapping("/users") public List<UserView> users(HttpServletRequest r){me(r);return admin.users();}
    @PostMapping("/users") public CreatedUser newUser(@RequestBody NewUserRequest q,HttpServletRequest r){SessionUser u=me(r);return admin.createStudent(q,u.id(),r.getHeader("X-Workstation-Id"));}
    @PostMapping("/users/{id}/active") public Map<String,Object> active(@PathVariable long id,@RequestParam boolean value,HttpServletRequest r){SessionUser u=me(r);admin.setActive(id,value,u.id(),r.getHeader("X-Workstation-Id"));return Map.of("ok",true);}
    @PostMapping(value="/users/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ImportResult importUsers(@RequestPart("file") MultipartFile file,HttpServletRequest r){SessionUser u=me(r);return admin.importStudents(file,u.id(),r.getHeader("X-Workstation-Id"));}

    @PostMapping("/books") public Map<String,Object> newBook(@RequestBody NewBookRequest q,HttpServletRequest r){SessionUser u=me(r);return Map.of("id",library.createBook(q,u.id(),r.getHeader("X-Workstation-Id")));}
    @PostMapping("/books/{id}/copies") public AddCopyResult addCopy(@PathVariable long id,@RequestBody AddCopyRequest q,HttpServletRequest r){SessionUser u=me(r);String code=library.addCopy(id,q.barcode(),u.id(),r.getHeader("X-Workstation-Id"));return new AddCopyResult(id,"Book",code);}
    @PostMapping(value="/books/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ImportResult importBooks(@RequestPart("file") MultipartFile file,HttpServletRequest r)throws IOException{SessionUser u=me(r);return library.importBooks(file,u.id(),r.getHeader("X-Workstation-Id"));}
    @PostMapping(value="/copies/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ImportResult importCopies(@RequestPart("file") MultipartFile file,HttpServletRequest r)throws IOException{SessionUser u=me(r);return library.importCopies(file,u.id(),r.getHeader("X-Workstation-Id"));}
    @GetMapping(value="/books/export",produces="text/csv") public ResponseEntity<byte[]> export(HttpServletRequest r){me(r);byte[] b=library.exportCsv().getBytes(StandardCharsets.UTF_8);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=intellilib_books.csv").body(b);}

    @GetMapping("/loans") public List<AdminLoanView> loans(@RequestParam(defaultValue="200")int limit,HttpServletRequest r){me(r);return admin.loans(limit);}
    @GetMapping("/activity") public List<AuditView> activity(@RequestParam(defaultValue="200")int limit,HttpServletRequest r){me(r);return admin.activity(limit);}
}
