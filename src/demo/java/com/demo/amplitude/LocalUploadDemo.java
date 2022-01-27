package com.demo.amplitude;

import com.amplitude.Amplitude;
import com.amplitude.AmplitudeLog;
import com.amplitude.Event;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

public class LocalUploadDemo {
    public static void main(String[] args) throws InterruptedException{
        Amplitude client = Amplitude.getInstance();
        client.init("44270dfba966dafe46554de66e088877");
        client.useBatchMode(true);
        client.setLogMode(AmplitudeLog.LogMode.DEBUG);
        client.setEventUploadThreshold(1500);
        for (int i = 0; i < 10000000; i++){
            while (client.shouldWait()) {
                System.out.println("Wait for flushing events");
                TimeUnit.SECONDS.sleep(5L);
            }
            Event ampEvent = new Event("General"+ (i % 20), "Test_UserID_B");
            ampEvent.userProperties = new JSONObject()
                    .put("property1", "p"+i)
                    .put("property2", "p"+i)
                    .put("property3", "p"+i)
                    .put("property4", "p"+i)
                    .put("property5", "p"+i);
            client.logEvent(ampEvent);
        }
    }
}
