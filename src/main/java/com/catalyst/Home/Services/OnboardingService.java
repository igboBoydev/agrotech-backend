package com.catalyst.Home.Services;


import com.catalyst.Home.Dto.response.GrpcResponse;
import users.Business;
import users.Farmers;
import users.KycOuterClass;
import users.ProductsOuterClass;

public interface OnboardingService {

    GrpcResponse<Farmers.CreateFarmerResponse> createFarmer(Farmers.CreateFarmerRequest request);

    GrpcResponse<Farmers.AddFarmerKycResponse> addFarmerKyc(Farmers.AddFarmerKycRequest request);

    GrpcResponse<Farmers.AddFarmResponse> addFarm(Farmers.AddFarmRequest request);

    GrpcResponse<Farmers.FarmerProfileResponse> getFarmersProfile(Farmers.FarmerProfileRequest request);

    GrpcResponse<Farmers.GetFarmsResponse> getFarms(Farmers.GetFarmsRequest request);

    GrpcResponse<KycOuterClass.KYCStatus> checkKycStatus(KycOuterClass.KYCRequest request);

    GrpcResponse<Business.CreateBusinessResponse> createBusiness(Business.CreateBusinessRequest request);

    GrpcResponse<Business.GetBusinessProfileResponse> getBusinessProfile(Business.GetBusinessProfileRequest request);

    GrpcResponse<ProductsOuterClass.GetProductsResponse> getFarmProducts(ProductsOuterClass.GetProductsRequest request);

    GrpcResponse<ProductsOuterClass.AddFarmProductResponse> addFarmProduct(ProductsOuterClass.AddFarmProductRequest request);
}
