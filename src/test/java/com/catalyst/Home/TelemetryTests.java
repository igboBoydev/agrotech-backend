package com.catalyst.Home;

import com.catalyst.Home.DTO.response.GrpcResponse;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.FarmsRepository;
import com.catalyst.Home.Repositories.GpsLocationRepository;
import com.catalyst.Home.Repositories.SensorReadingRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Repositories.VegetationIndexRepository;
import com.catalyst.Home.Services.OnboardingService;
import com.catalyst.Home.Services.TelemetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import telemetry.Gps;
import telemetry.Sensor;
import telemetry.Summary;
import telemetry.Vegetation;
import users.Farmers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TelemetryTests {

	private static final String TEST_EMAIL = "ngozi.eze.telemetry@example.com";

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private TelemetryService telemetryService;

	@Autowired
	private FarmerRepository farmerRepository;

	@Autowired
	private UsersRepository usersRepository;

	@Autowired
	private FarmsRepository farmsRepository;

	@Autowired
	private SensorReadingRepository sensorReadingRepository;

	@Autowired
	private GpsLocationRepository gpsLocationRepository;

	@Autowired
	private VegetationIndexRepository vegetationIndexRepository;

	@AfterEach
	void cleanUp() {
		farmerRepository.findByEmail(TEST_EMAIL).ifPresent(farmer -> {
			farmsRepository.findByFarmers_Uuid(farmer.getUuid()).forEach(farm -> {
				sensorReadingRepository.findAll().stream()
						.filter(r -> r.getFarm() != null && farm.getUuid().equals(r.getFarm().getUuid()))
						.forEach(sensorReadingRepository::delete);
				gpsLocationRepository.findByFarm_UuidOrderByIdDesc(farm.getUuid()).forEach(gpsLocationRepository::delete);
				vegetationIndexRepository.findByFarm_UuidOrderByIdDesc(farm.getUuid()).forEach(vegetationIndexRepository::delete);
				farmsRepository.delete(farm);
			});
			farmerRepository.delete(farmer);
		});
		usersRepository.findByEmail(TEST_EMAIL).ifPresent(usersRepository::delete);
	}

	private String createTestFarm() {
		onboardingService.createFarmer(Farmers.CreateFarmerRequest.newBuilder()
				.setFirstName("Ngozi")
				.setLastName("Eze")
				.setDob("1988-09-15")
				.setHomeAddress("7 Palm Avenue")
				.setLga("Enugu")
				.setState("Enugu")
				.setCountry("Nigeria")
				.setEmail(TEST_EMAIL)
				.setPhone("+2348099990000")
				.setPassword("s3cret-pw")
				.build());
		String farmerUuid = farmerRepository.findByEmail(TEST_EMAIL).orElseThrow().getUuid();

		onboardingService.addFarm(Farmers.AddFarmRequest.newBuilder()
				.setUserId(farmerUuid)
				.setAddress("2 Field Road")
				.setLga("Enugu")
				.setState("Enugu")
				.setCountry("Nigeria")
				.setLandSize("4 acres")
				.setIsLease(false)
				.setIsOwnProperty(true)
				.setPlantType("Cassava")
				.setFarmType("Crop")
				.build());
		return farmsRepository.findByFarmers_Uuid(farmerUuid).stream().findFirst().orElseThrow().getUuid();
	}

	@Test
	void sensorAndGps_recordAndRetrieveLatest() {
		String farmUuid = createTestFarm();

		telemetryService.recordSensorReading(Sensor.RecordSensorReadingRequest.newBuilder()
				.setFarmId(farmUuid)
				.setSensorType(telemetry.Enum.SensorType.SENSOR_SOIL_MOISTURE)
				.setValue(30.0)
				.setUnit("%")
				.setSource(telemetry.Enum.DataSource.SOURCE_SENSOR)
				.setRecordedAt("2026-01-01")
				.build());
		telemetryService.recordSensorReading(Sensor.RecordSensorReadingRequest.newBuilder()
				.setFarmId(farmUuid)
				.setSensorType(telemetry.Enum.SensorType.SENSOR_SOIL_MOISTURE)
				.setValue(35.0)
				.setUnit("%")
				.setSource(telemetry.Enum.DataSource.SOURCE_SENSOR)
				.setRecordedAt("2026-01-02")
				.build());
		telemetryService.recordSensorReading(Sensor.RecordSensorReadingRequest.newBuilder()
				.setFarmId(farmUuid)
				.setSensorType(telemetry.Enum.SensorType.SENSOR_TEMPERATURE)
				.setValue(28.0)
				.setUnit("C")
				.setSource(telemetry.Enum.DataSource.SOURCE_SENSOR)
				.setRecordedAt("2026-01-02")
				.build());

		GrpcResponse<Sensor.GetLatestSensorReadingResponse> latestMoisture = telemetryService.getLatestSensorReading(
				Sensor.GetLatestSensorReadingRequest.newBuilder()
						.setFarmId(farmUuid).setSensorType(telemetry.Enum.SensorType.SENSOR_SOIL_MOISTURE).build());
		assertThat(latestMoisture.getData().getReading().getValue()).isEqualTo(35.0);

		GrpcResponse<Sensor.GetLatestSensorReadingResponse> latestTemp = telemetryService.getLatestSensorReading(
				Sensor.GetLatestSensorReadingRequest.newBuilder()
						.setFarmId(farmUuid).setSensorType(telemetry.Enum.SensorType.SENSOR_TEMPERATURE).build());
		assertThat(latestTemp.getData().getReading().getValue()).isEqualTo(28.0);

		GrpcResponse<Sensor.GetSensorReadingsResponse> moistureHistory = telemetryService.getSensorReadings(
				Sensor.GetSensorReadingsRequest.newBuilder()
						.setFarmId(farmUuid).setSensorType(telemetry.Enum.SensorType.SENSOR_SOIL_MOISTURE).build());
		assertThat(moistureHistory.getData().getReadingsList()).hasSize(2);

		telemetryService.recordGpsLocation(Gps.RecordGpsLocationRequest.newBuilder()
				.setFarmId(farmUuid).setLatitude(6.44).setLongitude(7.51).setAccuracyMeters(5.0).setRecordedAt("2026-01-01").build());
		telemetryService.recordGpsLocation(Gps.RecordGpsLocationRequest.newBuilder()
				.setFarmId(farmUuid).setLatitude(6.45).setLongitude(7.52).setAccuracyMeters(4.0).setRecordedAt("2026-01-02").build());

		GrpcResponse<Gps.GetLatestGpsLocationResponse> latestGps = telemetryService.getLatestGpsLocation(
				Gps.GetLatestGpsLocationRequest.newBuilder().setFarmId(farmUuid).build());
		assertThat(latestGps.getData().getLocation().getLatitude()).isEqualTo(6.45);

		GrpcResponse<Gps.GetGpsHistoryResponse> gpsHistory = telemetryService.getGpsHistory(
				Gps.GetGpsHistoryRequest.newBuilder().setFarmId(farmUuid).build());
		assertThat(gpsHistory.getData().getLocationsList()).hasSize(2);
	}

	@Test
	void vegetationIndex_bandComputedAndSummaryAggregates() {
		String farmUuid = createTestFarm();

		GrpcResponse<Vegetation.RecordVegetationIndexResponse> recordResponse = telemetryService.recordVegetationIndex(
				Vegetation.RecordVegetationIndexRequest.newBuilder()
						.setFarmId(farmUuid)
						.setNdviValue(0.55)
						.setSource(telemetry.Enum.DataSource.SOURCE_SATELLITE)
						.setCapturedAt("2026-01-05")
						.build());
		assertThat(recordResponse.getCode()).isEqualTo("200");
		assertThat(recordResponse.getData().getIndex().getHealthBand()).isEqualTo(telemetry.Enum.VegetationHealthBand.VEGETATION_GOOD);

		telemetryService.recordSensorReading(Sensor.RecordSensorReadingRequest.newBuilder()
				.setFarmId(farmUuid)
				.setSensorType(telemetry.Enum.SensorType.SENSOR_HUMIDITY)
				.setValue(60.0)
				.setUnit("%")
				.setSource(telemetry.Enum.DataSource.SOURCE_SENSOR)
				.build());
		telemetryService.recordGpsLocation(Gps.RecordGpsLocationRequest.newBuilder()
				.setFarmId(farmUuid).setLatitude(6.44).setLongitude(7.51).build());

		GrpcResponse<Summary.GetFarmTelemetrySummaryResponse> summaryResponse = telemetryService.getFarmTelemetrySummary(
				Summary.GetFarmTelemetrySummaryRequest.newBuilder().setFarmId(farmUuid).build());
		assertThat(summaryResponse.getCode()).isEqualTo("200");
		Summary.FarmTelemetrySummary summary = summaryResponse.getData().getSummary();
		assertThat(summary.hasLatestGps()).isTrue();
		assertThat(summary.hasLatestVegetation()).isTrue();
		assertThat(summary.getLatestVegetation().getHealthBand()).isEqualTo(telemetry.Enum.VegetationHealthBand.VEGETATION_GOOD);
		assertThat(summary.getLatestReadingsList()).hasSize(1);
		assertThat(summary.getLatestReadingsList().get(0).getSensorType()).isEqualTo(telemetry.Enum.SensorType.SENSOR_HUMIDITY);
	}

}
