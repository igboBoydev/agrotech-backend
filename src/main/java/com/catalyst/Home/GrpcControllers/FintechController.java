package com.catalyst.Home.GrpcControllers;

import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Services.FintechService;
import fintech.Creditscore;
import fintech.FintechServiceGrpc;
import fintech.Insurance;
import fintech.Loan;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class FintechController extends FintechServiceGrpc.FintechServiceImplBase {

    private final FintechService fintechService;

    public FintechController(FintechService fintechService) {
        this.fintechService = fintechService;
    }

    @Override
    public void applyForLoan(Loan.ApplyForLoanRequest request, StreamObserver<Loan.ApplyForLoanResponse> responseObserver) {
        respond(fintechService.applyForLoan(request), responseObserver);
    }

    @Override
    public void reviewLoanApplication(Loan.ReviewLoanApplicationRequest request, StreamObserver<Loan.ReviewLoanApplicationResponse> responseObserver) {
        respond(fintechService.reviewLoanApplication(request), responseObserver);
    }

    @Override
    public void recordRepayment(Loan.RecordRepaymentRequest request, StreamObserver<Loan.RecordRepaymentResponse> responseObserver) {
        respond(fintechService.recordRepayment(request), responseObserver);
    }

    @Override
    public void getLoan(Loan.GetLoanRequest request, StreamObserver<Loan.GetLoanResponse> responseObserver) {
        respond(fintechService.getLoan(request), responseObserver);
    }

    @Override
    public void getLoansByFarmer(Loan.GetLoansByFarmerRequest request, StreamObserver<Loan.GetLoansByFarmerResponse> responseObserver) {
        respond(fintechService.getLoansByFarmer(request), responseObserver);
    }

    @Override
    public void createPolicy(Insurance.CreatePolicyRequest request, StreamObserver<Insurance.CreatePolicyResponse> responseObserver) {
        respond(fintechService.createPolicy(request), responseObserver);
    }

    @Override
    public void fileClaim(Insurance.FileClaimRequest request, StreamObserver<Insurance.FileClaimResponse> responseObserver) {
        respond(fintechService.fileClaim(request), responseObserver);
    }

    @Override
    public void reviewClaim(Insurance.ReviewClaimRequest request, StreamObserver<Insurance.ReviewClaimResponse> responseObserver) {
        respond(fintechService.reviewClaim(request), responseObserver);
    }

    @Override
    public void getPolicy(Insurance.GetPolicyRequest request, StreamObserver<Insurance.GetPolicyResponse> responseObserver) {
        respond(fintechService.getPolicy(request), responseObserver);
    }

    @Override
    public void getPoliciesByFarmer(Insurance.GetPoliciesByFarmerRequest request, StreamObserver<Insurance.GetPoliciesByFarmerResponse> responseObserver) {
        respond(fintechService.getPoliciesByFarmer(request), responseObserver);
    }

    @Override
    public void computeCreditScore(Creditscore.ComputeCreditScoreRequest request, StreamObserver<Creditscore.ComputeCreditScoreResponse> responseObserver) {
        respond(fintechService.computeCreditScore(request), responseObserver);
    }

    @Override
    public void getCreditScore(Creditscore.GetCreditScoreRequest request, StreamObserver<Creditscore.GetCreditScoreResponse> responseObserver) {
        respond(fintechService.getCreditScore(request), responseObserver);
    }

    @Override
    public void referToMfb(Creditscore.ReferToMfbRequest request, StreamObserver<Creditscore.ReferToMfbResponse> responseObserver) {
        respond(fintechService.referToMfb(request), responseObserver);
    }

    private <T> void respond(GrpcResponse<T> grpcResponse, StreamObserver<T> responseObserver) {
        if (!grpcResponse.getCode().equals("200")) {
            Status status = Status.fromCode(grpcResponse.getStatus()).withDescription(grpcResponse.getMessage());
            StatusRuntimeException statusException = status.asRuntimeException();
            responseObserver.onError(statusException);
            return;
        }
        responseObserver.onNext(grpcResponse.getData());
        responseObserver.onCompleted();
    }
}
