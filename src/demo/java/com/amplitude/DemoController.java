package com.amplitude;

import org.json.JSONObject;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import com.amplitude.Amplitude;

@RestController
public class DemoController {
	@RequestMapping("/")
	public String index() {
		Amplitude amplitude = Amplitude.getInstance("INSTANCE_NAME");
		amplitude.init("378fc916942fe9bee9d2dbd599508c6c");
		amplitude.setLogMode(AmplitudeLog.LogMode.DEBUG);

		for (int i = 0; i < 15; i++) {
			Event event = new Event("Button Clicked " + i, "test_user_for_demo");
			event.eventProperties = new JSONObject()
					.put("favorite_color", "red")
					.put("special_data", new double[]{1, 2, 4, 8, 16});
			amplitude.logEvent(event);
		}

		return "Amplitude Java SDK Demo: sending test event. " + System.currentTimeMillis();
	}
}
