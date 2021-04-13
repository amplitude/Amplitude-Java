package com.amplitude;

import java.util.Arrays;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import com.amplitude.Amplitude;

@SpringBootApplication
public class Demo {
	public static void main(String[] args) {
		System.out.println("Amplitude Java Demo:");
		Amplitude amplitude = Amplitude.getInstance("INSTANCE_NAME");
		amplitude.init("bcfd68add3ac9532f2f0920bdee7f9e2");
		amplitude.logEvent("Event name test");
		SpringApplication.run(Demo.class, args);
	}
}