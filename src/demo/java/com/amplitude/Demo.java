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
		SpringApplication.run(Demo.class, args);
	}
	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			System.out.println("Let's inspect the beans provided by Spring Boot:");
			Amplitude amplitude = Amplitude.getInstance("INSTANCE_NAME");
			amplitude.init("bcfd68add3ac9532f2f0920bdee7f9e2");
			amplitude.logEvent("Event name test");
			String[] beanNames = ctx.getBeanDefinitionNames();
			Arrays.sort(beanNames);
			for (String beanName : beanNames) {
				System.out.println(beanName);
			}
		};
	}
}