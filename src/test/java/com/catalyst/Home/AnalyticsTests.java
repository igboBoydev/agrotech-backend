package com.catalyst.Home;

import analytics.Insights;
import analytics.SubscriptionOuterClass;
import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Repositories.BusinessRepository;
import com.catalyst.Home.Repositories.FarmProductsRepository;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.FarmsRepository;
import com.catalyst.Home.Repositories.SubscriptionRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Services.AnalyticsService;
import com.catalyst.Home.Services.OnboardingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import users.Business;
import users.Farmers;
import users.ProductsOuterClass;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AnalyticsTests {

	private static final String TEST_EMAIL = "kunle.adebayo.analytics@example.com";
	private static final String COMPANY_REG_NO = "RC-ANALYTICS-001";

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private AnalyticsService analyticsService;

	@Autowired
	private FarmerRepository farmerRepository;

	@Autowired
	private UsersRepository usersRepository;

	@Autowired
	private FarmsRepository farmsRepository;

	@Autowired
	private FarmProductsRepository farmProductsRepository;

	@Autowired
	private BusinessRepository businessRepository;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@AfterEach
	void cleanUp() {
		farmerRepository.findByEmail(TEST_EMAIL).ifPresent(farmer -> {
			farmsRepository.findByFarmers_Uuid(farmer.getUuid()).forEach(farm -> {
				farmProductsRepository.findByFarm_Uuid(farm.getUuid()).forEach(farmProductsRepository::delete);
				farmsRepository.delete(farm);
			});
			farmerRepository.delete(farmer);
		});
		usersRepository.findByEmail(TEST_EMAIL).ifPresent(usersRepository::delete);

		businessRepository.findByCompanyRegistrationNo(COMPANY_REG_NO).ifPresent(business -> {
			subscriptionRepository.findTopByBusinessIdOrderByIdDesc(business.getUuid()).ifPresent(subscriptionRepository::delete);
			businessRepository.delete(business);
		});
	}

	private String createTestBusiness() {
		onboardingService.createBusiness(Business.CreateBusinessRequest.newBuilder()
				.setCompanyName("Adebayo Agro Supplies")
				.setCompanyRegistrationNo(COMPANY_REG_NO)
				.setDateOfRegistration("2018-05-10")
				.setCountryOfRegistration("Nigeria")
				.setCompanyAddress("15 Industrial Way")
				.setState("Lagos")
				.setCity("Ikeja")
				.setCountry("Nigeria")
				.setLastName("Adebayo")
				.setBvn("11122233344")
				.setTin("TIN-ANALYTICS")
				.setAddress("15 Industrial Way")
				.build());
		return businessRepository.findByCompanyRegistrationNo(COMPANY_REG_NO).orElseThrow().getUuid();
	}

	private void createTestFarmWithProduct() {
		onboardingService.createFarmer(Farmers.CreateFarmerRequest.newBuilder()
				.setFirstName("Kunle")
				.setLastName("Adebayo")
				.setDob("1980-03-22")
				.setHomeAddress("3 Farmgate Street")
				.setLga("Ikeja")
				.setState("Lagos")
				.setCountry("Nigeria")
				.setEmail(TEST_EMAIL)
				.setPhone("+2348033445566")
				.setPassword("s3cret-pw")
				.build());
		String farmerUuid = farmerRepository.findByEmail(TEST_EMAIL).orElseThrow().getUuid();

		onboardingService.addFarm(Farmers.AddFarmRequest.newBuilder()
				.setUserId(farmerUuid)
				.setAddress("10 Farm Lane")
				.setLga("Ikeja")
				.setState("Lagos")
				.setCountry("Nigeria")
				.setLandSize("6 acres")
				.setIsLease(false)
				.setIsOwnProperty(true)
				.setPlantType("Maize")
				.setFarmType("Crop")
				.build());
		String farmUuid = farmsRepository.findByFarmers_Uuid(farmerUuid).stream().findFirst().orElseThrow().getUuid();

		onboardingService.addFarmProduct(ProductsOuterClass.AddFarmProductRequest.newBuilder()
				.setFarmId(farmUuid)
				.setProductName("Maize")
				.setCategory("Grain")
				.setProductType("Cereal")
				.setQuantity(100.0)
				.setUnit("bags")
				.setPricePerUnit(50.0)
				.setIsAvailable(true)
				.build());
	}

	@Test
	void subscriptionGatesAccess_toRegionalCropSummary() {
		String businessUuid = createTestBusiness();
		createTestFarmWithProduct();

		Insights.GetRegionalCropSummaryRequest cropRequest = Insights.GetRegionalCropSummaryRequest.newBuilder()
				.setBusinessId(businessUuid)
				.setState("Lagos")
				.setPlantType("Maize")
				.build();

		GrpcResponse<Insights.GetRegionalCropSummaryResponse> beforeSubscription = analyticsService.getRegionalCropSummary(cropRequest);
		assertThat(beforeSubscription.getCode()).isEqualTo("403");

		GrpcResponse<SubscriptionOuterClass.CreateSubscriptionResponse> createResponse = analyticsService.createSubscription(
				SubscriptionOuterClass.CreateSubscriptionRequest.newBuilder()
						.setBusinessId(businessUuid)
						.setTier(analytics.Enum.SubscriptionTier.TIER_AGRO_DEALER)
						.build());
		assertThat(createResponse.getCode()).isEqualTo("200");
		assertThat(createResponse.getData().getSubscription().getPrice()).isEqualTo(200000.0);
		String subscriptionUuid = createResponse.getData().getSubscription().getId();

		GrpcResponse<Insights.GetRegionalCropSummaryResponse> afterSubscription = analyticsService.getRegionalCropSummary(cropRequest);
		assertThat(afterSubscription.getCode()).isEqualTo("200");
		assertThat(afterSubscription.getData().getFarmCount()).isEqualTo(1);
		assertThat(afterSubscription.getData().getTotalQuantityListed()).isEqualTo(100.0);
		assertThat(afterSubscription.getData().getAveragePricePerUnit()).isEqualTo(50.0);

		GrpcResponse<Insights.GetMarketplaceActivitySummaryResponse> higherTierCheck = analyticsService.getMarketplaceActivitySummary(
				Insights.GetMarketplaceActivitySummaryRequest.newBuilder().setBusinessId(businessUuid).build());
		assertThat(higherTierCheck.getCode()).isEqualTo("403");

		GrpcResponse<SubscriptionOuterClass.CancelSubscriptionResponse> cancelResponse = analyticsService.cancelSubscription(
				SubscriptionOuterClass.CancelSubscriptionRequest.newBuilder().setSubscriptionId(subscriptionUuid).build());
		assertThat(cancelResponse.getData().getSubscription().getStatus()).isEqualTo(analytics.Enum.SubscriptionStatus.SUBSCRIPTION_CANCELLED);

		GrpcResponse<Insights.GetRegionalCropSummaryResponse> afterCancellation = analyticsService.getRegionalCropSummary(cropRequest);
		assertThat(afterCancellation.getCode()).isEqualTo("403");
	}

}
