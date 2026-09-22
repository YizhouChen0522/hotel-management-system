package com.johnny.hotel.payment;

import java.util.*;

public record RawPaymentWebhook(Map<String,List<String>> headers,byte[] body) {
    public RawPaymentWebhook {
        headers=headers==null?Map.of():headers.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,e->List.copyOf(e.getValue())));
        body=body==null?new byte[0]:body.clone();
    }
    public String firstHeader(String name){return headers.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).flatMap(e->e.getValue().stream()).findFirst().orElse(null);}
    @Override public byte[] body(){return body.clone();}
}
