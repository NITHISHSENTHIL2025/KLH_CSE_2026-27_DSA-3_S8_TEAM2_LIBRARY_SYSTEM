package com.intellilib.client;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.*;
import java.util.Properties;

public final class AppConfig {
    private final Properties p=new Properties();
    private final Path userFile=Paths.get(System.getProperty("user.home"),".intellilib","client.properties");

    public AppConfig(){
        try{
            Files.createDirectories(userFile.getParent());
            if(Files.exists(userFile)) try(InputStream in=Files.newInputStream(userFile)){p.load(in);}
            else try(InputStream in=getClass().getResourceAsStream("/intellilib.properties")){if(in!=null)p.load(in);}
        }catch(Exception ignored){}
    }

    public String server(){
        String env=System.getenv("INTELLILIB_SERVER_URL");
        String value=env!=null&&!env.isBlank()?env:p.getProperty("serverBaseUrl","http://localhost:8080");
        return value.trim().replaceAll("/$","");
    }
    public String workstationId(){
        String x=p.getProperty("workstationId","AUTO");
        if(!"AUTO".equalsIgnoreCase(x))return x;
        try{return InetAddress.getLocalHost().getHostName();}catch(Exception e){return "INTELLILIB-WORKSTATION";}
    }
    public void saveServer(String server){
        p.setProperty("serverBaseUrl",server.trim().replaceAll("/$",""));
        p.setProperty("workstationId",workstationId());
        try(OutputStream out=Files.newOutputStream(userFile)){p.store(out,"IntelliLib desktop settings");}catch(Exception ignored){}
    }
}
