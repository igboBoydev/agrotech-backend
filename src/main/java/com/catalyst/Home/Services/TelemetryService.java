package com.catalyst.Home.Services;

import com.catalyst.Home.DTO.response.GrpcResponse;
import telemetry.Gps;
import telemetry.Sensor;
import telemetry.Summary;
import telemetry.Vegetation;

public interface TelemetryService {

    GrpcResponse<Sensor.RecordSensorReadingResponse> recordSensorReading(Sensor.RecordSensorReadingRequest request);

    GrpcResponse<Sensor.GetSensorReadingsResponse> getSensorReadings(Sensor.GetSensorReadingsRequest request);

    GrpcResponse<Sensor.GetLatestSensorReadingResponse> getLatestSensorReading(Sensor.GetLatestSensorReadingRequest request);

    GrpcResponse<Gps.RecordGpsLocationResponse> recordGpsLocation(Gps.RecordGpsLocationRequest request);

    GrpcResponse<Gps.GetGpsHistoryResponse> getGpsHistory(Gps.GetGpsHistoryRequest request);

    GrpcResponse<Gps.GetLatestGpsLocationResponse> getLatestGpsLocation(Gps.GetLatestGpsLocationRequest request);

    GrpcResponse<Vegetation.RecordVegetationIndexResponse> recordVegetationIndex(Vegetation.RecordVegetationIndexRequest request);

    GrpcResponse<Vegetation.GetVegetationHistoryResponse> getVegetationHistory(Vegetation.GetVegetationHistoryRequest request);

    GrpcResponse<Vegetation.GetLatestVegetationIndexResponse> getLatestVegetationIndex(Vegetation.GetLatestVegetationIndexRequest request);

    GrpcResponse<Summary.GetFarmTelemetrySummaryResponse> getFarmTelemetrySummary(Summary.GetFarmTelemetrySummaryRequest request);
}
