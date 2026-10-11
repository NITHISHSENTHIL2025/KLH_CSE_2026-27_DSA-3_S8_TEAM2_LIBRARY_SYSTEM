package com.intellilib.server.service;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service public class AuditService { private final JdbcTemplate jdbc; public AuditService(JdbcTemplate j){jdbc=j;} public void log(Long actor,String workstation,String action,String type,Object id,String details){ jdbc.update("INSERT INTO audit_log(actor_user_id,workstation_id,action,entity_type,entity_id,details) VALUES(?,?,?,?,?,?)",actor,workstation,action,type,id==null?null:String.valueOf(id),details); } }
