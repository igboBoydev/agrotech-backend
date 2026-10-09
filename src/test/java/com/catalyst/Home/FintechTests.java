package com.catalyst.Home;

import com.catalyst.Home.DTO.response.GrpcResponse;
import com.catalyst.Home.Repositories.CreditScoreRecordRepository;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.InsuranceClaimRepository;
import com.catalyst.Home.Repositories.InsurancePolicyRepository;
import com.catalyst.Home.Repositories.LoanAccountRepository;
import com.catalyst.Home.Repositories.LoanApplicationRepository;
import com.catalyst.Home.Repositories.LoanRepaymentRepository;
import com.catalyst.Home.Repositories.MfbReferralRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Services.FintechService;
import com.catalyst.Home.Services.OnboardingService;
import fintech.Creditscore;
import fintech.Insurance;
import fintech.Loan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import users.Farmers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FintechTests {

	private static final String TEST_EMAIL = "amara.nwosu.fintech@example.com";

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private FintechService fintechService;

	@Autowired
	private FarmerRepository farmerRepository;

	@Autowired
	private UsersRepository usersRepository;

	@Autowired
	private LoanApplicationRepository loanApplicationRepository;

	@Autowired
	private LoanAccountRepository loanAccountRepository;

	@Autowired
	private LoanRepaymentRepository loanRepaymentRepository;

	@Autowired
	private InsurancePolicyRepository insurancePolicyRepository;

	@Autowired
	private InsuranceClaimRepository insuranceClaimRepository;

	@Autowired
	private CreditScoreRecordRepository creditScoreRecordRepository;

	@Autowired
	private MfbReferralRepository mfbReferralRepository;

	@AfterEach
	void cleanUp() {
		farmerRepository.findByEmail(TEST_EMAIL).ifPresent(farmer -> {
			mfbReferralRepository.findByFarmers_Uuid(farmer.getUuid()).forEach(mfbReferralRepository::delete);
			loanAccountRepository.findByFarmers_Uuid(farmer.getUuid()).forEach(account -> {
				loanRepaymentRepository.deleteAll(loanRepaymentRepository.findAll().stream()
						.filter(r -> r.getLoanAccount() != null && account.getUuid().equals(r.getLoanAccount().getUuid()))
						.toList());
				loanAccountRepository.delete(account);
			});
			loanApplicationRepository.findByFarmers_Uuid(farmer.getUuid()).forEach(loanApplicationRepository::delete);
			insurancePolicyRepository.findByFarmers_Uuid(farmer.getUuid()).forEach(policy -> {
				insuranceClaimRepository.findAll().stream()
						.filter(c -> c.getPolicy() != null && policy.getUuid().equals(c.getPolicy().getUuid()))
						.forEach(insuranceClaimRepository::delete);
				insurancePolicyRepository.delete(policy);
			});
			creditScoreRecordRepository.findAll().stream()
					.filter(s -> s.getFarmers() != null && farmer.getUuid().equals(s.getFarmers().getUuid()))
					.forEach(creditScoreRecordRepository::delete);

			farmerRepository.delete(farmer);
		});
		usersRepository.findByEmail(TEST_EMAIL).ifPresent(usersRepository::delete);
	}

	private String createTestFarmer() {
		onboardingService.createFarmer(Farmers.CreateFarmerRequest.newBuilder()
				.setFirstName("Amara")
				.setLastName("Nwosu")
				.setDob("1985-06-12")
				.setHomeAddress("14 River Road")
				.setLga("Owerri")
				.setState("Imo")
				.setCountry("Nigeria")
				.setEmail(TEST_EMAIL)
				.setPhone("+2348055556666")
				.setPassword("s3cret-pw")
				.build());
		return farmerRepository.findByEmail(TEST_EMAIL).orElseThrow().getUuid();
	}

	@Test
	void loanLifecycle_applyApproveRepayToCompletion() {
		String farmerUuid = createTestFarmer();

		GrpcResponse<Loan.ApplyForLoanResponse> applyResponse = fintechService.applyForLoan(Loan.ApplyForLoanRequest.newBuilder()
				.setFarmerId(farmerUuid)
				.setRequestedAmount(100000.0)
				.setTermMonths(6)
				.setPurpose("Fertilizer and seeds")
				.build());
		assertThat(applyResponse.getCode()).isEqualTo("200");
		// No credit score computed yet, so the platform defaults to the mid-range 25% rate.
		assertThat(applyResponse.getData().getApplication().getInterestRate()).isEqualTo(0.25);
		String applicationUuid = applyResponse.getData().getApplication().getId();

		GrpcResponse<Loan.ReviewLoanApplicationResponse> reviewResponse = fintechService.reviewLoanApplication(
				Loan.ReviewLoanApplicationRequest.newBuilder().setApplicationId(applicationUuid).setApprove(true).build());
		assertThat(reviewResponse.getCode()).isEqualTo("200");
		Loan.LoanAccount account = reviewResponse.getData().getAccount();
		assertThat(account.getOutstandingBalance()).isEqualTo(125000.0);
		String loanUuid = account.getId();

		GrpcResponse<Loan.RecordRepaymentResponse> firstRepayment = fintechService.recordRepayment(
				Loan.RecordRepaymentRequest.newBuilder().setLoanId(loanUuid).setAmount(75000.0).build());
		assertThat(firstRepayment.getData().getAccount().getOutstandingBalance()).isEqualTo(50000.0);

		GrpcResponse<Loan.RecordRepaymentResponse> finalRepayment = fintechService.recordRepayment(
				Loan.RecordRepaymentRequest.newBuilder().setLoanId(loanUuid).setAmount(50000.0).build());
		assertThat(finalRepayment.getData().getAccount().getOutstandingBalance()).isEqualTo(0.0);
		assertThat(finalRepayment.getData().getAccount().getStatus()).isEqualTo(fintech.Enum.LoanAccountStatus.LOAN_COMPLETED);

		GrpcResponse<Loan.GetLoanResponse> getLoanResponse = fintechService.getLoan(
				Loan.GetLoanRequest.newBuilder().setLoanId(loanUuid).build());
		assertThat(getLoanResponse.getData().getAccount().getRepaymentsList()).hasSize(2);
	}

	@Test
	void insuranceLifecycle_createPolicyFileAndApproveClaim() {
		String farmerUuid = createTestFarmer();

		GrpcResponse<Insurance.CreatePolicyResponse> createResponse = fintechService.createPolicy(
				Insurance.CreatePolicyRequest.newBuilder()
						.setFarmerId(farmerUuid)
						.setPolicyType("Crop insurance")
						.setCoverageAmount(500000.0)
						.setPremiumAmount(20000.0)
						.setStartDate("2026-01-01")
						.setEndDate("2026-12-31")
						.build());
		assertThat(createResponse.getCode()).isEqualTo("200");
		// Platform commission is a fixed 20% of the premium.
		assertThat(createResponse.getData().getPolicy().getCommissionAmount()).isEqualTo(4000.0);
		String policyUuid = createResponse.getData().getPolicy().getId();

		GrpcResponse<Insurance.FileClaimResponse> fileResponse = fintechService.fileClaim(
				Insurance.FileClaimRequest.newBuilder()
						.setPolicyId(policyUuid)
						.setClaimAmount(150000.0)
						.setDescription("Flood damage to maize crop")
						.build());
		assertThat(fileResponse.getCode()).isEqualTo("200");
		String claimUuid = fileResponse.getData().getClaim().getId();

		GrpcResponse<Insurance.ReviewClaimResponse> reviewResponse = fintechService.reviewClaim(
				Insurance.ReviewClaimRequest.newBuilder().setClaimId(claimUuid).setApprove(true).setPayoutAmount(140000.0).build());
		assertThat(reviewResponse.getCode()).isEqualTo("200");
		assertThat(reviewResponse.getData().getClaim().getStatus()).isEqualTo(fintech.Enum.ClaimStatus.CLAIM_APPROVED);
		assertThat(reviewResponse.getData().getClaim().getPayoutAmount()).isEqualTo(140000.0);

		GrpcResponse<Insurance.GetPolicyResponse> getPolicyResponse = fintechService.getPolicy(
				Insurance.GetPolicyRequest.newBuilder().setPolicyId(policyUuid).build());
		assertThat(getPolicyResponse.getData().getPolicy().getClaimsList()).hasSize(1);
	}

	@Test
	void creditScoreAndReferral_requiresScoreBeforeReferral() {
		String farmerUuid = createTestFarmer();

		GrpcResponse<Creditscore.ReferToMfbResponse> tooEarly = fintechService.referToMfb(
				Creditscore.ReferToMfbRequest.newBuilder().setFarmerId(farmerUuid).setMfbName("Lift Above Poverty MFB").build());
		assertThat(tooEarly.getCode()).isEqualTo("412");

		GrpcResponse<Creditscore.ComputeCreditScoreResponse> computeResponse = fintechService.computeCreditScore(
				Creditscore.ComputeCreditScoreRequest.newBuilder().setFarmerId(farmerUuid).build());
		assertThat(computeResponse.getCode()).isEqualTo("200");
		// New farmer, no KYC/farms/loans yet: base score with only the phone/email-unvalidated defaults.
		assertThat(computeResponse.getData().getScore().getScore()).isEqualTo(300);
		assertThat(computeResponse.getData().getScore().getBand()).isEqualTo(fintech.Enum.CreditScoreBand.POOR);

		GrpcResponse<Creditscore.GetCreditScoreResponse> getResponse = fintechService.getCreditScore(
				Creditscore.GetCreditScoreRequest.newBuilder().setFarmerId(farmerUuid).build());
		assertThat(getResponse.getData().getScore().getScore()).isEqualTo(300);

		GrpcResponse<Creditscore.ReferToMfbResponse> referResponse = fintechService.referToMfb(
				Creditscore.ReferToMfbRequest.newBuilder().setFarmerId(farmerUuid).setMfbName("Lift Above Poverty MFB").build());
		assertThat(referResponse.getCode()).isEqualTo("200");
		// POOR band maps to the bottom of the doc's stated ₦3,000-10,000 referral fee range.
		assertThat(referResponse.getData().getReferral().getReferralFee()).isEqualTo(3000.0);
	}

}
