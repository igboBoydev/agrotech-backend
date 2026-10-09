package com.catalyst.Home.Services;

import aiml.Advisory;
import aiml.Diagnosis;
import aiml.Yield;
import com.catalyst.Home.DTO.response.GrpcResponse;

public interface AiMlService {

    GrpcResponse<Diagnosis.DiagnoseCropIssueResponse> diagnoseCropIssue(Diagnosis.DiagnoseCropIssueRequest request);

    GrpcResponse<Diagnosis.GetDiagnosisHistoryResponse> getDiagnosisHistory(Diagnosis.GetDiagnosisHistoryRequest request);

    GrpcResponse<Yield.PredictYieldResponse> predictYield(Yield.PredictYieldRequest request);

    GrpcResponse<Yield.GetLatestYieldPredictionResponse> getLatestYieldPrediction(Yield.GetLatestYieldPredictionRequest request);

    GrpcResponse<Advisory.GetAgronomicAdviceResponse> getAgronomicAdvice(Advisory.GetAgronomicAdviceRequest request);
}
