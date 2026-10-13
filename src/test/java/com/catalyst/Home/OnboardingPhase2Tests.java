package com.catalyst.Home;

import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Entity.FarmersKyc;
import com.catalyst.Home.Entity.Farms;
import com.catalyst.Home.Repositories.BusinessRepository;
import com.catalyst.Home.Repositories.FarmProductsRepository;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.FarmersKycRepository;
import com.catalyst.Home.Repositories.FarmsRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Services.OnboardingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import users.Business;
import users.DocumentOuterClass;
import users.Farmers;
import users.KycOuterClass;
import users.ProductsOuterClass;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OnboardingPhase2Tests {

	private static final String TEST_EMAIL = "wangari.maathai.verify@example.com";
	private static final String COMPANY_REG_NO = "RC-TEST-987654";

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private FarmerRepository farmerRepository;

	@Autowired
	private UsersRepository usersRepository;

	@Autowired
	private FarmsRepository farmsRepository;

	@Autowired
	private FarmersKycRepository farmersKycRepository;

	@Autowired
	private FarmProductsRepository farmProductsRepository;

	@Autowired
	private BusinessRepository businessRepository;

	@AfterEach
	void cleanUp() {
		farmProductsRepository.findAll().stream()
				.filter(p -> "Maize".equals(p.getProductName()))
				.forEach(farmProductsRepository::delete);

		farmerRepository.findByEmail(TEST_EMAIL).ifPresent(farmer -> {
			farmsRepository.findByFarmers_Uuid(farmer.getUuid()).forEach(farmsRepository::delete);
			FarmersKyc kyc = farmer.getKyc();
			farmer.setKyc(null);
			farmerRepository.save(farmer);
			if (kyc != null) {
				farmersKycRepository.delete(kyc);
			}
			farmerRepository.delete(farmer);
		});

		usersRepository.findByEmail(TEST_EMAIL).ifPresent(usersRepository::delete);
		businessRepository.findByCompanyRegistrationNo(COMPANY_REG_NO).ifPresent(businessRepository::delete);
	}

	private String createTestFarmer() {
		onboardingService.createFarmer(Farmers.CreateFarmerRequest.newBuilder()
				.setFirstName("Wangari")
				.setLastName("Maathai")
				.setDob("1940-04-01")
				.setHomeAddress("1 Greenbelt Way")
				.setLga("Ikeja")
				.setState("Lagos")
				.setCountry("Nigeria")
				.setEmail(TEST_EMAIL)
				.setPhone("+2348033334444")
				.setPassword("s3cret-pw")
				.build());
		return farmerRepository.findByEmail(TEST_EMAIL).orElseThrow().getUuid();
	}

	@Test
	void farmLifecycle_addGetProfileKycAndProducts() {
		String farmerUuid = createTestFarmer();

		GrpcResponse<Farmers.AddFarmResponse> addFarmResponse = onboardingService.addFarm(Farmers.AddFarmRequest.newBuilder()
				.setUserId(farmerUuid)
				.setAddress("12 Farm Road")
				.setLga("Ikeja")
				.setState("Lagos")
				.setCountry("Nigeria")
				.setLandSize("5 acres")
				.setIsLease(false)
				.setIsOwnProperty(true)
				.setNumOfEmployees(1)
				.setPlantType("Maize")
				.setPlantSeasonStartDate("2026-03-01")
				.setPlantSeasonEndDate("2026-07-01")
				.setFarmType("Crop")
				.addEmployees(Farmers.Employee.newBuilder()
						.setOccupation("Field hand")
						.setPosition("Laborer")
						.build())
				.build());
		assertThat(addFarmResponse.getCode()).isEqualTo("200");

		GrpcResponse<Farmers.GetFarmsResponse> farmsResponse = onboardingService.getFarms(
				Farmers.GetFarmsRequest.newBuilder().setUserId(farmerUuid).build());
		assertThat(farmsResponse.getData().getFarmsList()).hasSize(1);
		assertThat(farmsResponse.getData().getFarms(0).getPlantType()).isEqualTo("Maize");

		GrpcResponse<Farmers.FarmerProfileResponse> profileResponse = onboardingService.getFarmersProfile(
				Farmers.FarmerProfileRequest.newBuilder().setUserId(farmerUuid).build());
		assertThat(profileResponse.getCode()).isEqualTo("200");
		assertThat(profileResponse.getData().getFarmerDetails().getFirstName()).isEqualTo("Wangari");
		assertThat(profileResponse.getData().getFarmerDetails().getFarmsList()).hasSize(1);

		GrpcResponse<Farmers.AddFarmerKycResponse> kycResponse = onboardingService.addFarmerKyc(Farmers.AddFarmerKycRequest.newBuilder()
				.setId(farmerUuid)
				.setBvn("12345678901")
				.setTin("TIN-001")
				.setSignature("signature-blob")
				.setKycLevel("1")
				.setTier("1")
				.setCorporateCustomerAddress(Farmers.CorporateUserAddress.newBuilder()
						.setAddress("1 KYC Street")
						.setCity("Lagos")
						.setState("Lagos")
						.setCountry("Nigeria")
						.build())
				.addDocumentsAndIdentity(DocumentOuterClass.Document.newBuilder()
						.setDocumentName("NIN Slip")
						.setDocumentNumber("NIN-123")
						.setVerificationStatus(users.Enum.VerificationStatus.PENDING)
						.build())
				.build());
		assertThat(kycResponse.getCode()).isEqualTo("200");

		GrpcResponse<KycOuterClass.KYCStatus> statusResponse = onboardingService.checkKycStatus(
				KycOuterClass.KYCRequest.newBuilder().setUserId(farmerUuid).build());
		assertThat(statusResponse.getCode()).isEqualTo("200");
		assertThat(statusResponse.getData().getVerificationStatus()).isEqualTo(users.Enum.VerificationStatus.PENDING);

		Optional<Farms> farm = farmsRepository.findByFarmers_Uuid(farmerUuid).stream().findFirst();
		assertThat(farm).isPresent();
		String farmUuid = farm.get().getUuid();

		GrpcResponse<ProductsOuterClass.AddFarmProductResponse> addProductResponse = onboardingService.addFarmProduct(
				ProductsOuterClass.AddFarmProductRequest.newBuilder()
						.setFarmId(farmUuid)
						.setProductName("Maize")
						.setCategory("Grain")
						.setProductType("Cereal")
						.setQuantity(100)
						.setUnit("kg")
						.setPricePerUnit(50.0)
						.setIsAvailable(true)
						.build());
		assertThat(addProductResponse.getCode()).isEqualTo("200");

		GrpcResponse<ProductsOuterClass.GetProductsResponse> productsResponse = onboardingService.getFarmProducts(
				ProductsOuterClass.GetProductsRequest.newBuilder().setFarmId(farmUuid).build());
		List<ProductsOuterClass.Products> products = productsResponse.getData().getProductList();
		assertThat(products).hasSize(1);
		assertThat(products.get(0).getName()).isEqualTo("Maize");
		assertThat(products.get(0).getIsAvailable()).isTrue();
	}

	@Test
	void businessLifecycle_createAndFetchProfile() {
		GrpcResponse<Business.CreateBusinessResponse> createResponse = onboardingService.createBusiness(
				Business.CreateBusinessRequest.newBuilder()
						.setCompanyName("Acme Farms Ltd")
						.setCompanyRegistrationNo(COMPANY_REG_NO)
						.setDateOfRegistration("2020-01-01")
						.setCountryOfRegistration("Nigeria")
						.setCompanyAddress("1 Business Road")
						.setState("Lagos")
						.setCity("Ikeja")
						.setCountry("Nigeria")
						.setLastName("Doe")
						.setBvn("98765432109")
						.setTin("TIN-999")
						.setAddress("1 Business Road")
						.build());
		assertThat(createResponse.getCode()).isEqualTo("200");

		String businessUuid = businessRepository.findByCompanyRegistrationNo(COMPANY_REG_NO).orElseThrow().getUuid();

		GrpcResponse<Business.GetBusinessProfileResponse> profileResponse = onboardingService.getBusinessProfile(
				Business.GetBusinessProfileRequest.newBuilder().setUserId(businessUuid).build());
		assertThat(profileResponse.getCode()).isEqualTo("200");
		assertThat(profileResponse.getData().getCompanyName()).isEqualTo("Acme Farms Ltd");
		assertThat(profileResponse.getData().getBvn()).isEqualTo("98765432109");
	}

}
