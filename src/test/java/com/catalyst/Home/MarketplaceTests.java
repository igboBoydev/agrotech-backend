package com.catalyst.Home;

import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Repositories.FarmProductsRepository;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.FarmsRepository;
import com.catalyst.Home.Repositories.MarketplaceListingRepository;
import com.catalyst.Home.Repositories.MarketplaceOrderRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Services.MarketplaceService;
import com.catalyst.Home.Services.OnboardingService;
import marketplace.Listing;
import marketplace.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import users.Farmers;
import users.ProductsOuterClass;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MarketplaceTests {

	private static final String TEST_EMAIL = "chidi.okonkwo.marketplace@example.com";
	private static final String TEST_BUYER_ID = "BUYER-TEST-001";

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private MarketplaceService marketplaceService;

	@Autowired
	private FarmerRepository farmerRepository;

	@Autowired
	private UsersRepository usersRepository;

	@Autowired
	private FarmsRepository farmsRepository;

	@Autowired
	private FarmProductsRepository farmProductsRepository;

	@Autowired
	private MarketplaceListingRepository marketplaceListingRepository;

	@Autowired
	private MarketplaceOrderRepository marketplaceOrderRepository;

	@AfterEach
	void cleanUp() {
		farmerRepository.findByEmail(TEST_EMAIL).ifPresent(farmer -> {
			marketplaceOrderRepository.findByListing_SellerFarmer_Uuid(farmer.getUuid()).forEach(marketplaceOrderRepository::delete);
			marketplaceListingRepository.findBySellerFarmer_Uuid(farmer.getUuid()).forEach(marketplaceListingRepository::delete);
			farmsRepository.findByFarmers_Uuid(farmer.getUuid()).forEach(farm -> {
				farmProductsRepository.findByFarm_Uuid(farm.getUuid()).forEach(farmProductsRepository::delete);
				farmsRepository.delete(farm);
			});
			farmerRepository.delete(farmer);
		});
		usersRepository.findByEmail(TEST_EMAIL).ifPresent(usersRepository::delete);
	}

	private String createTestFarmer() {
		onboardingService.createFarmer(Farmers.CreateFarmerRequest.newBuilder()
				.setFirstName("Chidi")
				.setLastName("Okonkwo")
				.setDob("1990-02-20")
				.setHomeAddress("22 Market Street")
				.setLga("Nnewi")
				.setState("Anambra")
				.setCountry("Nigeria")
				.setEmail(TEST_EMAIL)
				.setPhone("+2348077778888")
				.setPassword("s3cret-pw")
				.build());
		return farmerRepository.findByEmail(TEST_EMAIL).orElseThrow().getUuid();
	}

	private String createTestListing(String farmerUuid, double quantityAvailable) {
		onboardingService.addFarm(Farmers.AddFarmRequest.newBuilder()
				.setUserId(farmerUuid)
				.setAddress("1 Farm Close")
				.setLga("Nnewi")
				.setState("Anambra")
				.setCountry("Nigeria")
				.setLandSize("3 acres")
				.setIsLease(false)
				.setIsOwnProperty(true)
				.setPlantType("Maize")
				.setFarmType("Crop")
				.build());
		String farmUuid = farmsRepository.findByFarmers_Uuid(farmerUuid).stream().findFirst().orElseThrow().getUuid();

		onboardingService.addFarmProduct(ProductsOuterClass.AddFarmProductRequest.newBuilder()
				.setFarmId(farmUuid)
				.setProductName("Maize")
				.setCategory("Grain")
				.setProductType("Cereal")
				.setQuantity(quantityAvailable)
				.setUnit("bags")
				.setPricePerUnit(50.0)
				.setIsAvailable(true)
				.build());
		String productId = String.valueOf(farmProductsRepository.findByFarm_Uuid(farmUuid).get(0).getId());

		GrpcResponse<Listing.CreateListingResponse> createResponse = marketplaceService.createListing(
				Listing.CreateListingRequest.newBuilder()
						.setFarmerId(farmerUuid)
						.setProductId(productId)
						.setPricePerUnit(60.0)
						.setQuantityAvailable(quantityAvailable)
						.build());
		assertThat(createResponse.getCode()).isEqualTo("200");
		return createResponse.getData().getListing().getId();
	}

	@Test
	void orderLifecycle_placeAcceptAndComplete() {
		String farmerUuid = createTestFarmer();
		String listingUuid = createTestListing(farmerUuid, 100.0);

		GrpcResponse<Order.PlaceOrderResponse> placeResponse = marketplaceService.placeOrder(
				Order.PlaceOrderRequest.newBuilder()
						.setListingId(listingUuid)
						.setBuyerId(TEST_BUYER_ID)
						.setBuyerType(marketplace.Enum.BuyerType.BUYER_BUSINESS)
						.setQuantity(40.0)
						.build());
		assertThat(placeResponse.getCode()).isEqualTo("200");
		Order.MarketplaceOrder order = placeResponse.getData().getOrder();
		assertThat(order.getTotalAmount()).isEqualTo(2400.0);
		// Platform commission is a fixed 3.5% of the order total.
		assertThat(order.getCommissionAmount()).isEqualTo(84.0);
		String orderUuid = order.getId();

		GrpcResponse<Listing.GetListingResponse> afterOrder = marketplaceService.getListing(
				Listing.GetListingRequest.newBuilder().setListingId(listingUuid).build());
		assertThat(afterOrder.getData().getListing().getQuantityAvailable()).isEqualTo(60.0);

		GrpcResponse<Order.ConfirmOrderResponse> confirmResponse = marketplaceService.confirmOrder(
				Order.ConfirmOrderRequest.newBuilder().setOrderId(orderUuid).setAccept(true).build());
		assertThat(confirmResponse.getData().getOrder().getStatus()).isEqualTo(marketplace.Enum.OrderStatus.ORDER_ACCEPTED);

		GrpcResponse<Order.CompleteOrderResponse> completeResponse = marketplaceService.completeOrder(
				Order.CompleteOrderRequest.newBuilder().setOrderId(orderUuid).build());
		assertThat(completeResponse.getData().getOrder().getStatus()).isEqualTo(marketplace.Enum.OrderStatus.ORDER_COMPLETED);
		assertThat(completeResponse.getData().getOrder().getEscrowStatus()).isEqualTo(marketplace.Enum.EscrowStatus.ESCROW_RELEASED);

		GrpcResponse<Order.GetOrdersBySellerResponse> sellerOrders = marketplaceService.getOrdersBySeller(
				Order.GetOrdersBySellerRequest.newBuilder().setFarmerId(farmerUuid).build());
		assertThat(sellerOrders.getData().getOrdersList()).hasSize(1);
	}

	@Test
	void order_cancelledOrderRestoresListingQuantity() {
		String farmerUuid = createTestFarmer();
		String listingUuid = createTestListing(farmerUuid, 100.0);

		GrpcResponse<Order.PlaceOrderResponse> placeResponse = marketplaceService.placeOrder(
				Order.PlaceOrderRequest.newBuilder()
						.setListingId(listingUuid)
						.setBuyerId(TEST_BUYER_ID)
						.setBuyerType(marketplace.Enum.BuyerType.BUYER_FARMER)
						.setQuantity(25.0)
						.build());
		String orderUuid = placeResponse.getData().getOrder().getId();

		GrpcResponse<Order.CancelOrderResponse> cancelResponse = marketplaceService.cancelOrder(
				Order.CancelOrderRequest.newBuilder().setOrderId(orderUuid).build());
		assertThat(cancelResponse.getData().getOrder().getStatus()).isEqualTo(marketplace.Enum.OrderStatus.ORDER_CANCELLED);
		assertThat(cancelResponse.getData().getOrder().getEscrowStatus()).isEqualTo(marketplace.Enum.EscrowStatus.ESCROW_REFUNDED);

		GrpcResponse<Listing.GetListingResponse> afterCancel = marketplaceService.getListing(
				Listing.GetListingRequest.newBuilder().setListingId(listingUuid).build());
		assertThat(afterCancel.getData().getListing().getQuantityAvailable()).isEqualTo(100.0);
		assertThat(afterCancel.getData().getListing().getStatus()).isEqualTo(marketplace.Enum.ListingStatus.LISTING_ACTIVE);
	}

}
