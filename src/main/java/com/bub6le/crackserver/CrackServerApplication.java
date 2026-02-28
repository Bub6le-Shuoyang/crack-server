package com.bub6le.crackserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.bub6le.crackserver.mapper")
public class CrackServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrackServerApplication.class, args);
    }

}
