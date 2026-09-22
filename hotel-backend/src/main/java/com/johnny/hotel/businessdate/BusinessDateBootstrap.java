package com.johnny.hotel.businessdate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class BusinessDateBootstrap implements ApplicationRunner {private final BusinessDateService service;public void run(ApplicationArguments args){service.initializeOnce();}}
