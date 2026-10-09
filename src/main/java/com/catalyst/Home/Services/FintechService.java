package com.catalyst.Home.Services;

import com.catalyst.Home.DTO.response.GrpcResponse;
import fintech.Creditscore;
import fintech.Insurance;
import fintech.Loan;

public interface FintechService {

    GrpcResponse<Loan.ApplyForLoanResponse> applyForLoan(Loan.ApplyForLoanRequest request);

    GrpcResponse<Loan.ReviewLoanApplicationResponse> reviewLoanApplication(Loan.ReviewLoanApplicationRequest request);

    GrpcResponse<Loan.RecordRepaymentResponse> recordRepayment(Loan.RecordRepaymentRequest request);

    GrpcResponse<Loan.GetLoanResponse> getLoan(Loan.GetLoanRequest request);

    GrpcResponse<Loan.GetLoansByFarmerResponse> getLoansByFarmer(Loan.GetLoansByFarmerRequest request);

    GrpcResponse<Insurance.CreatePolicyResponse> createPolicy(Insurance.CreatePolicyRequest request);

    GrpcResponse<Insurance.FileClaimResponse> fileClaim(Insurance.FileClaimRequest request);

    GrpcResponse<Insurance.ReviewClaimResponse> reviewClaim(Insurance.ReviewClaimRequest request);

    GrpcResponse<Insurance.GetPolicyResponse> getPolicy(Insurance.GetPolicyRequest request);

    GrpcResponse<Insurance.GetPoliciesByFarmerResponse> getPoliciesByFarmer(Insurance.GetPoliciesByFarmerRequest request);

    GrpcResponse<Creditscore.ComputeCreditScoreResponse> computeCreditScore(Creditscore.ComputeCreditScoreRequest request);

    GrpcResponse<Creditscore.GetCreditScoreResponse> getCreditScore(Creditscore.GetCreditScoreRequest request);

    GrpcResponse<Creditscore.ReferToMfbResponse> referToMfb(Creditscore.ReferToMfbRequest request);
}
