package com.intellilib.server.web;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.web.bind.annotation.*; import java.util.Map;
@RestController public class HealthController { private final JdbcTemplate jdbc; public HealthController(JdbcTemplate j){jdbc=j;} @GetMapping("/api/health") public Map<String,Object> health(){Long books=jdbc.queryForObject("SELECT COUNT(*) FROM books",Long.class);return Map.of("ok",true,"service","IntelliLib Server","books",books==null?0:books,"time",java.time.OffsetDateTime.now().toString());} }
