package com.intellilib.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public final class ApiClient {
    private final AppConfig config;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ApiClient(AppConfig config){this.config=config;}
    public ObjectMapper mapper(){return json;}

    private HttpRequest.Builder base(String path){
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(config.server()+path))
                .timeout(Duration.ofSeconds(90)).header("Accept","application/json").header("X-Workstation-Id",config.workstationId());
        if(SessionContext.token()!=null)b.header("X-Session-Token",SessionContext.token());
        return b;
    }
    private <T>T sendClass(HttpRequest req,Class<T> cls){
        try{
            HttpResponse<String> r=http.send(req,HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()>=400)throw new IllegalStateException(error(r.statusCode(),r.body()));
            return cls==Void.class?null:json.readValue(r.body(),cls);
        }catch(java.net.http.HttpTimeoutException e){
            throw new IllegalStateException("The cloud server took too long to respond. Please wait a moment and try again.");
        }catch(Exception e){
            throw e instanceof RuntimeException?(RuntimeException)e:new IllegalStateException(e.getMessage(),e);
        }
    }
    private <T>T sendType(HttpRequest req,TypeReference<T> type){
        try{
            HttpResponse<String> r=http.send(req,HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()>=400)throw new IllegalStateException(error(r.statusCode(),r.body()));
            return json.readValue(r.body(),type);
        }catch(java.net.http.HttpTimeoutException e){
            throw new IllegalStateException("The cloud server took too long to respond. Please wait a moment and try again.");
        }catch(Exception e){
            throw e instanceof RuntimeException?(RuntimeException)e:new IllegalStateException(e.getMessage(),e);
        }
    }
    private String error(int status,String body){
        try{
            JsonNode n=json.readTree(body);
            String message=n.path("error").asText("");
            if(!message.isBlank())return message;
        }catch(Exception ignored){}
        String text=body==null?"":body.stripLeading().toLowerCase(Locale.ROOT);
        if(status==502||status==503||status==504||text.startsWith("<!doctype html")||text.startsWith("<html")){
            return "The IntelliLib cloud service is temporarily unavailable (HTTP "+status+"). Wait a few seconds and try again.";
        }
        return "Request failed (HTTP "+status+").";
    }
    private String body(Object x){try{return json.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}

    public boolean health(){try{HttpResponse<String> r=http.send(HttpRequest.newBuilder(URI.create(config.server()+"/api/health")).timeout(Duration.ofSeconds(70)).GET().build(),HttpResponse.BodyHandlers.ofString());return r.statusCode()==200;}catch(Exception e){return false;}}
    public ClientModels.LoginResponse cardLogin(String token){return sendClass(base("/api/auth/card").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("cardToken",token,"workstationId",config.workstationId())))).build(),ClientModels.LoginResponse.class);}
    public ClientModels.LoginResponse adminLogin(String email,String password){return sendClass(base("/api/auth/admin").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("email",email,"password",password,"workstationId",config.workstationId())))).build(),ClientModels.LoginResponse.class);}
    public void logout(){if(SessionContext.token()!=null)sendClass(base("/api/auth/logout").POST(HttpRequest.BodyPublishers.noBody()).build(),Map.class);}

    public ClientModels.SearchResponse search(String q){return sendClass(base("/api/books/search?q="+enc(q)+"&limit=80").GET().build(),ClientModels.SearchResponse.class);}
    public List<ClientModels.Suggestion> suggestions(String q){return sendType(base("/api/books/suggestions?q="+enc(q)+"&limit=8").GET().build(),new TypeReference<>(){});}
    public List<ClientModels.Book> popular(){return sendType(base("/api/books/popular?limit=12").GET().build(),new TypeReference<>(){});}
    public List<ClientModels.Book> recommended(){return sendType(base("/api/books/recommended?limit=12").GET().build(),new TypeReference<>(){});}
    public List<ClientModels.Book> browse(String prefix,int limit){return sendType(base("/api/books/browse?prefix="+enc(prefix==null?"":prefix)+"&limit="+limit).GET().build(),new TypeReference<>(){});}
    public List<ClientModels.Book> related(long id){return sendType(base("/api/books/"+id+"/related?limit=8").GET().build(),new TypeReference<>(){});}
    public void viewed(long id){sendClass(base("/api/books/"+id+"/view").POST(HttpRequest.BodyPublishers.noBody()).build(),Void.class);}

    public void requestBorrow(long bookId){sendClass(base("/api/me/requests/"+bookId).POST(HttpRequest.BodyPublishers.noBody()).build(),Map.class);}
    public List<ClientModels.BorrowRequestView> myRequests(){return sendType(base("/api/me/requests").GET().build(),new TypeReference<>(){});}
    public List<ClientModels.LoanView> myLoans(){return sendType(base("/api/me/loans").GET().build(),new TypeReference<>(){});}
    public List<ClientModels.NotificationView> notifications(){return sendType(base("/api/me/notifications").GET().build(),new TypeReference<>(){});}

    public ClientModels.Dashboard dashboard(){return sendClass(base("/api/admin/dashboard").GET().build(),ClientModels.Dashboard.class);}
    public List<ClientModels.AdminRequestView> adminRequests(){return sendType(base("/api/admin/requests").GET().build(),new TypeReference<>(){});}
    public void decide(long id,boolean approve,String note){String p="/api/admin/requests/"+id+(approve?"/approve":"/reject");sendClass(base(p).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("note",note==null?"":note)))).build(),Map.class);}
    public ClientModels.CirculationResult provideRequest(long requestId,String card,String barcode){return sendClass(base("/api/admin/requests/"+requestId+"/provide").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("studentCardToken",card,"bookBarcode",barcode,"workstationId",config.workstationId())))).build(),ClientModels.CirculationResult.class);}
    public ClientModels.StudentScanResult inspectStudent(String card){return sendClass(base("/api/admin/scan/student").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("cardCode",card)))).build(),ClientModels.StudentScanResult.class);}
    public ClientModels.ReturnStudentView inspectReturnStudent(String card){return sendClass(base("/api/admin/return/student").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("cardCode",card)))).build(),ClientModels.ReturnStudentView.class);}
    public ClientModels.BookScanResult inspectBook(String barcode){return sendClass(base("/api/admin/scan/book").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("barcode",barcode)))).build(),ClientModels.BookScanResult.class);}
    public ClientModels.CirculationResult issue(String card,String barcode){return sendClass(base("/api/admin/issue").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("studentCardToken",card,"bookBarcode",barcode,"workstationId",config.workstationId())))).build(),ClientModels.CirculationResult.class);}
    public ClientModels.CirculationResult returned(String card,String barcode){return sendClass(base("/api/admin/return").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("studentCardToken",card,"bookBarcode",barcode,"workstationId",config.workstationId())))).build(),ClientModels.CirculationResult.class);}
    public ClientModels.CirculationResult returnLoan(String card,long loanId){return sendClass(base("/api/admin/return/loan/"+loanId).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("studentCardToken",card,"workstationId",config.workstationId())))).build(),ClientModels.CirculationResult.class);}

    public List<ClientModels.UserView> users(){return sendType(base("/api/admin/users").GET().build(),new TypeReference<>(){});}
    public ClientModels.CreatedUser createUser(Map<String,Object> data){return sendClass(base("/api/admin/users").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(data))).build(),ClientModels.CreatedUser.class);}
    public void setUserActive(long id,boolean active){sendClass(base("/api/admin/users/"+id+"/active?value="+active).POST(HttpRequest.BodyPublishers.noBody()).build(),Map.class);}
    public ClientModels.ImportResult importStudents(Path file){return importCsv("/api/admin/users/import",file);}

    public long createBook(Map<String,Object> data){Map<?,?> r=sendClass(base("/api/admin/books").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(data))).build(),Map.class);return ((Number)r.get("id")).longValue();}
    public String addCopy(long bookId,String barcode){var r=sendType(base("/api/admin/books/"+bookId+"/copies").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body(Map.of("barcode",barcode)))).build(),new TypeReference<Map<String,Object>>(){});return String.valueOf(r.get("barcode"));}
    public ClientModels.ImportResult importBooks(Path file){return importCsv("/api/admin/books/import",file);}
    public ClientModels.ImportResult importCopies(Path file){return importCsv("/api/admin/copies/import",file);}
    public List<ClientModels.AdminLoanView> adminLoans(){return sendType(base("/api/admin/loans?limit=300").GET().build(),new TypeReference<>(){});}
    public List<ClientModels.AuditView> activity(){return sendType(base("/api/admin/activity?limit=300").GET().build(),new TypeReference<>(){});}

    public byte[] exportCsv(){
        try{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(config.server()+"/api/admin/books/export")).header("Accept","text/csv").header("X-Workstation-Id",config.workstationId());if(SessionContext.token()!=null)b.header("X-Session-Token",SessionContext.token());HttpResponse<byte[]> r=http.send(b.GET().build(),HttpResponse.BodyHandlers.ofByteArray());if(r.statusCode()>=400)throw new IllegalStateException("Export failed");return r.body();}catch(Exception e){throw e instanceof RuntimeException?(RuntimeException)e:new IllegalStateException(e);}
    }

    private ClientModels.ImportResult importCsv(String endpoint,Path file){
        try{
            String boundary="----IntelliLib"+System.nanoTime();
            byte[] data=java.nio.file.Files.readAllBytes(file);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+file.getFileName()+"\"\r\nContent-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(data);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            return sendClass(base(endpoint).timeout(Duration.ofMinutes(10)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),ClientModels.ImportResult.class);
        }catch(Exception e){throw e instanceof RuntimeException?(RuntimeException)e:new IllegalStateException(e);}
    }

    private static String enc(String s){return java.net.URLEncoder.encode(s,StandardCharsets.UTF_8);}
}
