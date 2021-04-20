package com.amplitude;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import com.amplitude.Amplitude;
@RestController
public class DemoController {
	@RequestMapping("/")
	public String index() {
		Amplitude amplitude = Amplitude.getInstance("INSTANCE_NAME");
		amplitude.init("8e07b9d451a7d07bd33f6e9ba5870f21");
		amplitude.logEvent("Test Event");
		return "Amplitude Java SDK Demo: sending test event.";
	}
}