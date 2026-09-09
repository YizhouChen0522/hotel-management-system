package com.johnny.hotel;

import com.johnny.hotel.support.RedisMysqlTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=HotelBackendApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"debug=false","logging.level.org.springframework=WARN","logging.level.com.johnny=WARN",
                "logging.level.org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration=ERROR"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.mysql.tests",matches="true")
class RedisHttpStartupTest extends RedisMysqlTest {
    @Value("${local.server.port}") int port;
    @Test void realHttpServerStartsAndPreservesHealthAndSecurityWhileLimitingLogin()throws Exception{
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        String base="http://127.0.0.1:"+port;
        var health=client.send(HttpRequest.newBuilder(URI.create(base+"/api/health")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,health.statusCode());assertTrue(health.body().contains("Backend is running"));
        for(int i=0;i<4;i++){
            var request=HttpRequest.newBuilder(URI.create(base+"/api/auth/login")).header("Content-Type","application/json")
                    .header("X-Forwarded-For","192.0.2."+i).POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            var result=client.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(i<3?400:429,result.statusCode());
        }
        var protectedResult=client.send(HttpRequest.newBuilder(URI.create(base+"/api/admin/room-types")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,protectedResult.statusCode());
    }
}
