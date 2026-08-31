package com.charlie.ikibaho;

import org.springframework.boot.SpringApplication;

public class TestIkibahoApplication {
    public static void main(String[] args) {
        SpringApplication.from(IkibahoApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
