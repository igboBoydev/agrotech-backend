package com.catalyst.Home;

import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Services.OnboardingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import users.Farmers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OnboardingTests {

	private static final String TEST_EMAIL = "ada.lovelace.verify@example.com";

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private FarmerRepository farmerRepository;

	@Autowired
	private UsersRepository usersRepository;

	@AfterEach
	void cleanUp() {
		farmerRepository.findByEmail(TEST_EMAIL).ifPresent(farmerRepository::delete);
		usersRepository.findByEmail(TEST_EMAIL).ifPresent(usersRepository::delete);
	}

	@Test
	void contextLoads() {
	}

	@Test
	void createFarmer_persistsAndReturnsSuccess() {
		Farmers.CreateFarmerRequest request = Farmers.CreateFarmerRequest.newBuilder()
				.setFirstName("Ada")
				.setLastName("Lovelace")
				.setDob("1990-01-01")
				.setHomeAddress("12 Farm Road")
				.setLga("Ikeja")
				.setState("Lagos")
				.setCountry("Nigeria")
				.setEmail(TEST_EMAIL)
				.setPhone("+2348011112222")
				.setPassword("s3cret")
				.build();

		GrpcResponse<Farmers.CreateFarmerResponse> response = onboardingService.createFarmer(request);

		assertThat(response.getCode()).isEqualTo("200");
		assertThat(response.getData().getCode()).isEqualTo("200");
		assertThat(farmerRepository.findByEmail(TEST_EMAIL)).isPresent();
		assertThat(usersRepository.findByEmail(TEST_EMAIL)).isPresent();
	}

}
