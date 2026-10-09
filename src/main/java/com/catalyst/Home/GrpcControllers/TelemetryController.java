package com.catalyst.Home.GrpcControllers;

import com.catalyst.Home.DTO.response.GrpcResponse;
import com.catalyst.Home.Services.TelemetryService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;
import telemetry.Gps;
import telemetry.Sensor;
import telemetry.Summary;
import telemetry.TelemetryServiceGrpc;
import telemetry.Vegetation;

@GrpcService
public class TelemetryController extends TelemetryServiceGrpc.TelemetryServiceImplBase {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @Override
    public void recordSensorReading(Sensor.RecordSensorReadingRequest request, StreamObserver<Sensor.RecordSensorReadingResponse> responseObserver) {
        respond(telemetryService.recordSensorReading(request), responseObserver);
    }

    @Override
    public void getSensorReadings(Sensor.GetSensorReadingsRequest request, StreamObserver<Sensor.GetSensorReadingsResponse> responseObserver) {
        respond(telemetryService.getSensorReadings(request), responseObserver);
    }

    @Override
    public void getLatestSensorReading(Sensor.GetLatestSensorReadingRequest request, StreamObserver<Sensor.GetLatestSensorReadingResponse> responseObserver) {
        respond(telemetryService.getLatestSensorReading(request), responseObserver);
    }

    @Override
    public void recordGpsLocation(Gps.RecordGpsLocationRequest request, StreamObserver<Gps.RecordGpsLocationResponse> responseObserver) {
        respond(telemetryService.recordGpsLocation(request), responseObserver);
    }

    @Override
    public void getGpsHistory(Gps.GetGpsHistoryRequest request, StreamObserver<Gps.GetGpsHistoryResponse> responseObserver) {
        respond(telemetryService.getGpsHistory(request), responseObserver);
    }

    @Override
    public void getLatestGpsLocation(Gps.GetLatestGpsLocationRequest request, StreamObserver<Gps.GetLatestGpsLocationResponse> responseObserver) {
        respond(telemetryService.getLatestGpsLocation(request), responseObserver);
    }

    @Override
    public void recordVegetationIndex(Vegetation.RecordVegetationIndexRequest request, StreamObserver<Vegetation.RecordVegetationIndexResponse> responseObserver) {
        respond(telemetryService.recordVegetationIndex(request), responseObserver);
    }

    @Override
    public void getVegetationHistory(Vegetation.GetVegetationHistoryRequest request, StreamObserver<Vegetation.GetVegetationHistoryResponse> responseObserver) {
        respond(telemetryService.getVegetationHistory(request), responseObserver);
    }

    @Override
    public void getLatestVegetationIndex(Vegetation.GetLatestVegetationIndexRequest request, StreamObserver<Vegetation.GetLatestVegetationIndexResponse> responseObserver) {
        respond(telemetryService.getLatestVegetationIndex(request), responseObserver);
    }

    @Override
    public void getFarmTelemetrySummary(Summary.GetFarmTelemetrySummaryRequest request, StreamObserver<Summary.GetFarmTelemetrySummaryResponse> responseObserver) {
        respond(telemetryService.getFarmTelemetrySummary(request), responseObserver);
    }

    private <T> void respond(GrpcResponse<T> grpcResponse, StreamObserver<T> responseObserver) {
        if (!grpcResponse.getCode().equals("200")) {
            Status status = Status.fromCode(grpcResponse.getStatus()).withDescription(grpcResponse.getMessage());
            StatusRuntimeException statusException = status.asRuntimeException();
            responseObserver.onError(statusException);
            return;
        }
        responseObserver.onNext(grpcResponse.getData());
        responseObserver.onCompleted();
    }
}
