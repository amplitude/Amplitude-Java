package com.demo.amplitude;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Demo {
  public static void main(String[] args) {
    System.out.println("Running Amplitude Java Demo");
    SpringApplication.run(Demo.class, args);
  }
}
