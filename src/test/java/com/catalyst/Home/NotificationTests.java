package com.catalyst.Home;

import com.catalyst.Home.DTO.response.GrpcResponse;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.NotificationPreferenceRepository;
import com.catalyst.Home.Repositories.NotificationRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Services.NotificationService;
import com.catalyst.Home.Services.OnboardingService;
import notification.NotificationOuterClass;
import notification.Preference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import users.Farmers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationTests {

	private static final String TEST_EMAIL = "funmi.bello.notification@example.com";

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private FarmerRepository farmerRepository;

	@Autowired
	private UsersRepository usersRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private NotificationPreferenceRepository notificationPreferenceRepository;

	@AfterEach
	void cleanUp() {
		farmerRepository.findByEmail(TEST_EMAIL).ifPresent(farmer -> {
			notificationRepository.findByRecipientIdOrderByIdDesc(farmer.getUuid()).forEach(notificationRepository::delete);
			notificationPreferenceRepository.findByRecipientId(farmer.getUuid()).ifPresent(notificationPreferenceRepository::delete);
			farmerRepository.delete(farmer);
		});
		usersRepository.findByEmail(TEST_EMAIL).ifPresent(usersRepository::delete);
	}

	private String createTestFarmer() {
		onboardingService.createFarmer(Farmers.CreateFarmerRequest.newBuilder()
				.setFirstName("Funmi")
				.setLastName("Bello")
				.setDob("1992-11-03")
				.setHomeAddress("9 Ogunlana Drive")
				.setLga("Surulere")
				.setState("Lagos")
				.setCountry("Nigeria")
				.setEmail(TEST_EMAIL)
				.setPhone("+2348011112222")
				.setPassword("s3cret-pw")
				.build());
		return farmerRepository.findByEmail(TEST_EMAIL).orElseThrow().getUuid();
	}

	@Test
	void notificationLifecycle_sendReadAndUnreadCount() {
		String farmerUuid = createTestFarmer();

		GrpcResponse<Preference.GetNotificationPreferencesResponse> defaults = notificationService.getNotificationPreferences(
				Preference.GetNotificationPreferencesRequest.newBuilder().setRecipientId(farmerUuid).build());
		assertThat(defaults.getData().getPreference().getSmsEnabled()).isTrue();
		assertThat(defaults.getData().getPreference().getPushEnabled()).isTrue();
		assertThat(defaults.getData().getPreference().getWhatsappEnabled()).isTrue();

		GrpcResponse<NotificationOuterClass.SendNotificationResponse> sendResponse = notificationService.sendNotification(
				NotificationOuterClass.SendNotificationRequest.newBuilder()
						.setRecipientId(farmerUuid)
						.setRecipientType(notification.Enum.RecipientType.RECIPIENT_FARMER)
						.setChannel(notification.Enum.NotificationChannel.CHANNEL_PUSH)
						.setTitle("Loan approved")
						.setBody("Your loan application has been approved.")
						.build());
		assertThat(sendResponse.getCode()).isEqualTo("200");
		assertThat(sendResponse.getData().getNotification().getStatus()).isEqualTo(notification.Enum.NotificationStatus.NOTIFICATION_SENT);
		String notificationUuid = sendResponse.getData().getNotification().getId();

		GrpcResponse<NotificationOuterClass.GetUnreadCountResponse> unreadAfterSend = notificationService.getUnreadCount(
				NotificationOuterClass.GetUnreadCountRequest.newBuilder().setRecipientId(farmerUuid).build());
		assertThat(unreadAfterSend.getData().getUnreadCount()).isEqualTo(1);

		GrpcResponse<NotificationOuterClass.MarkAsReadResponse> markReadResponse = notificationService.markAsRead(
				NotificationOuterClass.MarkAsReadRequest.newBuilder().setNotificationId(notificationUuid).build());
		assertThat(markReadResponse.getData().getNotification().getStatus()).isEqualTo(notification.Enum.NotificationStatus.NOTIFICATION_READ);

		GrpcResponse<NotificationOuterClass.GetUnreadCountResponse> unreadAfterRead = notificationService.getUnreadCount(
				NotificationOuterClass.GetUnreadCountRequest.newBuilder().setRecipientId(farmerUuid).build());
		assertThat(unreadAfterRead.getData().getUnreadCount()).isEqualTo(0);

		GrpcResponse<NotificationOuterClass.GetNotificationsByRecipientResponse> history = notificationService.getNotificationsByRecipient(
				NotificationOuterClass.GetNotificationsByRecipientRequest.newBuilder().setRecipientId(farmerUuid).build());
		assertThat(history.getData().getNotificationsList()).hasSize(1);
	}

	@Test
	void disabledChannel_notificationFailsInsteadOfFakingDelivery() {
		String farmerUuid = createTestFarmer();

		GrpcResponse<Preference.SetNotificationPreferencesResponse> setPrefsResponse = notificationService.setNotificationPreferences(
				Preference.SetNotificationPreferencesRequest.newBuilder()
						.setRecipientId(farmerUuid)
						.setSmsEnabled(false)
						.setPushEnabled(true)
						.setWhatsappEnabled(true)
						.build());
		assertThat(setPrefsResponse.getData().getPreference().getSmsEnabled()).isFalse();

		GrpcResponse<NotificationOuterClass.SendNotificationResponse> sendResponse = notificationService.sendNotification(
				NotificationOuterClass.SendNotificationRequest.newBuilder()
						.setRecipientId(farmerUuid)
						.setRecipientType(notification.Enum.RecipientType.RECIPIENT_FARMER)
						.setChannel(notification.Enum.NotificationChannel.CHANNEL_SMS)
						.setTitle("Price alert")
						.setBody("Maize prices rose 8% this week.")
						.build());
		assertThat(sendResponse.getCode()).isEqualTo("200");
		assertThat(sendResponse.getData().getNotification().getStatus()).isEqualTo(notification.Enum.NotificationStatus.NOTIFICATION_FAILED);
	}

}
