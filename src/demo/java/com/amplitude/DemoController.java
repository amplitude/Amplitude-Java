package com.amplitude;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import com.amplitude.Amplitude;
@RestController
public class DemoController {
	@RequestMapping("/")
	public String index() {
		Amplitude amplitude = Amplitude.getInstance("INSTANCE_NAME");
		amplitude.init("bcfd68add3ac9532f2f0920bdee7f9e2");
		amplitude.logEvent("Event name test");
		return "Amplitude Java SDK Demo: sending test event.";
	}
}