package com.catalyst.Home.Services;

import com.catalyst.Home.Dto.response.GrpcResponse;
import analytics.Insights;
import analytics.SubscriptionOuterClass;

public interface AnalyticsService {

    GrpcResponse<SubscriptionOuterClass.CreateSubscriptionResponse> createSubscription(SubscriptionOuterClass.CreateSubscriptionRequest request);

    GrpcResponse<SubscriptionOuterClass.GetSubscriptionResponse> getSubscription(SubscriptionOuterClass.GetSubscriptionRequest request);

    GrpcResponse<SubscriptionOuterClass.CancelSubscriptionResponse> cancelSubscription(SubscriptionOuterClass.CancelSubscriptionRequest request);

    GrpcResponse<Insights.GetRegionalCropSummaryResponse> getRegionalCropSummary(Insights.GetRegionalCropSummaryRequest request);

    GrpcResponse<Insights.GetMarketplaceActivitySummaryResponse> getMarketplaceActivitySummary(Insights.GetMarketplaceActivitySummaryRequest request);

    GrpcResponse<Insights.GetFoodSecurityIndicatorsResponse> getFoodSecurityIndicators(Insights.GetFoodSecurityIndicatorsRequest request);
}
