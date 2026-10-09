package com.catalyst.Home.GrpcControllers;

import aiml.Advisory;
import aiml.AiMlServiceGrpc;
import aiml.Diagnosis;
import aiml.Yield;
import com.catalyst.Home.DTO.response.GrpcResponse;
import com.catalyst.Home.Services.AiMlService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class AiMlController extends AiMlServiceGrpc.AiMlServiceImplBase {

    private final AiMlService aiMlService;

    public AiMlController(AiMlService aiMlService) {
        this.aiMlService = aiMlService;
    }

    @Override
    public void diagnoseCropIssue(Diagnosis.DiagnoseCropIssueRequest request, StreamObserver<Diagnosis.DiagnoseCropIssueResponse> responseObserver) {
        respond(aiMlService.diagnoseCropIssue(request), responseObserver);
    }

    @Override
    public void getDiagnosisHistory(Diagnosis.GetDiagnosisHistoryRequest request, StreamObserver<Diagnosis.GetDiagnosisHistoryResponse> responseObserver) {
        respond(aiMlService.getDiagnosisHistory(request), responseObserver);
    }

    @Override
    public void predictYield(Yield.PredictYieldRequest request, StreamObserver<Yield.PredictYieldResponse> responseObserver) {
        respond(aiMlService.predictYield(request), responseObserver);
    }

    @Override
    public void getLatestYieldPrediction(Yield.GetLatestYieldPredictionRequest request, StreamObserver<Yield.GetLatestYieldPredictionResponse> responseObserver) {
        respond(aiMlService.getLatestYieldPrediction(request), responseObserver);
    }

    @Override
    public void getAgronomicAdvice(Advisory.GetAgronomicAdviceRequest request, StreamObserver<Advisory.GetAgronomicAdviceResponse> responseObserver) {
        respond(aiMlService.getAgronomicAdvice(request), responseObserver);
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
