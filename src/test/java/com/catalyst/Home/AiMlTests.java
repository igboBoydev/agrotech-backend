package com.catalyst.Home;

import aiml.Advisory;
import aiml.Diagnosis;
import aiml.Yield;
import com.catalyst.Home.DTO.response.GrpcResponse;
import com.catalyst.Home.Repositories.CropDiagnosisRepository;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.FarmsRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Repositories.VegetationIndexRepository;
import com.catalyst.Home.Repositories.YieldPredictionRepository;
import com.catalyst.Home.Services.AiMlService;
import com.catalyst.Home.Services.OnboardingService;
import com.catalyst.Home.Services.TelemetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import telemetry.Vegetation;
import users.Farmers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiMlTests {

	private static final String TEST_EMAIL = "tunde.okafor.aiml@example.com";

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private AiMlService aiMlService;

	@Autowired
	private TelemetryService telemetryService;

	@Autowired
	private FarmerRepository farmerRepository;

	@Autowired
	private UsersRepository usersRepository;

	@Autowired
	private FarmsRepository farmsRepository;

	@Autowired
	private CropDiagnosisRepository cropDiagnosisRepository;

	@Autowired
	private YieldPredictionRepository yieldPredictionRepository;

	@Autowired
	private VegetationIndexRepository vegetationIndexRepository;

	@AfterEach
	void cleanUp() {
		farmerRepository.findByEmail(TEST_EMAIL).ifPresent(farmer -> {
			farmsRepository.findByFarmers_Uuid(farmer.getUuid()).forEach(farm -> {
				cropDiagnosisRepository.findByFarm_UuidOrderByIdDesc(farm.getUuid()).forEach(cropDiagnosisRepository::delete);
				yieldPredictionRepository.findByFarm_UuidOrderByIdDesc(farm.getUuid()).forEach(yieldPredictionRepository::delete);
				vegetationIndexRepository.findByFarm_UuidOrderByIdDesc(farm.getUuid()).forEach(vegetationIndexRepository::delete);
				farmsRepository.delete(farm);
			});
			farmerRepository.delete(farmer);
		});
		usersRepository.findByEmail(TEST_EMAIL).ifPresent(usersRepository::delete);
	}

	private String createTestFarm() {
		onboardingService.createFarmer(Farmers.CreateFarmerRequest.newBuilder()
				.setFirstName("Tunde")
				.setLastName("Okafor")
				.setDob("1991-07-18")
				.setHomeAddress("21 Agric Road")
				.setLga("Abeokuta")
				.setState("Ogun")
				.setCountry("Nigeria")
				.setEmail(TEST_EMAIL)
				.setPhone("+2348022223333")
				.setPassword("s3cret-pw")
				.build());
		String farmerUuid = farmerRepository.findByEmail(TEST_EMAIL).orElseThrow().getUuid();

		onboardingService.addFarm(Farmers.AddFarmRequest.newBuilder()
				.setUserId(farmerUuid)
				.setAddress("5 Farm Close")
				.setLga("Abeokuta")
				.setState("Ogun")
				.setCountry("Nigeria")
				.setLandSize("5 acres")
				.setIsLease(false)
				.setIsOwnProperty(true)
				.setPlantType("Maize")
				.setFarmType("Crop")
				.build());
		return farmsRepository.findByFarmers_Uuid(farmerUuid).stream().findFirst().orElseThrow().getUuid();
	}

	@Test
	void diagnoseCropIssue_matchesRuleOrFallsBackHonestly() {
		String farmUuid = createTestFarm();

		GrpcResponse<Diagnosis.DiagnoseCropIssueResponse> matchedResponse = aiMlService.diagnoseCropIssue(
				Diagnosis.DiagnoseCropIssueRequest.newBuilder()
						.setFarmId(farmUuid)
						.setCropType("Maize")
						.addSymptoms(aiml.Enum.CropSymptom.SYMPTOM_LEAF_DISCOLORATION)
						.addSymptoms(aiml.Enum.CropSymptom.SYMPTOM_LEAF_SPOTS)
						.build());
		assertThat(matchedResponse.getCode()).isEqualTo("200");
		assertThat(matchedResponse.getData().getDiagnosis().getDiagnosisResult()).isEqualTo("Maize Leaf Blight");
		assertThat(matchedResponse.getData().getDiagnosis().getConfidence()).isGreaterThan(0.0);

		GrpcResponse<Diagnosis.DiagnoseCropIssueResponse> fallbackResponse = aiMlService.diagnoseCropIssue(
				Diagnosis.DiagnoseCropIssueRequest.newBuilder()
						.setFarmId(farmUuid)
						.setCropType("Maize")
						.build());
		assertThat(fallbackResponse.getData().getDiagnosis().getDiagnosisResult())
				.isEqualTo("Unable to determine a specific cause from the symptoms provided");
		assertThat(fallbackResponse.getData().getDiagnosis().getConfidence()).isEqualTo(0.0);

		GrpcResponse<Diagnosis.GetDiagnosisHistoryResponse> history = aiMlService.getDiagnosisHistory(
				Diagnosis.GetDiagnosisHistoryRequest.newBuilder().setFarmId(farmUuid).build());
		assertThat(history.getData().getDiagnosesList()).hasSize(2);
	}

	@Test
	void predictYield_usesHealthMultiplierWhenTelemetryExists() {
		String farmUuid = createTestFarm();

		GrpcResponse<Yield.PredictYieldResponse> beforeTelemetry = aiMlService.predictYield(
				Yield.PredictYieldRequest.newBuilder().setFarmId(farmUuid).build());
		assertThat(beforeTelemetry.getCode()).isEqualTo("200");
		// maize base 2.5 tons/acre * 5 acres * neutral 1.0 multiplier (no vegetation data yet)
		assertThat(beforeTelemetry.getData().getPrediction().getPredictedYieldTons()).isEqualTo(12.5);
		assertThat(beforeTelemetry.getData().getPrediction().getHealthMultiplierUsed()).isEqualTo(1.0);

		telemetryService.recordVegetationIndex(Vegetation.RecordVegetationIndexRequest.newBuilder()
				.setFarmId(farmUuid)
				.setNdviValue(0.65)
				.setSource(telemetry.Enum.DataSource.SOURCE_SATELLITE)
				.build());

		GrpcResponse<Yield.PredictYieldResponse> afterTelemetry = aiMlService.predictYield(
				Yield.PredictYieldRequest.newBuilder().setFarmId(farmUuid).build());
		// EXCELLENT band (ndvi 0.65 >= 0.6) multiplier is 1.2
		assertThat(afterTelemetry.getData().getPrediction().getHealthMultiplierUsed()).isEqualTo(1.2);
		assertThat(afterTelemetry.getData().getPrediction().getPredictedYieldTons()).isEqualTo(15.0);

		GrpcResponse<Yield.GetLatestYieldPredictionResponse> latest = aiMlService.getLatestYieldPrediction(
				Yield.GetLatestYieldPredictionRequest.newBuilder().setFarmId(farmUuid).build());
		assertThat(latest.getData().getPrediction().getPredictedYieldTons()).isEqualTo(15.0);
	}

	@Test
	void getAgronomicAdvice_returnsCropSpecificTip() {
		GrpcResponse<Advisory.GetAgronomicAdviceResponse> response = aiMlService.getAgronomicAdvice(
				Advisory.GetAgronomicAdviceRequest.newBuilder().setCropType("Maize").setGrowthStage("vegetative").build());
		assertThat(response.getCode()).isEqualTo("200");
		assertThat(response.getData().getAdvice()).contains("nitrogen");
	}

}
