package com.catalyst.Home;

import auth.Auth;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.TokenValidationRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Services.AuthService;
import com.catalyst.Home.Services.OnboardingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import users.Farmers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuthTests {

	private static final String TEST_EMAIL = "grace.hopper.verify@example.com";
	private static final String TEST_PASSWORD = "s3cret-pw";

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private AuthService authService;

	@Autowired
	private FarmerRepository farmerRepository;

	@Autowired
	private UsersRepository usersRepository;

	@Autowired
	private TokenValidationRepository tokenValidationRepository;

	@AfterEach
	void cleanUp() {
		farmerRepository.findByEmail(TEST_EMAIL).ifPresent(farmerRepository::delete);
		usersRepository.findByEmail(TEST_EMAIL).ifPresent(usersRepository::delete);
		tokenValidationRepository.deleteByEmail(TEST_EMAIL);
	}

	private void createTestFarmer() {
		onboardingService.createFarmer(Farmers.CreateFarmerRequest.newBuilder()
				.setFirstName("Grace")
				.setLastName("Hopper")
				.setDob("1906-12-09")
				.setHomeAddress("1 Compiler Way")
				.setLga("Ikeja")
				.setState("Lagos")
				.setCountry("Nigeria")
				.setEmail(TEST_EMAIL)
				.setPhone("+2348022223333")
				.setPassword(TEST_PASSWORD)
				.build());
	}

	@Test
	void farmerLogin_withCorrectPassword_issuesToken() {
		createTestFarmer();

		Auth.FarmerLoginResponse response = authService.farmerLogin(Auth.FarmerLoginRequest.newBuilder()
				.setEmail(TEST_EMAIL)
				.setPassword(TEST_PASSWORD)
				.build());

		assertThat(response.getCode()).isEqualTo("200");
		assertThat(response.getToken()).isNotBlank();
	}

	@Test
	void farmerLogin_withWrongPassword_isRejected() {
		createTestFarmer();

		Auth.FarmerLoginResponse response = authService.farmerLogin(Auth.FarmerLoginRequest.newBuilder()
				.setEmail(TEST_EMAIL)
				.setPassword("not-the-password")
				.build());

		assertThat(response.getCode()).isEqualTo("401");
		assertThat(response.getToken()).isBlank();
	}

	@Test
	void validateEmail_reflectsRegistrationState() {
		Auth.ValidateEmailResponse beforeSignup = authService.validateEmail(Auth.ValidateEmailRequest.newBuilder()
				.setEmail(TEST_EMAIL)
				.build());
		assertThat(beforeSignup.getCode()).isEqualTo("200");

		createTestFarmer();

		Auth.ValidateEmailResponse afterSignup = authService.validateEmail(Auth.ValidateEmailRequest.newBuilder()
				.setEmail(TEST_EMAIL)
				.build());
		assertThat(afterSignup.getCode()).isEqualTo("409");
	}

	@Test
	void generateAndValidateToken_roundTrips() {
		Auth.GenerateTokenResponse generated = authService.generateToken(Auth.GenerateTokenRequest.newBuilder()
				.setEmail(TEST_EMAIL)
				.build());
		assertThat(generated.getToken()).hasSize(6);

		Auth.TokenResponse validated = authService.validateToken(Auth.TokenRequest.newBuilder()
				.setToken(generated.getToken())
				.setValidateType(Auth.ValidateType.EMAIL)
				.build());
		assertThat(validated.getCode()).isEqualTo("200");

		Auth.TokenResponse reused = authService.validateToken(Auth.TokenRequest.newBuilder()
				.setToken(generated.getToken())
				.setValidateType(Auth.ValidateType.EMAIL)
				.build());
		assertThat(reused.getCode()).isEqualTo("400");
	}

}
