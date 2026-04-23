package com.smartcampus.resource;

import com.smartcampus.application.DataStore;
import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private final Sensor sensor;
    private final DataStore store;

    public SensorReadingResource(Sensor sensor, DataStore store) {
        this.sensor = sensor;
        this.store = store;
    }

    @GET
    public Response getReadings() {
        List<SensorReading> readings = store.getReadingsForSensor(sensor.getId());
        return Response.ok(readings).build();
    }

    @POST
    public Response addReading(SensorReading reading) {
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                "Sensor '" + sensor.getId() + "' is currently under MAINTENANCE " +
                "and cannot accept new readings.");
        }
        if (reading == null) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "Reading body is required.");
            return Response.status(Response.Status.BAD_REQUEST).entity(err).build();
        }
        if (reading.getId() == null || reading.getId().isBlank()) {
            reading.setId(UUID.randomUUID().toString());
        }
        if (reading.getTimestamp() == 0) {
            reading.setTimestamp(System.currentTimeMillis());
        }
        store.addReading(sensor.getId(), reading);
        sensor.setCurrentValue(reading.getValue());
        URI location = URI.create("/api/v1/sensors/" + sensor.getId() + "/readings/" + reading.getId());
        return Response.created(location).entity(reading).build();
    }
}
