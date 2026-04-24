package com.smartcampus.application;

import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton in-memory data store.
 * Because JAX-RS creates a new resource class instance per request by default,
 * we centralise all shared state here using thread-safe ConcurrentHashMap and
 * CopyOnWriteArrayList structures to prevent race conditions.
 */
public class DataStore {

    private static final DataStore INSTANCE = new DataStore();

    // ConcurrentHashMap gives us thread-safe read/write without explicit synchronization
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, Sensor> sensors = new ConcurrentHashMap<>();
    // Reading history keyed by sensorId
    private final Map<String, List<SensorReading>> sensorReadings = new ConcurrentHashMap<>();

    private DataStore() {
        // Seed some demo data
        Room r1 = new Room("LIB-301", "Library Quiet Study", 40);
        Room r2 = new Room("LAB-101", "Computer Science Lab", 30);
        rooms.put(r1.getId(), r1);
        rooms.put(r2.getId(), r2);

        Sensor s1 = new Sensor("TEMP-001", "Temperature", "ACTIVE", 22.5, "LIB-301");
        Sensor s2 = new Sensor("CO2-001", "CO2", "ACTIVE", 412.0, "LAB-101");
        Sensor s3 = new Sensor("OCC-001", "Occupancy", "MAINTENANCE", 0.0, "LIB-301");
        sensors.put(s1.getId(), s1);
        sensors.put(s2.getId(), s2);
        sensors.put(s3.getId(), s3);

        r1.getSensorIds().add("TEMP-001");
        r1.getSensorIds().add("OCC-001");
        r2.getSensorIds().add("CO2-001");

        sensorReadings.put("TEMP-001", new CopyOnWriteArrayList<>());
        sensorReadings.put("CO2-001", new CopyOnWriteArrayList<>());
        sensorReadings.put("OCC-001", new CopyOnWriteArrayList<>());
    }

    public static DataStore getInstance() {
        return INSTANCE;
    }

    public Map<String, Room> getRooms() { return rooms; }
    public Map<String, Sensor> getSensors() { return sensors; }

    public List<SensorReading> getReadingsForSensor(String sensorId) {
        return sensorReadings.computeIfAbsent(sensorId, k -> new CopyOnWriteArrayList<>());
    }

    public void addReading(String sensorId, SensorReading reading) {
        getReadingsForSensor(sensorId).add(reading);
    }
}
