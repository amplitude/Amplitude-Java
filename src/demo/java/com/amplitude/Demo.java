package com.amplitude;

import java.util.Arrays;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Demo {
	public static void main(String[] args) {
		System.out.println("Running Amplitude Java Demo");
		SpringApplication.run(Demo.class, args);
	}
}