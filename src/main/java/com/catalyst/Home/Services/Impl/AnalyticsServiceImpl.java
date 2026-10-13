package com.catalyst.Home.Services.Impl;

import analytics.Insights;
import analytics.SubscriptionOuterClass;
import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Entity.FarmProducts;
import com.catalyst.Home.Entity.Farms;
import com.catalyst.Home.Entity.MarketplaceOrder;
import com.catalyst.Home.Entity.VegetationIndex;
import com.catalyst.Home.Repositories.FarmProductsRepository;
import com.catalyst.Home.Repositories.FarmsRepository;
import com.catalyst.Home.Repositories.MarketplaceOrderRepository;
import com.catalyst.Home.Repositories.SubscriptionRepository;
import com.catalyst.Home.Repositories.VegetationIndexRepository;
import com.catalyst.Home.Services.AnalyticsService;
import com.catalyst.Home.Utils.OrderStatus;
import com.catalyst.Home.Utils.SubscriptionStatus;
import com.catalyst.Home.Utils.SubscriptionTier;
import com.catalyst.Home.Utils.VegetationHealthBand;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {
    private final SubscriptionRepository subscriptionRepository;
    private final FarmsRepository farmsRepository;
    private final FarmProductsRepository farmProductsRepository;
    private final MarketplaceOrderRepository marketplaceOrderRepository;
    private final VegetationIndexRepository vegetationIndexRepository;

    @Override
    @Transactional
    public GrpcResponse<SubscriptionOuterClass.CreateSubscriptionResponse> createSubscription(SubscriptionOuterClass.CreateSubscriptionRequest request) {
        SubscriptionTier tier = mapTier(request.getTier());
        LocalDate startDate = LocalDate.now();

        com.catalyst.Home.Entity.Subscription subscription = subscriptionRepository.save(
                com.catalyst.Home.Entity.Subscription.builder()
                        .uuid(UUID.randomUUID().toString())
                        .businessId(request.getBusinessId())
                        .tier(tier)
                        .price(priceForTier(tier))
                        .status(SubscriptionStatus.SUBSCRIPTION_ACTIVE)
                        .startDate(startDate.toString())
                        .endDate(startDate.plusYears(1).toString())
                        .build());

        SubscriptionOuterClass.CreateSubscriptionResponse response = SubscriptionOuterClass.CreateSubscriptionResponse.newBuilder()
                .setCode("200")
                .setMessage("Subscription created successfully")
                .setSubscription(toSubscriptionProto(subscription))
                .build();

        return GrpcResponse.<SubscriptionOuterClass.CreateSubscriptionResponse>builder()
                .code("200").message("Subscription created successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<SubscriptionOuterClass.GetSubscriptionResponse> getSubscription(SubscriptionOuterClass.GetSubscriptionRequest request) {
        Optional<com.catalyst.Home.Entity.Subscription> subscriptionOpt =
                subscriptionRepository.findTopByBusinessIdOrderByIdDesc(request.getBusinessId());
        if (subscriptionOpt.isEmpty()) {
            return GrpcResponse.<SubscriptionOuterClass.GetSubscriptionResponse>builder()
                    .code("404").message("No subscription found for this business").status(Status.Code.NOT_FOUND).build();
        }

        SubscriptionOuterClass.GetSubscriptionResponse response = SubscriptionOuterClass.GetSubscriptionResponse.newBuilder()
                .setCode("200")
                .setMessage("Subscription retrieved successfully")
                .setSubscription(toSubscriptionProto(subscriptionOpt.get()))
                .build();

        return GrpcResponse.<SubscriptionOuterClass.GetSubscriptionResponse>builder()
                .code("200").message("Subscription retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<SubscriptionOuterClass.CancelSubscriptionResponse> cancelSubscription(SubscriptionOuterClass.CancelSubscriptionRequest request) {
        Optional<com.catalyst.Home.Entity.Subscription> subscriptionOpt =
                subscriptionRepository.findByUuid(request.getSubscriptionId());
        if (subscriptionOpt.isEmpty()) {
            return GrpcResponse.<SubscriptionOuterClass.CancelSubscriptionResponse>builder()
                    .code("404").message("Subscription not found").status(Status.Code.NOT_FOUND).build();
        }
        com.catalyst.Home.Entity.Subscription subscription = subscriptionOpt.get();
        if (subscription.getStatus() != SubscriptionStatus.SUBSCRIPTION_ACTIVE) {
            return GrpcResponse.<SubscriptionOuterClass.CancelSubscriptionResponse>builder()
                    .code("409").message("Subscription is not active").status(Status.Code.FAILED_PRECONDITION).build();
        }

        subscription.setStatus(SubscriptionStatus.SUBSCRIPTION_CANCELLED);
        subscriptionRepository.save(subscription);

        SubscriptionOuterClass.CancelSubscriptionResponse response = SubscriptionOuterClass.CancelSubscriptionResponse.newBuilder()
                .setCode("200")
                .setMessage("Subscription cancelled successfully")
                .setSubscription(toSubscriptionProto(subscription))
                .build();

        return GrpcResponse.<SubscriptionOuterClass.CancelSubscriptionResponse>builder()
                .code("200").message("Subscription cancelled successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Insights.GetRegionalCropSummaryResponse> getRegionalCropSummary(Insights.GetRegionalCropSummaryRequest request) {
        if (!hasAccess(request.getBusinessId(), SubscriptionTier.TIER_AGRO_DEALER)) {
            return GrpcResponse.<Insights.GetRegionalCropSummaryResponse>builder()
                    .code("403").message("Requires an active TIER_AGRO_DEALER subscription or higher").status(Status.Code.PERMISSION_DENIED).build();
        }

        List<Farms> farms = farmsRepository.findByStateAndPlantType(request.getState(), request.getPlantType());

        double totalQuantity = 0;
        double totalPrice = 0;
        int productCount = 0;
        for (Farms farm : farms) {
            List<FarmProducts> products = farmProductsRepository.findByFarm_Uuid(farm.getUuid());
            for (FarmProducts product : products) {
                totalQuantity += product.getQuantity() == null ? 0 : product.getQuantity();
                if (product.getPricePerUnit() != null) {
                    totalPrice += product.getPricePerUnit().doubleValue();
                    productCount++;
                }
            }
        }

        Insights.GetRegionalCropSummaryResponse response = Insights.GetRegionalCropSummaryResponse.newBuilder()
                .setCode("200")
                .setMessage("Regional crop summary retrieved successfully")
                .setFarmCount(farms.size())
                .setTotalQuantityListed(totalQuantity)
                .setAveragePricePerUnit(productCount == 0 ? 0 : totalPrice / productCount)
                .build();

        return GrpcResponse.<Insights.GetRegionalCropSummaryResponse>builder()
                .code("200").message("Regional crop summary retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Insights.GetMarketplaceActivitySummaryResponse> getMarketplaceActivitySummary(Insights.GetMarketplaceActivitySummaryRequest request) {
        if (!hasAccess(request.getBusinessId(), SubscriptionTier.TIER_FOOD_PROCESSOR)) {
            return GrpcResponse.<Insights.GetMarketplaceActivitySummaryResponse>builder()
                    .code("403").message("Requires an active TIER_FOOD_PROCESSOR subscription or higher").status(Status.Code.PERMISSION_DENIED).build();
        }

        List<MarketplaceOrder> completedOrders = marketplaceOrderRepository.findByStatus(OrderStatus.ORDER_COMPLETED);
        double gmv = completedOrders.stream()
                .map(MarketplaceOrder::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        Insights.GetMarketplaceActivitySummaryResponse response = Insights.GetMarketplaceActivitySummaryResponse.newBuilder()
                .setCode("200")
                .setMessage("Marketplace activity summary retrieved successfully")
                .setCompletedOrderCount(completedOrders.size())
                .setGrossMerchandiseValue(gmv)
                .setAverageOrderValue(completedOrders.isEmpty() ? 0 : gmv / completedOrders.size())
                .build();

        return GrpcResponse.<Insights.GetMarketplaceActivitySummaryResponse>builder()
                .code("200").message("Marketplace activity summary retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Insights.GetFoodSecurityIndicatorsResponse> getFoodSecurityIndicators(Insights.GetFoodSecurityIndicatorsRequest request) {
        if (!hasAccess(request.getBusinessId(), SubscriptionTier.TIER_GOV_NGO)) {
            return GrpcResponse.<Insights.GetFoodSecurityIndicatorsResponse>builder()
                    .code("403").message("Requires an active TIER_GOV_NGO subscription").status(Status.Code.PERMISSION_DENIED).build();
        }

        List<Farms> farms = farmsRepository.findByState(request.getState());

        Map<VegetationHealthBand, Integer> bandCounts = new EnumMap<>(VegetationHealthBand.class);
        for (Farms farm : farms) {
            Optional<VegetationIndex> latest = vegetationIndexRepository.findTopByFarm_UuidOrderByIdDesc(farm.getUuid());
            latest.map(VegetationIndex::getHealthBand)
                    .ifPresent(band -> bandCounts.merge(band, 1, Integer::sum));
        }

        Insights.GetFoodSecurityIndicatorsResponse.Builder responseBuilder = Insights.GetFoodSecurityIndicatorsResponse.newBuilder()
                .setCode("200")
                .setMessage("Food security indicators retrieved successfully")
                .setFarmCount(farms.size());

        bandCounts.forEach((band, count) -> responseBuilder.addVegetationHealthDistribution(
                Insights.VegetationHealthBandCount.newBuilder().setBand(band.name()).setCount(count).build()));

        Insights.GetFoodSecurityIndicatorsResponse response = responseBuilder.build();

        return GrpcResponse.<Insights.GetFoodSecurityIndicatorsResponse>builder()
                .code("200").message("Food security indicators retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    private boolean hasAccess(String businessId, SubscriptionTier requiredTier) {
        Optional<com.catalyst.Home.Entity.Subscription> subscriptionOpt =
                subscriptionRepository.findTopByBusinessIdOrderByIdDesc(businessId);
        if (subscriptionOpt.isEmpty()) {
            return false;
        }
        com.catalyst.Home.Entity.Subscription subscription = subscriptionOpt.get();
        if (subscription.getStatus() != SubscriptionStatus.SUBSCRIPTION_ACTIVE) {
            return false;
        }
        return subscription.getTier().ordinal() >= requiredTier.ordinal();
    }

    private static BigDecimal priceForTier(SubscriptionTier tier) {
        return switch (tier) {
            case TIER_AGRO_DEALER -> BigDecimal.valueOf(200_000);
            case TIER_FOOD_PROCESSOR -> BigDecimal.valueOf(1_400_000);
            case TIER_GOV_NGO -> BigDecimal.valueOf(27_500_000);
        };
    }

    private SubscriptionOuterClass.Subscription toSubscriptionProto(com.catalyst.Home.Entity.Subscription subscription) {
        return SubscriptionOuterClass.Subscription.newBuilder()
                .setId(nullToEmpty(subscription.getUuid()))
                .setBusinessId(nullToEmpty(subscription.getBusinessId()))
                .setTier(mapTier(subscription.getTier()))
                .setPrice(subscription.getPrice() == null ? 0 : subscription.getPrice().doubleValue())
                .setStatus(mapStatus(subscription.getStatus()))
                .setStartDate(nullToEmpty(subscription.getStartDate()))
                .setEndDate(nullToEmpty(subscription.getEndDate()))
                .build();
    }

    private static analytics.Enum.SubscriptionTier mapTier(SubscriptionTier tier) {
        return analytics.Enum.SubscriptionTier.valueOf(tier.name());
    }

    private static SubscriptionTier mapTier(analytics.Enum.SubscriptionTier tier) {
        return SubscriptionTier.valueOf(tier.name());
    }

    private static analytics.Enum.SubscriptionStatus mapStatus(SubscriptionStatus status) {
        return analytics.Enum.SubscriptionStatus.valueOf(status.name());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
