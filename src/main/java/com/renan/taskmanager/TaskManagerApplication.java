package com.renan.taskmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da Task Manager API.
 *
 * <p>API REST para gestão de tarefas com autenticação JWT, construída com
 * Java 21 e Spring Boot 3.</p>
 */
@SpringBootApplication
public class TaskManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskManagerApplication.class, args);
    }
}
