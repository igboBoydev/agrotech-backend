package com.catalyst.Home.Services.Impl;

import aiml.Advisory;
import aiml.Diagnosis;
import aiml.Yield;
import com.catalyst.Home.DTO.response.GrpcResponse;
import com.catalyst.Home.Entity.Farms;
import com.catalyst.Home.Entity.VegetationIndex;
import com.catalyst.Home.Repositories.CropDiagnosisRepository;
import com.catalyst.Home.Repositories.FarmsRepository;
import com.catalyst.Home.Repositories.VegetationIndexRepository;
import com.catalyst.Home.Repositories.YieldPredictionRepository;
import com.catalyst.Home.Services.AiMlService;
import com.catalyst.Home.Utils.CropSymptom;
import com.catalyst.Home.Utils.VegetationHealthBand;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based crop diagnosis and heuristic yield estimation.
 * This is a small hardcoded knowledge base, not a trained ML model — no image
 * pipeline or model files exist in this codebase. Documented here rather than
 * left implicit so this domain doesn't overclaim what "AI" means.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiMlServiceImpl implements AiMlService {
    private final FarmsRepository farmsRepository;
    private final CropDiagnosisRepository cropDiagnosisRepository;
    private final YieldPredictionRepository yieldPredictionRepository;
    private final VegetationIndexRepository vegetationIndexRepository;

    private record DiseaseRule(Set<CropSymptom> requiredSymptoms, String name, String action, double confidence) {}

    private static final Map<String, List<DiseaseRule>> DISEASE_RULES = Map.of(
            "maize", List.of(
                    new DiseaseRule(EnumSet.of(CropSymptom.SYMPTOM_LEAF_DISCOLORATION, CropSymptom.SYMPTOM_LEAF_SPOTS),
                            "Maize Leaf Blight", "Apply an appropriate foliar fungicide and remove severely infected leaves", 0.7),
                    new DiseaseRule(EnumSet.of(CropSymptom.SYMPTOM_PEST_SIGHTED, CropSymptom.SYMPTOM_STUNTED_GROWTH),
                            "Fall Armyworm infestation", "Apply a recommended pesticide and scout the field weekly", 0.75),
                    new DiseaseRule(EnumSet.of(CropSymptom.SYMPTOM_WILTING),
                            "Possible drought stress or root rot", "Check soil moisture levels and improve field drainage", 0.5)
            ),
            "cassava", List.of(
                    new DiseaseRule(EnumSet.of(CropSymptom.SYMPTOM_LEAF_DISCOLORATION, CropSymptom.SYMPTOM_LEAF_SPOTS),
                            "Cassava Mosaic Disease", "Remove and destroy infected plants; replant with disease-resistant cuttings", 0.7),
                    new DiseaseRule(EnumSet.of(CropSymptom.SYMPTOM_PEST_SIGHTED),
                            "Cassava mealybug infestation", "Introduce natural predators or apply an approved insecticide", 0.6)
            ),
            "rice", List.of(
                    new DiseaseRule(EnumSet.of(CropSymptom.SYMPTOM_LEAF_DISCOLORATION, CropSymptom.SYMPTOM_WILTING),
                            "Rice Blast", "Apply a systemic fungicide and avoid excess nitrogen fertilization", 0.65),
                    new DiseaseRule(EnumSet.of(CropSymptom.SYMPTOM_STUNTED_GROWTH, CropSymptom.SYMPTOM_PEST_SIGHTED),
                            "Rice stem borer infestation", "Apply a recommended insecticide and remove affected stems", 0.6)
            )
    );

    private static final String FALLBACK_DIAGNOSIS = "Unable to determine a specific cause from the symptoms provided";
    private static final String FALLBACK_ACTION = "Consult a local agronomist or extension officer";

    private static final Map<String, Double> BASE_TONS_PER_ACRE = Map.of(
            "maize", 2.5,
            "cassava", 10.0,
            "rice", 3.0,
            "yam", 8.0
    );
    private static final double DEFAULT_BASE_TONS_PER_ACRE = 2.0;
    private static final double DEFAULT_ACRES = 1.0;
    private static final Pattern LEADING_NUMBER = Pattern.compile("[0-9]+(\\.[0-9]+)?");

    private static final Map<String, String> AGRONOMIC_ADVICE = Map.of(
            "maize|vegetative", "Ensure adequate nitrogen application and maintain soil moisture during the vegetative stage.",
            "maize|flowering", "Avoid moisture stress during tasseling and silking — this is the most drought-sensitive stage.",
            "cassava|planting", "Plant healthy, disease-free stem cuttings at the onset of rains for best establishment.",
            "rice|vegetative", "Maintain standing water at roughly 5cm depth and apply nitrogen in split doses."
    );
    private static final String FALLBACK_ADVICE =
            "General guidance: monitor soil moisture, watch for pests, and follow recommended fertilizer timing for your crop and region.";

    @Override
    @Transactional
    public GrpcResponse<Diagnosis.DiagnoseCropIssueResponse> diagnoseCropIssue(Diagnosis.DiagnoseCropIssueRequest request) {
        Optional<Farms> farmOpt = farmsRepository.findByUuid(request.getFarmId());
        if (farmOpt.isEmpty()) {
            return GrpcResponse.<Diagnosis.DiagnoseCropIssueResponse>builder()
                    .code("404").message("Farm not found").status(Status.Code.NOT_FOUND).build();
        }

        Set<CropSymptom> submitted = request.getSymptomsList().stream()
                .map(AiMlServiceImpl::mapSymptom)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(CropSymptom.class)));

        Optional<DiseaseRule> match = findBestMatch(request.getCropType(), submitted);

        String diagnosisResult = match.map(DiseaseRule::name).orElse(FALLBACK_DIAGNOSIS);
        String action = match.map(DiseaseRule::action).orElse(FALLBACK_ACTION);
        double confidence = match.map(DiseaseRule::confidence).orElse(0.0);

        com.catalyst.Home.Entity.CropDiagnosis diagnosis = cropDiagnosisRepository.save(
                com.catalyst.Home.Entity.CropDiagnosis.builder()
                        .uuid(UUID.randomUUID().toString())
                        .farm(farmOpt.get())
                        .cropType(request.getCropType())
                        .symptoms(joinSymptoms(submitted))
                        .diagnosisResult(diagnosisResult)
                        .confidence(confidence)
                        .recommendedAction(action)
                        .createdAt(LocalDate.now().toString())
                        .build());

        Diagnosis.DiagnoseCropIssueResponse response = Diagnosis.DiagnoseCropIssueResponse.newBuilder()
                .setCode("200")
                .setMessage("Diagnosis completed")
                .setDiagnosis(toDiagnosisProto(diagnosis))
                .build();

        return GrpcResponse.<Diagnosis.DiagnoseCropIssueResponse>builder()
                .code("200").message("Diagnosis completed").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Diagnosis.GetDiagnosisHistoryResponse> getDiagnosisHistory(Diagnosis.GetDiagnosisHistoryRequest request) {
        List<com.catalyst.Home.Entity.CropDiagnosis> diagnoses =
                cropDiagnosisRepository.findByFarm_UuidOrderByIdDesc(request.getFarmId());

        Diagnosis.GetDiagnosisHistoryResponse response = Diagnosis.GetDiagnosisHistoryResponse.newBuilder()
                .addAllDiagnoses(diagnoses.stream().map(this::toDiagnosisProto).toList())
                .build();

        return GrpcResponse.<Diagnosis.GetDiagnosisHistoryResponse>builder()
                .code("200").message("Diagnosis history retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Yield.PredictYieldResponse> predictYield(Yield.PredictYieldRequest request) {
        Optional<Farms> farmOpt = farmsRepository.findByUuid(request.getFarmId());
        if (farmOpt.isEmpty()) {
            return GrpcResponse.<Yield.PredictYieldResponse>builder()
                    .code("404").message("Farm not found").status(Status.Code.NOT_FOUND).build();
        }
        Farms farm = farmOpt.get();
        String cropType = farm.getPlantType() == null ? "" : farm.getPlantType();

        double baseTonsPerAcre = BASE_TONS_PER_ACRE.get(cropType.toLowerCase());
        if (!BASE_TONS_PER_ACRE.containsKey(cropType.toLowerCase())) {
            log.warn("No base yield figure for crop type '{}', using default {} tons/acre", cropType, DEFAULT_BASE_TONS_PER_ACRE);
            baseTonsPerAcre = DEFAULT_BASE_TONS_PER_ACRE;
        }

        double acres = parseAcres(farm.getLandSize());

        Optional<VegetationIndex> latestVegetation = vegetationIndexRepository.findTopByFarm_UuidOrderByIdDesc(farm.getUuid());
        double multiplier = latestVegetation.map(v -> multiplierForBand(v.getHealthBand())).orElse(1.0);

        double predictedTons = baseTonsPerAcre * acres * multiplier;

        com.catalyst.Home.Entity.YieldPrediction prediction = yieldPredictionRepository.save(
                com.catalyst.Home.Entity.YieldPrediction.builder()
                        .uuid(UUID.randomUUID().toString())
                        .farm(farm)
                        .cropType(cropType)
                        .predictedYieldTons(predictedTons)
                        .healthMultiplierUsed(multiplier)
                        .computedAt(LocalDate.now().toString())
                        .build());

        Yield.PredictYieldResponse response = Yield.PredictYieldResponse.newBuilder()
                .setCode("200")
                .setMessage("Yield prediction computed successfully")
                .setPrediction(toYieldProto(prediction))
                .build();

        return GrpcResponse.<Yield.PredictYieldResponse>builder()
                .code("200").message("Yield prediction computed successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Yield.GetLatestYieldPredictionResponse> getLatestYieldPrediction(Yield.GetLatestYieldPredictionRequest request) {
        Optional<com.catalyst.Home.Entity.YieldPrediction> predictionOpt =
                yieldPredictionRepository.findTopByFarm_UuidOrderByIdDesc(request.getFarmId());
        if (predictionOpt.isEmpty()) {
            return GrpcResponse.<Yield.GetLatestYieldPredictionResponse>builder()
                    .code("404").message("No yield prediction computed yet").status(Status.Code.NOT_FOUND).build();
        }

        Yield.GetLatestYieldPredictionResponse response = Yield.GetLatestYieldPredictionResponse.newBuilder()
                .setCode("200")
                .setMessage("Latest yield prediction retrieved successfully")
                .setPrediction(toYieldProto(predictionOpt.get()))
                .build();

        return GrpcResponse.<Yield.GetLatestYieldPredictionResponse>builder()
                .code("200").message("Latest yield prediction retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    public GrpcResponse<Advisory.GetAgronomicAdviceResponse> getAgronomicAdvice(Advisory.GetAgronomicAdviceRequest request) {
        String key = request.getCropType().toLowerCase() + "|" + request.getGrowthStage().toLowerCase();
        String advice = AGRONOMIC_ADVICE.getOrDefault(key, FALLBACK_ADVICE);

        Advisory.GetAgronomicAdviceResponse response = Advisory.GetAgronomicAdviceResponse.newBuilder()
                .setCode("200")
                .setMessage("Agronomic advice retrieved successfully")
                .setAdvice(advice)
                .build();

        return GrpcResponse.<Advisory.GetAgronomicAdviceResponse>builder()
                .code("200").message("Agronomic advice retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    private static Optional<DiseaseRule> findBestMatch(String cropType, Set<CropSymptom> submitted) {
        if (cropType == null || submitted.isEmpty()) {
            return Optional.empty();
        }
        List<DiseaseRule> rules = DISEASE_RULES.get(cropType.toLowerCase());
        if (rules == null) {
            return Optional.empty();
        }

        DiseaseRule best = null;
        int bestScore = 0;
        for (DiseaseRule rule : rules) {
            int score = (int) rule.requiredSymptoms().stream().filter(submitted::contains).count();
            if (score > bestScore) {
                bestScore = score;
                best = rule;
            }
        }
        return bestScore > 0 ? Optional.of(best) : Optional.empty();
    }

    private static double parseAcres(String landSize) {
        if (landSize == null) {
            return DEFAULT_ACRES;
        }
        Matcher matcher = LEADING_NUMBER.matcher(landSize);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group());
            } catch (NumberFormatException e) {
                log.warn("Could not parse acreage from land size '{}', using default {} acres", landSize, DEFAULT_ACRES);
            }
        }
        return DEFAULT_ACRES;
    }

    private static double multiplierForBand(VegetationHealthBand band) {
        if (band == null) {
            return 1.0;
        }
        return switch (band) {
            case VEGETATION_POOR -> 0.6;
            case VEGETATION_MODERATE -> 0.8;
            case VEGETATION_GOOD -> 1.0;
            case VEGETATION_EXCELLENT -> 1.2;
        };
    }

    private static String joinSymptoms(Set<CropSymptom> symptoms) {
        return symptoms.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
    }

    private List<aiml.Enum.CropSymptom> parseStoredSymptoms(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(CropSymptom::valueOf)
                .map(AiMlServiceImpl::mapSymptom)
                .toList();
    }

    private Diagnosis.CropDiagnosis toDiagnosisProto(com.catalyst.Home.Entity.CropDiagnosis diagnosis) {
        return Diagnosis.CropDiagnosis.newBuilder()
                .setId(nullToEmpty(diagnosis.getUuid()))
                .setFarmId(diagnosis.getFarm() != null ? nullToEmpty(diagnosis.getFarm().getUuid()) : "")
                .setCropType(nullToEmpty(diagnosis.getCropType()))
                .addAllSymptoms(parseStoredSymptoms(diagnosis.getSymptoms()))
                .setDiagnosisResult(nullToEmpty(diagnosis.getDiagnosisResult()))
                .setConfidence(diagnosis.getConfidence() == null ? 0 : diagnosis.getConfidence())
                .setRecommendedAction(nullToEmpty(diagnosis.getRecommendedAction()))
                .setCreatedAt(nullToEmpty(diagnosis.getCreatedAt()))
                .build();
    }

    private Yield.YieldPrediction toYieldProto(com.catalyst.Home.Entity.YieldPrediction prediction) {
        return Yield.YieldPrediction.newBuilder()
                .setId(nullToEmpty(prediction.getUuid()))
                .setFarmId(prediction.getFarm() != null ? nullToEmpty(prediction.getFarm().getUuid()) : "")
                .setCropType(nullToEmpty(prediction.getCropType()))
                .setPredictedYieldTons(prediction.getPredictedYieldTons() == null ? 0 : prediction.getPredictedYieldTons())
                .setHealthMultiplierUsed(prediction.getHealthMultiplierUsed() == null ? 0 : prediction.getHealthMultiplierUsed())
                .setComputedAt(nullToEmpty(prediction.getComputedAt()))
                .build();
    }

    private static aiml.Enum.CropSymptom mapSymptom(CropSymptom symptom) {
        return aiml.Enum.CropSymptom.valueOf(symptom.name());
    }

    private static CropSymptom mapSymptom(aiml.Enum.CropSymptom symptom) {
        return CropSymptom.valueOf(symptom.name());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
