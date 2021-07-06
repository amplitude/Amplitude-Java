package com.amplitude.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.amplitude.Event;

public class EventsGenerator {
  public static List<Event> generateEvents(int eventCount) {
    return generateEvents(eventCount, 1, 1);
  }

  public static List<Event> generateEvents(int eventCount, int userCount, int deviceCount) {
    List<Event> events = new ArrayList<>();
    Random rand = new Random();
    String[] users = new String[userCount];
    String[] devices = new String[deviceCount];
    for (int i = 0; i < userCount; i++) {
      users[i] = "test-user-id-" + i;
    }
    for (int i = 0; i < deviceCount; i++) {
      devices[i] = UUID.randomUUID().toString();
    }
    for (int i = 0; i < eventCount; i++) {
      events.add(
          new Event(
              "sample-type-" + i,
              users[rand.nextInt(userCount)],
              devices[rand.nextInt(deviceCount)]));
    }
    return events;
  }
}
