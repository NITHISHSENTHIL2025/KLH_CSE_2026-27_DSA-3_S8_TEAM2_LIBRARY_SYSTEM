package com.intellilib.client;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class RealtimeClient {
    private StompSession session;

    public void connect(AppConfig cfg,long userId,Consumer<Map<String,Object>> onUser,Consumer<Map<String,Object>> onAdmin){
        Thread t=new Thread(()->{
            try{
                WebSocketStompClient c=new WebSocketStompClient(new StandardWebSocketClient());
                c.setMessageConverter(new MappingJackson2MessageConverter());
                String wsUrl=cfg.server().replaceFirst("^http","ws")+"/ws";
                session=c.connectAsync(wsUrl,new StompSessionHandlerAdapter(){}).get(12, TimeUnit.SECONDS);
                if(onUser!=null)sub("/topic/user/"+userId,onUser);
                if(onAdmin!=null)sub("/topic/admin/requests",onAdmin);
            }catch(Exception ignored){}
        },"intellilib-realtime");
        t.setDaemon(true);t.start();
    }

    private void sub(String dest,Consumer<Map<String,Object>> cb){
        if(session==null)return;
        session.subscribe(dest,new StompFrameHandler(){
            public Type getPayloadType(StompHeaders h){return Map.class;}
            @SuppressWarnings("unchecked") public void handleFrame(StompHeaders h,Object p){cb.accept((Map<String,Object>)p);}
        });
    }

    public void close(){try{if(session!=null)session.disconnect();}catch(Exception ignored){}}
}
