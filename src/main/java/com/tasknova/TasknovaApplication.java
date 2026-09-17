package com.tasknova;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * TaskNova — Task Management System
 * Entry point for the Spring Boot application.
 */
@SpringBootApplication
@EnableAspectJAutoProxy
@EnableAsync
public class TasknovaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TasknovaApplication.class, args);
    }
}
