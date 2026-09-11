package com.johnny.hotel.support;

import org.springframework.context.ConfigurableApplicationContext;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** Opt-in development fixture. It never invokes the destructive isolated fixture. */
public final class WalletDevelopmentGuard {
    private WalletDevelopmentGuard() {}
    public static Map<String,String> settings() {
        try {
            Map<String,String> values=new HashMap<>();
            var factory=DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            var document=factory.newDocumentBuilder().parse(Path.of("../../.idea/workspace.xml").toFile());
            var configs=document.getElementsByTagName("configuration");
            for(int i=0;i<configs.getLength();i++) {
                var config=(org.w3c.dom.Element)configs.item(i);
                if(!"HotelBackendApplication".equals(config.getAttribute("name"))) continue;
                var envs=config.getElementsByTagName("env");
                for(int j=0;j<envs.getLength();j++){var e=(org.w3c.dom.Element)envs.item(j);values.put(e.getAttribute("name"),e.getAttribute("value"));}
            }
            for(String key:List.of("DB_URL","DB_USERNAME","DB_PASSWORD","REDIS_PASSWORD")) {
                if(System.getenv(key)!=null) values.put(key,System.getenv(key));
                if(!values.containsKey(key)) throw new IllegalStateException("Missing variable");
            }
            return values;
        }catch(Exception e){throw new IllegalStateException("Development test configuration unavailable (credentials are never logged)");}
    }
    public static String url() {
        String url=settings().get("DB_URL");
        if(!url.equals("jdbc:mysql://localhost:3306/hotel_management")) throw new IllegalStateException("Unexpected development database identity");
        return url+"?useUnicode=true&characterEncoding=UTF-8&serverTimezone=America/Toronto";
    }
    public static void verify(ConfigurableApplicationContext context) {
        if(!Boolean.getBoolean("hotel.wallet.dev.tests")) throw new IllegalStateException("Wallet development tests require explicit opt-in");
        var env=context.getEnvironment();
        if(!url().equals(env.getProperty("spring.datasource.url")) || env.getProperty("spring.flyway.url")!=null)
            throw new IllegalStateException("Unexpected Wallet test datasource");
        try(var c=DriverManager.getConnection(url(),env.getProperty("spring.datasource.username"),env.getProperty("spring.datasource.password"));
            var s=c.createStatement();var r=s.executeQuery("SELECT DATABASE(),@@port")) {
            r.next();if(!"hotel_management".equals(r.getString(1)) || r.getInt(2)!=3306) throw new IllegalStateException("Wrong development database");
        }catch(java.sql.SQLException e){throw new IllegalStateException("Development database read-only verification failed");}
    }
}
