package com.intellilib.server.bootstrap;

import com.intellilib.server.config.AppConfig.AppSettings;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@Order(0)
public class ProductionGuard implements ApplicationRunner {
    private final AppSettings settings;
    public ProductionGuard(AppSettings settings){this.settings=settings;}
    @Override public void run(ApplicationArguments args){
        boolean render=System.getenv("RENDER")!=null;
        if(!render)return;
        if(settings.cardPepper()==null||settings.cardPepper().length()<24||settings.cardPepper().contains("change-me")||settings.cardPepper().contains("local-development"))
            throw new IllegalStateException("Set a strong APP_CARD_PEPPER secret before production deployment.");
        if(settings.adminPassword()==null||settings.adminPassword().length()<12||"Admin@12345".equals(settings.adminPassword()))
            throw new IllegalStateException("Set a strong APP_BOOTSTRAP_ADMIN_PASSWORD secret before production deployment.");
    }
}
