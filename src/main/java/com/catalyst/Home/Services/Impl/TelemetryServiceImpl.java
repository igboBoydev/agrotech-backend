package com.catalyst.Home.Services.Impl;

import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Entity.Farms;
import com.catalyst.Home.Entity.GpsLocation;
import com.catalyst.Home.Entity.SensorReading;
import com.catalyst.Home.Entity.VegetationIndex;
import com.catalyst.Home.Repositories.FarmsRepository;
import com.catalyst.Home.Repositories.GpsLocationRepository;
import com.catalyst.Home.Repositories.SensorReadingRepository;
import com.catalyst.Home.Repositories.VegetationIndexRepository;
import com.catalyst.Home.Services.TelemetryService;
import com.catalyst.Home.Utils.DataSource;
import com.catalyst.Home.Utils.SensorType;
import com.catalyst.Home.Utils.VegetationHealthBand;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import telemetry.Gps;
import telemetry.Sensor;
import telemetry.Summary;
import telemetry.Vegetation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelemetryServiceImpl implements TelemetryService {
    private final FarmsRepository farmsRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final GpsLocationRepository gpsLocationRepository;
    private final VegetationIndexRepository vegetationIndexRepository;

    @Override
    @Transactional
    public GrpcResponse<Sensor.RecordSensorReadingResponse> recordSensorReading(Sensor.RecordSensorReadingRequest request) {
        Optional<Farms> farmOpt = farmsRepository.findByUuid(request.getFarmId());
        if (farmOpt.isEmpty()) {
            return GrpcResponse.<Sensor.RecordSensorReadingResponse>builder()
                    .code("404").message("Farm not found").status(Status.Code.NOT_FOUND).build();
        }

        SensorReading reading = sensorReadingRepository.save(SensorReading.builder()
                .uuid(UUID.randomUUID().toString())
                .farm(farmOpt.get())
                .sensorType(mapSensorType(request.getSensorType()))
                .value(request.getValue())
                .unit(request.getUnit())
                .source(mapDataSource(request.getSource()))
                .recordedAt(defaultToToday(request.getRecordedAt()))
                .build());

        Sensor.RecordSensorReadingResponse response = Sensor.RecordSensorReadingResponse.newBuilder()
                .setCode("200")
                .setMessage("Sensor reading recorded successfully")
                .setReading(toSensorReadingProto(reading))
                .build();

        return GrpcResponse.<Sensor.RecordSensorReadingResponse>builder()
                .code("200").message("Sensor reading recorded successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Sensor.GetSensorReadingsResponse> getSensorReadings(Sensor.GetSensorReadingsRequest request) {
        List<SensorReading> readings = sensorReadingRepository.findByFarm_UuidAndSensorTypeOrderByIdDesc(
                request.getFarmId(), mapSensorType(request.getSensorType()));

        Sensor.GetSensorReadingsResponse response = Sensor.GetSensorReadingsResponse.newBuilder()
                .addAllReadings(readings.stream().map(this::toSensorReadingProto).toList())
                .build();

        return GrpcResponse.<Sensor.GetSensorReadingsResponse>builder()
                .code("200").message("Sensor readings retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Sensor.GetLatestSensorReadingResponse> getLatestSensorReading(Sensor.GetLatestSensorReadingRequest request) {
        Optional<SensorReading> readingOpt = sensorReadingRepository.findTopByFarm_UuidAndSensorTypeOrderByIdDesc(
                request.getFarmId(), mapSensorType(request.getSensorType()));
        if (readingOpt.isEmpty()) {
            return GrpcResponse.<Sensor.GetLatestSensorReadingResponse>builder()
                    .code("404").message("No reading recorded yet for this sensor type").status(Status.Code.NOT_FOUND).build();
        }

        Sensor.GetLatestSensorReadingResponse response = Sensor.GetLatestSensorReadingResponse.newBuilder()
                .setCode("200")
                .setMessage("Latest sensor reading retrieved successfully")
                .setReading(toSensorReadingProto(readingOpt.get()))
                .build();

        return GrpcResponse.<Sensor.GetLatestSensorReadingResponse>builder()
                .code("200").message("Latest sensor reading retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Gps.RecordGpsLocationResponse> recordGpsLocation(Gps.RecordGpsLocationRequest request) {
        Optional<Farms> farmOpt = farmsRepository.findByUuid(request.getFarmId());
        if (farmOpt.isEmpty()) {
            return GrpcResponse.<Gps.RecordGpsLocationResponse>builder()
                    .code("404").message("Farm not found").status(Status.Code.NOT_FOUND).build();
        }

        GpsLocation location = gpsLocationRepository.save(GpsLocation.builder()
                .uuid(UUID.randomUUID().toString())
                .farm(farmOpt.get())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracyMeters(request.getAccuracyMeters())
                .recordedAt(defaultToToday(request.getRecordedAt()))
                .build());

        Gps.RecordGpsLocationResponse response = Gps.RecordGpsLocationResponse.newBuilder()
                .setCode("200")
                .setMessage("GPS location recorded successfully")
                .setLocation(toGpsLocationProto(location))
                .build();

        return GrpcResponse.<Gps.RecordGpsLocationResponse>builder()
                .code("200").message("GPS location recorded successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Gps.GetGpsHistoryResponse> getGpsHistory(Gps.GetGpsHistoryRequest request) {
        List<GpsLocation> locations = gpsLocationRepository.findByFarm_UuidOrderByIdDesc(request.getFarmId());

        Gps.GetGpsHistoryResponse response = Gps.GetGpsHistoryResponse.newBuilder()
                .addAllLocations(locations.stream().map(this::toGpsLocationProto).toList())
                .build();

        return GrpcResponse.<Gps.GetGpsHistoryResponse>builder()
                .code("200").message("GPS history retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Gps.GetLatestGpsLocationResponse> getLatestGpsLocation(Gps.GetLatestGpsLocationRequest request) {
        Optional<GpsLocation> locationOpt = gpsLocationRepository.findTopByFarm_UuidOrderByIdDesc(request.getFarmId());
        if (locationOpt.isEmpty()) {
            return GrpcResponse.<Gps.GetLatestGpsLocationResponse>builder()
                    .code("404").message("No GPS location recorded yet").status(Status.Code.NOT_FOUND).build();
        }

        Gps.GetLatestGpsLocationResponse response = Gps.GetLatestGpsLocationResponse.newBuilder()
                .setCode("200")
                .setMessage("Latest GPS location retrieved successfully")
                .setLocation(toGpsLocationProto(locationOpt.get()))
                .build();

        return GrpcResponse.<Gps.GetLatestGpsLocationResponse>builder()
                .code("200").message("Latest GPS location retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Vegetation.RecordVegetationIndexResponse> recordVegetationIndex(Vegetation.RecordVegetationIndexRequest request) {
        Optional<Farms> farmOpt = farmsRepository.findByUuid(request.getFarmId());
        if (farmOpt.isEmpty()) {
            return GrpcResponse.<Vegetation.RecordVegetationIndexResponse>builder()
                    .code("404").message("Farm not found").status(Status.Code.NOT_FOUND).build();
        }

        VegetationIndex index = vegetationIndexRepository.save(VegetationIndex.builder()
                .uuid(UUID.randomUUID().toString())
                .farm(farmOpt.get())
                .ndviValue(request.getNdviValue())
                .healthBand(bandForNdvi(request.getNdviValue()))
                .source(mapDataSource(request.getSource()))
                .capturedAt(defaultToToday(request.getCapturedAt()))
                .build());

        Vegetation.RecordVegetationIndexResponse response = Vegetation.RecordVegetationIndexResponse.newBuilder()
                .setCode("200")
                .setMessage("Vegetation index recorded successfully")
                .setIndex(toVegetationIndexProto(index))
                .build();

        return GrpcResponse.<Vegetation.RecordVegetationIndexResponse>builder()
                .code("200").message("Vegetation index recorded successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Vegetation.GetVegetationHistoryResponse> getVegetationHistory(Vegetation.GetVegetationHistoryRequest request) {
        List<VegetationIndex> indices = vegetationIndexRepository.findByFarm_UuidOrderByIdDesc(request.getFarmId());

        Vegetation.GetVegetationHistoryResponse response = Vegetation.GetVegetationHistoryResponse.newBuilder()
                .addAllIndices(indices.stream().map(this::toVegetationIndexProto).toList())
                .build();

        return GrpcResponse.<Vegetation.GetVegetationHistoryResponse>builder()
                .code("200").message("Vegetation history retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Vegetation.GetLatestVegetationIndexResponse> getLatestVegetationIndex(Vegetation.GetLatestVegetationIndexRequest request) {
        Optional<VegetationIndex> indexOpt = vegetationIndexRepository.findTopByFarm_UuidOrderByIdDesc(request.getFarmId());
        if (indexOpt.isEmpty()) {
            return GrpcResponse.<Vegetation.GetLatestVegetationIndexResponse>builder()
                    .code("404").message("No vegetation index recorded yet").status(Status.Code.NOT_FOUND).build();
        }

        Vegetation.GetLatestVegetationIndexResponse response = Vegetation.GetLatestVegetationIndexResponse.newBuilder()
                .setCode("200")
                .setMessage("Latest vegetation index retrieved successfully")
                .setIndex(toVegetationIndexProto(indexOpt.get()))
                .build();

        return GrpcResponse.<Vegetation.GetLatestVegetationIndexResponse>builder()
                .code("200").message("Latest vegetation index retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Summary.GetFarmTelemetrySummaryResponse> getFarmTelemetrySummary(Summary.GetFarmTelemetrySummaryRequest request) {
        Optional<Farms> farmOpt = farmsRepository.findByUuid(request.getFarmId());
        if (farmOpt.isEmpty()) {
            return GrpcResponse.<Summary.GetFarmTelemetrySummaryResponse>builder()
                    .code("404").message("Farm not found").status(Status.Code.NOT_FOUND).build();
        }
        String farmUuid = farmOpt.get().getUuid();

        Summary.FarmTelemetrySummary.Builder summaryBuilder = Summary.FarmTelemetrySummary.newBuilder()
                .setFarmId(farmUuid);

        gpsLocationRepository.findTopByFarm_UuidOrderByIdDesc(farmUuid)
                .ifPresent(location -> summaryBuilder.setLatestGps(toGpsLocationProto(location)));

        vegetationIndexRepository.findTopByFarm_UuidOrderByIdDesc(farmUuid)
                .ifPresent(index -> summaryBuilder.setLatestVegetation(toVegetationIndexProto(index)));

        for (SensorType type : SensorType.values()) {
            sensorReadingRepository.findTopByFarm_UuidAndSensorTypeOrderByIdDesc(farmUuid, type)
                    .ifPresent(reading -> summaryBuilder.addLatestReadings(toSensorReadingProto(reading)));
        }

        Summary.GetFarmTelemetrySummaryResponse response = Summary.GetFarmTelemetrySummaryResponse.newBuilder()
                .setCode("200")
                .setMessage("Telemetry summary retrieved successfully")
                .setSummary(summaryBuilder.build())
                .build();

        return GrpcResponse.<Summary.GetFarmTelemetrySummaryResponse>builder()
                .code("200").message("Telemetry summary retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    private static VegetationHealthBand bandForNdvi(double ndvi) {
        if (ndvi < 0.2) return VegetationHealthBand.VEGETATION_POOR;
        if (ndvi < 0.4) return VegetationHealthBand.VEGETATION_MODERATE;
        if (ndvi < 0.6) return VegetationHealthBand.VEGETATION_GOOD;
        return VegetationHealthBand.VEGETATION_EXCELLENT;
    }

    private static String defaultToToday(String value) {
        return (value == null || value.isBlank()) ? LocalDate.now().toString() : value;
    }

    private Sensor.SensorReading toSensorReadingProto(SensorReading reading) {
        return Sensor.SensorReading.newBuilder()
                .setId(nullToEmpty(reading.getUuid()))
                .setFarmId(reading.getFarm() != null ? nullToEmpty(reading.getFarm().getUuid()) : "")
                .setSensorType(mapSensorType(reading.getSensorType()))
                .setValue(reading.getValue() == null ? 0 : reading.getValue())
                .setUnit(nullToEmpty(reading.getUnit()))
                .setSource(mapDataSource(reading.getSource()))
                .setRecordedAt(nullToEmpty(reading.getRecordedAt()))
                .build();
    }

    private Gps.GpsLocation toGpsLocationProto(GpsLocation location) {
        return Gps.GpsLocation.newBuilder()
                .setId(nullToEmpty(location.getUuid()))
                .setFarmId(location.getFarm() != null ? nullToEmpty(location.getFarm().getUuid()) : "")
                .setLatitude(location.getLatitude() == null ? 0 : location.getLatitude())
                .setLongitude(location.getLongitude() == null ? 0 : location.getLongitude())
                .setAccuracyMeters(location.getAccuracyMeters() == null ? 0 : location.getAccuracyMeters())
                .setRecordedAt(nullToEmpty(location.getRecordedAt()))
                .build();
    }

    private Vegetation.VegetationIndex toVegetationIndexProto(VegetationIndex index) {
        return Vegetation.VegetationIndex.newBuilder()
                .setId(nullToEmpty(index.getUuid()))
                .setFarmId(index.getFarm() != null ? nullToEmpty(index.getFarm().getUuid()) : "")
                .setNdviValue(index.getNdviValue() == null ? 0 : index.getNdviValue())
                .setHealthBand(mapVegetationHealthBand(index.getHealthBand()))
                .setSource(mapDataSource(index.getSource()))
                .setCapturedAt(nullToEmpty(index.getCapturedAt()))
                .build();
    }

    private static telemetry.Enum.SensorType mapSensorType(SensorType type) {
        return telemetry.Enum.SensorType.valueOf(type.name());
    }

    private static SensorType mapSensorType(telemetry.Enum.SensorType type) {
        return SensorType.valueOf(type.name());
    }

    private static telemetry.Enum.DataSource mapDataSource(DataSource source) {
        return telemetry.Enum.DataSource.valueOf(source.name());
    }

    private static DataSource mapDataSource(telemetry.Enum.DataSource source) {
        return DataSource.valueOf(source.name());
    }

    private static telemetry.Enum.VegetationHealthBand mapVegetationHealthBand(VegetationHealthBand band) {
        return telemetry.Enum.VegetationHealthBand.valueOf(band.name());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
