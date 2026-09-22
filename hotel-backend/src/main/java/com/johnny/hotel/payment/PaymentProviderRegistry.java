package com.johnny.hotel.payment;

import com.johnny.hotel.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PaymentProviderRegistry {
    private final Map<String,PaymentProvider> providers;
    public PaymentProviderRegistry(List<PaymentProvider> values){var map=new HashMap<String,PaymentProvider>();values.forEach(p->map.put(p.id(),p));providers=Map.copyOf(map);}
    public PaymentProvider require(String id){String key=id==null?"":id.trim().toUpperCase(Locale.ROOT);var result=providers.get(key);if(result==null)throw new BusinessException("Payment provider is unsupported or not configured");return result;}
}
