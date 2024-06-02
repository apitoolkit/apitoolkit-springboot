package io.apitoolkit.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import io.apitoolkit.springboot.annotations.EnableAPIToolkit;

@EnableAPIToolkit
@SpringBootApplication
@RestController

public class Demo {

    public static void main(String[] args) {
        SpringApplication.run(Demo.class, args);
    }

    @GetMapping("/hello")
    public String hello(@RequestParam(value = "name", defaultValue = "World") String name, HttpServletRequest request) {

        return String.format("Hello %s!", name);
    }
}
