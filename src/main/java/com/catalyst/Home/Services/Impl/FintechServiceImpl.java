package com.catalyst.Home.Services.Impl;

import com.catalyst.Home.DTO.response.GrpcResponse;
import com.catalyst.Home.Entity.CreditScoreRecord;
import com.catalyst.Home.Entity.Documents;
import com.catalyst.Home.Entity.FarmersDetails;
import com.catalyst.Home.Entity.FarmersKyc;
import com.catalyst.Home.Entity.Farms;
import com.catalyst.Home.Entity.InsuranceClaim;
import com.catalyst.Home.Entity.InsurancePolicy;
import com.catalyst.Home.Entity.LoanAccount;
import com.catalyst.Home.Entity.LoanApplication;
import com.catalyst.Home.Entity.LoanRepayment;
import com.catalyst.Home.Entity.MfbReferral;
import com.catalyst.Home.Repositories.CreditScoreRecordRepository;
import com.catalyst.Home.Repositories.FarmProductsRepository;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.FarmsRepository;
import com.catalyst.Home.Repositories.InsuranceClaimRepository;
import com.catalyst.Home.Repositories.InsurancePolicyRepository;
import com.catalyst.Home.Repositories.LoanAccountRepository;
import com.catalyst.Home.Repositories.LoanApplicationRepository;
import com.catalyst.Home.Repositories.LoanRepaymentRepository;
import com.catalyst.Home.Repositories.MfbReferralRepository;
import com.catalyst.Home.Services.FintechService;
import com.catalyst.Home.Utils.ClaimStatus;
import com.catalyst.Home.Utils.CreditScoreBand;
import com.catalyst.Home.Utils.LoanAccountStatus;
import com.catalyst.Home.Utils.LoanApplicationStatus;
import com.catalyst.Home.Utils.PolicyStatus;
import com.catalyst.Home.Utils.ReferralStatus;
import com.catalyst.Home.Utils.VerificationStatus;
import fintech.Creditscore;
import fintech.Insurance;
import fintech.Loan;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FintechServiceImpl implements FintechService {
    private final FarmerRepository farmerRepository;
    private final FarmsRepository farmsRepository;
    private final FarmProductsRepository farmProductsRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final InsuranceClaimRepository insuranceClaimRepository;
    private final CreditScoreRecordRepository creditScoreRecordRepository;
    private final MfbReferralRepository mfbReferralRepository;

    private static final double INSURANCE_COMMISSION_RATE = 0.20;

    @Override
    @Transactional
    public GrpcResponse<Loan.ApplyForLoanResponse> applyForLoan(Loan.ApplyForLoanRequest request) {
        Optional<FarmersDetails> farmerOpt = farmerRepository.findByUuid(request.getFarmerId());
        if (farmerOpt.isEmpty()) {
            return GrpcResponse.<Loan.ApplyForLoanResponse>builder()
                    .code("404").message("Farmer not found").status(Status.Code.NOT_FOUND).build();
        }
        FarmersDetails farmer = farmerOpt.get();

        CreditScoreBand band = creditScoreRecordRepository.findTopByFarmers_UuidOrderByIdDesc(farmer.getUuid())
                .map(CreditScoreRecord::getBand)
                .orElse(null);
        double interestRate = interestRateForBand(band);

        LoanApplication application = loanApplicationRepository.save(LoanApplication.builder()
                .uuid(UUID.randomUUID().toString())
                .farmers(farmer)
                .requestedAmount(BigDecimal.valueOf(request.getRequestedAmount()))
                .termMonths(request.getTermMonths())
                .purpose(request.getPurpose())
                .interestRate(interestRate)
                .status(LoanApplicationStatus.LOAN_APPLICATION_PENDING)
                .appliedAt(LocalDate.now().toString())
                .build());

        Loan.ApplyForLoanResponse response = Loan.ApplyForLoanResponse.newBuilder()
                .setCode("200")
                .setMessage("Loan application submitted successfully")
                .setApplication(toLoanApplicationProto(application))
                .build();

        return GrpcResponse.<Loan.ApplyForLoanResponse>builder()
                .code("200").message("Loan application submitted successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Loan.ReviewLoanApplicationResponse> reviewLoanApplication(Loan.ReviewLoanApplicationRequest request) {
        Optional<LoanApplication> applicationOpt = loanApplicationRepository.findByUuid(request.getApplicationId());
        if (applicationOpt.isEmpty()) {
            return GrpcResponse.<Loan.ReviewLoanApplicationResponse>builder()
                    .code("404").message("Loan application not found").status(Status.Code.NOT_FOUND).build();
        }
        LoanApplication application = applicationOpt.get();
        if (application.getStatus() != LoanApplicationStatus.LOAN_APPLICATION_PENDING) {
            return GrpcResponse.<Loan.ReviewLoanApplicationResponse>builder()
                    .code("409").message("Loan application has already been decided").status(Status.Code.FAILED_PRECONDITION).build();
        }

        application.setDecidedAt(LocalDate.now().toString());

        Loan.ReviewLoanApplicationResponse.Builder responseBuilder = Loan.ReviewLoanApplicationResponse.newBuilder();

        if (request.getApprove()) {
            application.setStatus(LoanApplicationStatus.LOAN_APPLICATION_APPROVED);
            loanApplicationRepository.save(application);

            BigDecimal principal = application.getRequestedAmount();
            BigDecimal totalRepayable = principal.add(principal.multiply(BigDecimal.valueOf(application.getInterestRate())));

            LoanAccount account = loanAccountRepository.save(LoanAccount.builder()
                    .uuid(UUID.randomUUID().toString())
                    .application(application)
                    .farmers(application.getFarmers())
                    .principalAmount(principal)
                    .interestRate(application.getInterestRate())
                    .outstandingBalance(totalRepayable)
                    .status(LoanAccountStatus.LOAN_ACTIVE)
                    .disbursedAt(LocalDate.now().toString())
                    .build());

            responseBuilder.setCode("200").setMessage("Loan approved and disbursed").setAccount(toLoanAccountProto(account));
        } else {
            application.setStatus(LoanApplicationStatus.LOAN_APPLICATION_REJECTED);
            loanApplicationRepository.save(application);
            responseBuilder.setCode("200").setMessage("Loan application rejected");
        }

        return GrpcResponse.<Loan.ReviewLoanApplicationResponse>builder()
                .code("200").message(responseBuilder.getMessage()).status(Status.Code.OK).data(responseBuilder.build()).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Loan.RecordRepaymentResponse> recordRepayment(Loan.RecordRepaymentRequest request) {
        Optional<LoanAccount> accountOpt = loanAccountRepository.findByUuid(request.getLoanId());
        if (accountOpt.isEmpty()) {
            return GrpcResponse.<Loan.RecordRepaymentResponse>builder()
                    .code("404").message("Loan account not found").status(Status.Code.NOT_FOUND).build();
        }
        LoanAccount account = accountOpt.get();
        if (account.getStatus() != LoanAccountStatus.LOAN_ACTIVE) {
            return GrpcResponse.<Loan.RecordRepaymentResponse>builder()
                    .code("409").message("Loan is not active").status(Status.Code.FAILED_PRECONDITION).build();
        }

        BigDecimal amount = BigDecimal.valueOf(request.getAmount());
        LoanRepayment repayment = loanRepaymentRepository.save(LoanRepayment.builder()
                .uuid(UUID.randomUUID().toString())
                .loanAccount(account)
                .amount(amount)
                .paidAt(LocalDate.now().toString())
                .build());

        List<LoanRepayment> repayments = account.getRepayments();
        if (repayments == null) {
            repayments = new ArrayList<>();
            account.setRepayments(repayments);
        }
        repayments.add(repayment);

        BigDecimal newBalance = account.getOutstandingBalance().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            newBalance = BigDecimal.ZERO;
            account.setStatus(LoanAccountStatus.LOAN_COMPLETED);
        }
        account.setOutstandingBalance(newBalance);
        loanAccountRepository.save(account);

        Loan.RecordRepaymentResponse response = Loan.RecordRepaymentResponse.newBuilder()
                .setCode("200")
                .setMessage("Repayment recorded successfully")
                .setAccount(toLoanAccountProto(account))
                .build();

        return GrpcResponse.<Loan.RecordRepaymentResponse>builder()
                .code("200").message("Repayment recorded successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Loan.GetLoanResponse> getLoan(Loan.GetLoanRequest request) {
        Optional<LoanAccount> accountOpt = loanAccountRepository.findByUuid(request.getLoanId());
        if (accountOpt.isEmpty()) {
            return GrpcResponse.<Loan.GetLoanResponse>builder()
                    .code("404").message("Loan account not found").status(Status.Code.NOT_FOUND).build();
        }

        Loan.GetLoanResponse response = Loan.GetLoanResponse.newBuilder()
                .setCode("200")
                .setMessage("Loan retrieved successfully")
                .setAccount(toLoanAccountProto(accountOpt.get()))
                .build();

        return GrpcResponse.<Loan.GetLoanResponse>builder()
                .code("200").message("Loan retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Loan.GetLoansByFarmerResponse> getLoansByFarmer(Loan.GetLoansByFarmerRequest request) {
        List<LoanAccount> accounts = loanAccountRepository.findByFarmers_Uuid(request.getFarmerId());

        Loan.GetLoansByFarmerResponse response = Loan.GetLoansByFarmerResponse.newBuilder()
                .addAllAccounts(accounts.stream().map(this::toLoanAccountProto).toList())
                .build();

        return GrpcResponse.<Loan.GetLoansByFarmerResponse>builder()
                .code("200").message("Loans retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Insurance.CreatePolicyResponse> createPolicy(Insurance.CreatePolicyRequest request) {
        Optional<FarmersDetails> farmerOpt = farmerRepository.findByUuid(request.getFarmerId());
        if (farmerOpt.isEmpty()) {
            return GrpcResponse.<Insurance.CreatePolicyResponse>builder()
                    .code("404").message("Farmer not found").status(Status.Code.NOT_FOUND).build();
        }

        BigDecimal premium = BigDecimal.valueOf(request.getPremiumAmount());
        BigDecimal commission = premium.multiply(BigDecimal.valueOf(INSURANCE_COMMISSION_RATE));

        InsurancePolicy policy = insurancePolicyRepository.save(InsurancePolicy.builder()
                .uuid(UUID.randomUUID().toString())
                .farmers(farmerOpt.get())
                .policyType(request.getPolicyType())
                .coverageAmount(BigDecimal.valueOf(request.getCoverageAmount()))
                .premiumAmount(premium)
                .commissionAmount(commission)
                .status(PolicyStatus.POLICY_ACTIVE)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build());

        Insurance.CreatePolicyResponse response = Insurance.CreatePolicyResponse.newBuilder()
                .setCode("200")
                .setMessage("Insurance policy created successfully")
                .setPolicy(toPolicyProto(policy))
                .build();

        return GrpcResponse.<Insurance.CreatePolicyResponse>builder()
                .code("200").message("Insurance policy created successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Insurance.FileClaimResponse> fileClaim(Insurance.FileClaimRequest request) {
        Optional<InsurancePolicy> policyOpt = insurancePolicyRepository.findByUuid(request.getPolicyId());
        if (policyOpt.isEmpty()) {
            return GrpcResponse.<Insurance.FileClaimResponse>builder()
                    .code("404").message("Insurance policy not found").status(Status.Code.NOT_FOUND).build();
        }
        InsurancePolicy policy = policyOpt.get();
        if (policy.getStatus() != PolicyStatus.POLICY_ACTIVE) {
            return GrpcResponse.<Insurance.FileClaimResponse>builder()
                    .code("409").message("Policy is not active").status(Status.Code.FAILED_PRECONDITION).build();
        }

        InsuranceClaim claim = insuranceClaimRepository.save(InsuranceClaim.builder()
                .uuid(UUID.randomUUID().toString())
                .policy(policy)
                .claimAmount(BigDecimal.valueOf(request.getClaimAmount()))
                .payoutAmount(BigDecimal.ZERO)
                .description(request.getDescription())
                .status(ClaimStatus.CLAIM_FILED)
                .filedAt(LocalDate.now().toString())
                .build());

        Insurance.FileClaimResponse response = Insurance.FileClaimResponse.newBuilder()
                .setCode("200")
                .setMessage("Claim filed successfully")
                .setClaim(toClaimProto(claim))
                .build();

        return GrpcResponse.<Insurance.FileClaimResponse>builder()
                .code("200").message("Claim filed successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Insurance.ReviewClaimResponse> reviewClaim(Insurance.ReviewClaimRequest request) {
        Optional<InsuranceClaim> claimOpt = insuranceClaimRepository.findByUuid(request.getClaimId());
        if (claimOpt.isEmpty()) {
            return GrpcResponse.<Insurance.ReviewClaimResponse>builder()
                    .code("404").message("Claim not found").status(Status.Code.NOT_FOUND).build();
        }
        InsuranceClaim claim = claimOpt.get();
        if (claim.getStatus() != ClaimStatus.CLAIM_FILED) {
            return GrpcResponse.<Insurance.ReviewClaimResponse>builder()
                    .code("409").message("Claim has already been decided").status(Status.Code.FAILED_PRECONDITION).build();
        }

        claim.setDecidedAt(LocalDate.now().toString());
        if (request.getApprove()) {
            claim.setStatus(ClaimStatus.CLAIM_APPROVED);
            claim.setPayoutAmount(BigDecimal.valueOf(request.getPayoutAmount()));
        } else {
            claim.setStatus(ClaimStatus.CLAIM_REJECTED);
        }
        insuranceClaimRepository.save(claim);

        Insurance.ReviewClaimResponse response = Insurance.ReviewClaimResponse.newBuilder()
                .setCode("200")
                .setMessage(request.getApprove() ? "Claim approved" : "Claim rejected")
                .setClaim(toClaimProto(claim))
                .build();

        return GrpcResponse.<Insurance.ReviewClaimResponse>builder()
                .code("200").message(response.getMessage()).status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Insurance.GetPolicyResponse> getPolicy(Insurance.GetPolicyRequest request) {
        Optional<InsurancePolicy> policyOpt = insurancePolicyRepository.findByUuid(request.getPolicyId());
        if (policyOpt.isEmpty()) {
            return GrpcResponse.<Insurance.GetPolicyResponse>builder()
                    .code("404").message("Insurance policy not found").status(Status.Code.NOT_FOUND).build();
        }

        Insurance.GetPolicyResponse response = Insurance.GetPolicyResponse.newBuilder()
                .setCode("200")
                .setMessage("Policy retrieved successfully")
                .setPolicy(toPolicyProto(policyOpt.get()))
                .build();

        return GrpcResponse.<Insurance.GetPolicyResponse>builder()
                .code("200").message("Policy retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Insurance.GetPoliciesByFarmerResponse> getPoliciesByFarmer(Insurance.GetPoliciesByFarmerRequest request) {
        List<InsurancePolicy> policies = insurancePolicyRepository.findByFarmers_Uuid(request.getFarmerId());

        Insurance.GetPoliciesByFarmerResponse response = Insurance.GetPoliciesByFarmerResponse.newBuilder()
                .addAllPolicies(policies.stream().map(this::toPolicyProto).toList())
                .build();

        return GrpcResponse.<Insurance.GetPoliciesByFarmerResponse>builder()
                .code("200").message("Policies retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Creditscore.ComputeCreditScoreResponse> computeCreditScore(Creditscore.ComputeCreditScoreRequest request) {
        Optional<FarmersDetails> farmerOpt = farmerRepository.findByUuid(request.getFarmerId());
        if (farmerOpt.isEmpty()) {
            return GrpcResponse.<Creditscore.ComputeCreditScoreResponse>builder()
                    .code("404").message("Farmer not found").status(Status.Code.NOT_FOUND).build();
        }
        FarmersDetails farmer = farmerOpt.get();

        int score = computeScoreForFarmer(farmer);
        CreditScoreBand band = bandFor(score);

        CreditScoreRecord record = creditScoreRecordRepository.save(CreditScoreRecord.builder()
                .uuid(UUID.randomUUID().toString())
                .farmers(farmer)
                .score(score)
                .band(band)
                .computedAt(LocalDate.now().toString())
                .build());

        Creditscore.ComputeCreditScoreResponse response = Creditscore.ComputeCreditScoreResponse.newBuilder()
                .setCode("200")
                .setMessage("Credit score computed successfully")
                .setScore(toCreditScoreProto(record))
                .build();

        return GrpcResponse.<Creditscore.ComputeCreditScoreResponse>builder()
                .code("200").message("Credit score computed successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Creditscore.GetCreditScoreResponse> getCreditScore(Creditscore.GetCreditScoreRequest request) {
        Optional<CreditScoreRecord> recordOpt = creditScoreRecordRepository.findTopByFarmers_UuidOrderByIdDesc(request.getFarmerId());
        if (recordOpt.isEmpty()) {
            return GrpcResponse.<Creditscore.GetCreditScoreResponse>builder()
                    .code("404").message("No credit score has been computed yet").status(Status.Code.NOT_FOUND).build();
        }

        Creditscore.GetCreditScoreResponse response = Creditscore.GetCreditScoreResponse.newBuilder()
                .setCode("200")
                .setMessage("Credit score retrieved successfully")
                .setScore(toCreditScoreProto(recordOpt.get()))
                .build();

        return GrpcResponse.<Creditscore.GetCreditScoreResponse>builder()
                .code("200").message("Credit score retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Creditscore.ReferToMfbResponse> referToMfb(Creditscore.ReferToMfbRequest request) {
        Optional<FarmersDetails> farmerOpt = farmerRepository.findByUuid(request.getFarmerId());
        if (farmerOpt.isEmpty()) {
            return GrpcResponse.<Creditscore.ReferToMfbResponse>builder()
                    .code("404").message("Farmer not found").status(Status.Code.NOT_FOUND).build();
        }

        Optional<CreditScoreRecord> recordOpt = creditScoreRecordRepository.findTopByFarmers_UuidOrderByIdDesc(request.getFarmerId());
        if (recordOpt.isEmpty()) {
            return GrpcResponse.<Creditscore.ReferToMfbResponse>builder()
                    .code("412").message("Credit score must be computed before a referral can be made").status(Status.Code.FAILED_PRECONDITION).build();
        }

        CreditScoreRecord creditScore = recordOpt.get();
        MfbReferral referral = mfbReferralRepository.save(MfbReferral.builder()
                .uuid(UUID.randomUUID().toString())
                .farmers(farmerOpt.get())
                .creditScore(creditScore)
                .mfbName(request.getMfbName())
                .referralFee(referralFeeForBand(creditScore.getBand()))
                .status(ReferralStatus.REFERRAL_PENDING)
                .referredAt(LocalDate.now().toString())
                .build());

        Creditscore.ReferToMfbResponse response = Creditscore.ReferToMfbResponse.newBuilder()
                .setCode("200")
                .setMessage("Farmer referred to MFB successfully")
                .setReferral(toReferralProto(referral))
                .build();

        return GrpcResponse.<Creditscore.ReferToMfbResponse>builder()
                .code("200").message("Farmer referred to MFB successfully").status(Status.Code.OK).data(response).build();
    }

    private int computeScoreForFarmer(FarmersDetails farmer) {
        int score = 300;

        FarmersKyc kyc = farmer.getKyc();
        if (kyc != null) {
            List<Documents> docs = kyc.getDocumentsAndIdentity();
            if (docs != null && !docs.isEmpty()
                    && docs.stream().allMatch(d -> d.getVerificationStatus() == VerificationStatus.APPROVED)) {
                score += 200;
            } else {
                score += 50;
            }
        }

        if (Boolean.TRUE.equals(farmer.getPhoneValidated())) {
            score += 50;
        }
        if (Boolean.TRUE.equals(farmer.getEmailValidated())) {
            score += 50;
        }

        List<Farms> farms = farmsRepository.findByFarmers_Uuid(farmer.getUuid());
        if (!farms.isEmpty()) {
            score += 50;
            boolean hasProduct = farms.stream()
                    .anyMatch(farm -> !farmProductsRepository.findByFarm_Uuid(farm.getUuid()).isEmpty());
            if (hasProduct) {
                score += 50;
            }
        }

        List<LoanAccount> loans = loanAccountRepository.findByFarmers_Uuid(farmer.getUuid());
        if (loans.stream().anyMatch(l -> l.getStatus() == LoanAccountStatus.LOAN_DEFAULTED)) {
            score -= 100;
        } else if (loans.stream().anyMatch(l -> l.getStatus() == LoanAccountStatus.LOAN_COMPLETED)) {
            score += 100;
        }

        return Math.max(300, Math.min(850, score));
    }

    private static CreditScoreBand bandFor(int score) {
        if (score >= 800) return CreditScoreBand.EXCELLENT;
        if (score >= 750) return CreditScoreBand.VERY_GOOD;
        if (score >= 650) return CreditScoreBand.GOOD;
        if (score >= 500) return CreditScoreBand.FAIR;
        return CreditScoreBand.POOR;
    }

    private static double interestRateForBand(CreditScoreBand band) {
        if (band == null) {
            return 0.25;
        }
        return switch (band) {
            case EXCELLENT -> 0.12;
            case VERY_GOOD -> 0.16;
            case GOOD -> 0.20;
            case FAIR -> 0.25;
            case POOR -> 0.30;
        };
    }

    private static BigDecimal referralFeeForBand(CreditScoreBand band) {
        return switch (band) {
            case POOR -> BigDecimal.valueOf(3000);
            case FAIR -> BigDecimal.valueOf(4750);
            case GOOD -> BigDecimal.valueOf(6500);
            case VERY_GOOD -> BigDecimal.valueOf(8250);
            case EXCELLENT -> BigDecimal.valueOf(10000);
        };
    }

    private Loan.LoanApplication toLoanApplicationProto(LoanApplication app) {
        return Loan.LoanApplication.newBuilder()
                .setId(nullToEmpty(app.getUuid()))
                .setFarmerId(app.getFarmers() != null ? nullToEmpty(app.getFarmers().getUuid()) : "")
                .setRequestedAmount(app.getRequestedAmount() == null ? 0 : app.getRequestedAmount().doubleValue())
                .setTermMonths(app.getTermMonths() == null ? 0 : app.getTermMonths())
                .setPurpose(nullToEmpty(app.getPurpose()))
                .setInterestRate(app.getInterestRate() == null ? 0 : app.getInterestRate())
                .setStatus(mapLoanApplicationStatus(app.getStatus()))
                .setAppliedAt(nullToEmpty(app.getAppliedAt()))
                .setDecidedAt(nullToEmpty(app.getDecidedAt()))
                .build();
    }

    private Loan.LoanAccount toLoanAccountProto(LoanAccount acc) {
        Loan.LoanAccount.Builder builder = Loan.LoanAccount.newBuilder()
                .setId(nullToEmpty(acc.getUuid()))
                .setApplicationId(acc.getApplication() != null ? nullToEmpty(acc.getApplication().getUuid()) : "")
                .setFarmerId(acc.getFarmers() != null ? nullToEmpty(acc.getFarmers().getUuid()) : "")
                .setPrincipalAmount(acc.getPrincipalAmount() == null ? 0 : acc.getPrincipalAmount().doubleValue())
                .setInterestRate(acc.getInterestRate() == null ? 0 : acc.getInterestRate())
                .setOutstandingBalance(acc.getOutstandingBalance() == null ? 0 : acc.getOutstandingBalance().doubleValue())
                .setStatus(mapLoanAccountStatus(acc.getStatus()))
                .setDisbursedAt(nullToEmpty(acc.getDisbursedAt()));

        if (acc.getRepayments() != null) {
            builder.addAllRepayments(acc.getRepayments().stream().map(this::toRepaymentProto).toList());
        }

        return builder.build();
    }

    private Loan.LoanRepayment toRepaymentProto(LoanRepayment repayment) {
        return Loan.LoanRepayment.newBuilder()
                .setId(nullToEmpty(repayment.getUuid()))
                .setAmount(repayment.getAmount() == null ? 0 : repayment.getAmount().doubleValue())
                .setPaidAt(nullToEmpty(repayment.getPaidAt()))
                .build();
    }

    private Insurance.InsurancePolicy toPolicyProto(InsurancePolicy policy) {
        Insurance.InsurancePolicy.Builder builder = Insurance.InsurancePolicy.newBuilder()
                .setId(nullToEmpty(policy.getUuid()))
                .setFarmerId(policy.getFarmers() != null ? nullToEmpty(policy.getFarmers().getUuid()) : "")
                .setPolicyType(nullToEmpty(policy.getPolicyType()))
                .setCoverageAmount(policy.getCoverageAmount() == null ? 0 : policy.getCoverageAmount().doubleValue())
                .setPremiumAmount(policy.getPremiumAmount() == null ? 0 : policy.getPremiumAmount().doubleValue())
                .setCommissionAmount(policy.getCommissionAmount() == null ? 0 : policy.getCommissionAmount().doubleValue())
                .setStatus(mapPolicyStatus(policy.getStatus()))
                .setStartDate(nullToEmpty(policy.getStartDate()))
                .setEndDate(nullToEmpty(policy.getEndDate()));

        if (policy.getClaims() != null) {
            builder.addAllClaims(policy.getClaims().stream().map(this::toClaimProto).toList());
        }

        return builder.build();
    }

    private Insurance.InsuranceClaim toClaimProto(InsuranceClaim claim) {
        return Insurance.InsuranceClaim.newBuilder()
                .setId(nullToEmpty(claim.getUuid()))
                .setClaimAmount(claim.getClaimAmount() == null ? 0 : claim.getClaimAmount().doubleValue())
                .setPayoutAmount(claim.getPayoutAmount() == null ? 0 : claim.getPayoutAmount().doubleValue())
                .setDescription(nullToEmpty(claim.getDescription()))
                .setStatus(mapClaimStatus(claim.getStatus()))
                .setFiledAt(nullToEmpty(claim.getFiledAt()))
                .setDecidedAt(nullToEmpty(claim.getDecidedAt()))
                .build();
    }

    private Creditscore.CreditScoreRecord toCreditScoreProto(CreditScoreRecord record) {
        return Creditscore.CreditScoreRecord.newBuilder()
                .setId(nullToEmpty(record.getUuid()))
                .setFarmerId(record.getFarmers() != null ? nullToEmpty(record.getFarmers().getUuid()) : "")
                .setScore(record.getScore() == null ? 0 : record.getScore())
                .setBand(mapCreditScoreBand(record.getBand()))
                .setComputedAt(nullToEmpty(record.getComputedAt()))
                .build();
    }

    private Creditscore.MfbReferral toReferralProto(MfbReferral referral) {
        return Creditscore.MfbReferral.newBuilder()
                .setId(nullToEmpty(referral.getUuid()))
                .setFarmerId(referral.getFarmers() != null ? nullToEmpty(referral.getFarmers().getUuid()) : "")
                .setMfbName(nullToEmpty(referral.getMfbName()))
                .setReferralFee(referral.getReferralFee() == null ? 0 : referral.getReferralFee().doubleValue())
                .setStatus(mapReferralStatus(referral.getStatus()))
                .setReferredAt(nullToEmpty(referral.getReferredAt()))
                .build();
    }

    private static fintech.Enum.LoanApplicationStatus mapLoanApplicationStatus(LoanApplicationStatus status) {
        return fintech.Enum.LoanApplicationStatus.valueOf(status.name());
    }

    private static fintech.Enum.LoanAccountStatus mapLoanAccountStatus(LoanAccountStatus status) {
        return fintech.Enum.LoanAccountStatus.valueOf(status.name());
    }

    private static fintech.Enum.PolicyStatus mapPolicyStatus(PolicyStatus status) {
        return fintech.Enum.PolicyStatus.valueOf(status.name());
    }

    private static fintech.Enum.ClaimStatus mapClaimStatus(ClaimStatus status) {
        return fintech.Enum.ClaimStatus.valueOf(status.name());
    }

    private static fintech.Enum.ReferralStatus mapReferralStatus(ReferralStatus status) {
        return fintech.Enum.ReferralStatus.valueOf(status.name());
    }

    private static fintech.Enum.CreditScoreBand mapCreditScoreBand(CreditScoreBand band) {
        return fintech.Enum.CreditScoreBand.valueOf(band.name());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
