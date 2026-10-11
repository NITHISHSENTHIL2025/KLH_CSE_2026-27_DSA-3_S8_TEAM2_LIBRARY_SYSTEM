package com.intellilib.server.web;

import com.intellilib.server.model.ApiModels.*;
import com.intellilib.server.search.SearchEngine;
import com.intellilib.server.security.*;
import com.intellilib.server.service.LibraryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final SearchEngine search;
    private final AuthService auth;
    private final LibraryService library;
    private final JdbcTemplate jdbc;

    public BookController(SearchEngine search, AuthService auth, LibraryService library, JdbcTemplate jdbc) {
        this.search=search; this.auth=auth; this.library=library; this.jdbc=jdbc;
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam String q, @RequestParam(defaultValue="40") int limit, HttpServletRequest req) {
        SessionUser u=auth.require(AuthHeader.token(req));
        SearchResponse r=search.search(q,Math.min(100,Math.max(1,limit)));
        jdbc.update("INSERT INTO search_history(user_id,query,result_count,duration_ms) VALUES(?,?,?,?)",u.id(),q,r.total(),r.durationMs());
        return r;
    }

    @GetMapping("/suggestions")
    public List<Suggestion> suggestions(@RequestParam String q,@RequestParam(defaultValue="8")int limit,HttpServletRequest req){
        auth.require(AuthHeader.token(req)); return search.suggest(q,Math.min(15,Math.max(1,limit)));
    }

    @GetMapping("/{id}")
    public BookSummary one(@PathVariable long id,HttpServletRequest req){
        SessionUser u=auth.require(AuthHeader.token(req));
        BookSummary b=search.get(id); if(b==null)throw new IllegalArgumentException("Book not found");
        if("STUDENT".equals(u.role())) library.recordView(u.id(),id);
        return b;
    }

    @PostMapping("/{id}/view")
    public void viewed(@PathVariable long id,HttpServletRequest req){
        SessionUser u=auth.require(AuthHeader.token(req));
        if("STUDENT".equals(u.role())) library.recordView(u.id(),id);
    }

    @GetMapping("/{id}/related")
    public List<BookSummary> related(@PathVariable long id,@RequestParam(defaultValue="8")int limit,HttpServletRequest req){auth.require(AuthHeader.token(req));return search.related(id,Math.min(20,Math.max(1,limit)));}
    @GetMapping("/popular") public List<BookSummary> popular(@RequestParam(defaultValue="12")int limit,HttpServletRequest req){auth.require(AuthHeader.token(req));return library.popular(limit);}
    @GetMapping("/recommended") public List<BookSummary> recommended(@RequestParam(defaultValue="12")int limit,HttpServletRequest req){SessionUser u=auth.require(AuthHeader.token(req));return library.recommended(u.id(),limit);}
    @GetMapping("/browse") public List<BookSummary> browse(@RequestParam(defaultValue="A") String prefix,@RequestParam(defaultValue="100")int limit,HttpServletRequest req){auth.require(AuthHeader.token(req));return search.browse(prefix,Math.min(250,Math.max(1,limit)));}
}
