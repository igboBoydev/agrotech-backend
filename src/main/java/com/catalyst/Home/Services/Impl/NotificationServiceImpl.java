package com.catalyst.Home.Services.Impl;

import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Repositories.NotificationPreferenceRepository;
import com.catalyst.Home.Repositories.NotificationRepository;
import com.catalyst.Home.Services.NotificationService;
import com.catalyst.Home.Utils.NotificationChannel;
import com.catalyst.Home.Utils.NotificationStatus;
import com.catalyst.Home.Utils.RecipientType;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification.NotificationOuterClass;
import notification.Preference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    @Override
    @Transactional
    public GrpcResponse<NotificationOuterClass.SendNotificationResponse> sendNotification(NotificationOuterClass.SendNotificationRequest request) {
        NotificationChannel channel = mapChannel(request.getChannel());
        Optional<com.catalyst.Home.Entity.NotificationPreference> preferenceOpt =
                notificationPreferenceRepository.findByRecipientId(request.getRecipientId());

        boolean channelEnabled = isChannelEnabled(preferenceOpt.orElse(null), channel);

        NotificationStatus status;
        String resultMessage;
        if (channelEnabled) {
            status = NotificationStatus.NOTIFICATION_SENT;
            resultMessage = "Notification sent successfully";
            log.info("Simulated {} notification sent to {}: {}", channel, request.getRecipientId(), request.getTitle());
        } else {
            status = NotificationStatus.NOTIFICATION_FAILED;
            resultMessage = "Recipient has disabled this channel";
        }

        com.catalyst.Home.Entity.Notification notification = notificationRepository.save(
                com.catalyst.Home.Entity.Notification.builder()
                        .uuid(UUID.randomUUID().toString())
                        .recipientId(request.getRecipientId())
                        .recipientType(mapRecipientType(request.getRecipientType()))
                        .channel(channel)
                        .title(request.getTitle())
                        .body(request.getBody())
                        .status(status)
                        .createdAt(LocalDate.now().toString())
                        .build());

        NotificationOuterClass.SendNotificationResponse response = NotificationOuterClass.SendNotificationResponse.newBuilder()
                .setCode("200")
                .setMessage(resultMessage)
                .setNotification(toNotificationProto(notification))
                .build();

        return GrpcResponse.<NotificationOuterClass.SendNotificationResponse>builder()
                .code("200").message(resultMessage).status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<NotificationOuterClass.GetNotificationResponse> getNotification(NotificationOuterClass.GetNotificationRequest request) {
        Optional<com.catalyst.Home.Entity.Notification> notificationOpt = notificationRepository.findByUuid(request.getNotificationId());
        if (notificationOpt.isEmpty()) {
            return GrpcResponse.<NotificationOuterClass.GetNotificationResponse>builder()
                    .code("404").message("Notification not found").status(Status.Code.NOT_FOUND).build();
        }

        NotificationOuterClass.GetNotificationResponse response = NotificationOuterClass.GetNotificationResponse.newBuilder()
                .setCode("200")
                .setMessage("Notification retrieved successfully")
                .setNotification(toNotificationProto(notificationOpt.get()))
                .build();

        return GrpcResponse.<NotificationOuterClass.GetNotificationResponse>builder()
                .code("200").message("Notification retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<NotificationOuterClass.GetNotificationsByRecipientResponse> getNotificationsByRecipient(NotificationOuterClass.GetNotificationsByRecipientRequest request) {
        List<com.catalyst.Home.Entity.Notification> notifications =
                notificationRepository.findByRecipientIdOrderByIdDesc(request.getRecipientId());

        NotificationOuterClass.GetNotificationsByRecipientResponse response = NotificationOuterClass.GetNotificationsByRecipientResponse.newBuilder()
                .addAllNotifications(notifications.stream().map(this::toNotificationProto).toList())
                .build();

        return GrpcResponse.<NotificationOuterClass.GetNotificationsByRecipientResponse>builder()
                .code("200").message("Notifications retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<NotificationOuterClass.MarkAsReadResponse> markAsRead(NotificationOuterClass.MarkAsReadRequest request) {
        Optional<com.catalyst.Home.Entity.Notification> notificationOpt = notificationRepository.findByUuid(request.getNotificationId());
        if (notificationOpt.isEmpty()) {
            return GrpcResponse.<NotificationOuterClass.MarkAsReadResponse>builder()
                    .code("404").message("Notification not found").status(Status.Code.NOT_FOUND).build();
        }
        com.catalyst.Home.Entity.Notification notification = notificationOpt.get();
        if (notification.getStatus() != NotificationStatus.NOTIFICATION_SENT) {
            return GrpcResponse.<NotificationOuterClass.MarkAsReadResponse>builder()
                    .code("409").message("Notification cannot be marked read from its current state").status(Status.Code.FAILED_PRECONDITION).build();
        }

        notification.setStatus(NotificationStatus.NOTIFICATION_READ);
        notification.setReadAt(LocalDate.now().toString());
        notificationRepository.save(notification);

        NotificationOuterClass.MarkAsReadResponse response = NotificationOuterClass.MarkAsReadResponse.newBuilder()
                .setCode("200")
                .setMessage("Notification marked as read")
                .setNotification(toNotificationProto(notification))
                .build();

        return GrpcResponse.<NotificationOuterClass.MarkAsReadResponse>builder()
                .code("200").message("Notification marked as read").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<NotificationOuterClass.GetUnreadCountResponse> getUnreadCount(NotificationOuterClass.GetUnreadCountRequest request) {
        long count = notificationRepository.countByRecipientIdAndStatus(request.getRecipientId(), NotificationStatus.NOTIFICATION_SENT);

        NotificationOuterClass.GetUnreadCountResponse response = NotificationOuterClass.GetUnreadCountResponse.newBuilder()
                .setUnreadCount((int) count)
                .build();

        return GrpcResponse.<NotificationOuterClass.GetUnreadCountResponse>builder()
                .code("200").message("Unread count retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Preference.SetNotificationPreferencesResponse> setNotificationPreferences(Preference.SetNotificationPreferencesRequest request) {
        com.catalyst.Home.Entity.NotificationPreference preference = notificationPreferenceRepository
                .findByRecipientId(request.getRecipientId())
                .orElseGet(() -> com.catalyst.Home.Entity.NotificationPreference.builder()
                        .uuid(UUID.randomUUID().toString())
                        .recipientId(request.getRecipientId())
                        .build());

        preference.setSmsEnabled(request.getSmsEnabled());
        preference.setPushEnabled(request.getPushEnabled());
        preference.setWhatsappEnabled(request.getWhatsappEnabled());
        notificationPreferenceRepository.save(preference);

        Preference.SetNotificationPreferencesResponse response = Preference.SetNotificationPreferencesResponse.newBuilder()
                .setCode("200")
                .setMessage("Notification preferences updated successfully")
                .setPreference(toPreferenceProto(preference))
                .build();

        return GrpcResponse.<Preference.SetNotificationPreferencesResponse>builder()
                .code("200").message("Notification preferences updated successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Preference.GetNotificationPreferencesResponse> getNotificationPreferences(Preference.GetNotificationPreferencesRequest request) {
        Preference.NotificationPreference preferenceProto = notificationPreferenceRepository
                .findByRecipientId(request.getRecipientId())
                .map(this::toPreferenceProto)
                .orElseGet(() -> Preference.NotificationPreference.newBuilder()
                        .setRecipientId(request.getRecipientId())
                        .setSmsEnabled(true)
                        .setPushEnabled(true)
                        .setWhatsappEnabled(true)
                        .build());

        Preference.GetNotificationPreferencesResponse response = Preference.GetNotificationPreferencesResponse.newBuilder()
                .setCode("200")
                .setMessage("Notification preferences retrieved successfully")
                .setPreference(preferenceProto)
                .build();

        return GrpcResponse.<Preference.GetNotificationPreferencesResponse>builder()
                .code("200").message("Notification preferences retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    private static boolean isChannelEnabled(com.catalyst.Home.Entity.NotificationPreference preference, NotificationChannel channel) {
        if (preference == null) {
            return true;
        }
        Boolean value = switch (channel) {
            case CHANNEL_SMS -> preference.getSmsEnabled();
            case CHANNEL_PUSH -> preference.getPushEnabled();
            case CHANNEL_WHATSAPP -> preference.getWhatsappEnabled();
        };
        return value == null || value;
    }

    private NotificationOuterClass.Notification toNotificationProto(com.catalyst.Home.Entity.Notification notification) {
        return NotificationOuterClass.Notification.newBuilder()
                .setId(nullToEmpty(notification.getUuid()))
                .setRecipientId(nullToEmpty(notification.getRecipientId()))
                .setRecipientType(mapRecipientType(notification.getRecipientType()))
                .setChannel(mapChannel(notification.getChannel()))
                .setTitle(nullToEmpty(notification.getTitle()))
                .setBody(nullToEmpty(notification.getBody()))
                .setStatus(mapStatus(notification.getStatus()))
                .setCreatedAt(nullToEmpty(notification.getCreatedAt()))
                .setReadAt(nullToEmpty(notification.getReadAt()))
                .build();
    }

    private Preference.NotificationPreference toPreferenceProto(com.catalyst.Home.Entity.NotificationPreference preference) {
        return Preference.NotificationPreference.newBuilder()
                .setRecipientId(nullToEmpty(preference.getRecipientId()))
                .setSmsEnabled(preference.getSmsEnabled() == null || preference.getSmsEnabled())
                .setPushEnabled(preference.getPushEnabled() == null || preference.getPushEnabled())
                .setWhatsappEnabled(preference.getWhatsappEnabled() == null || preference.getWhatsappEnabled())
                .build();
    }

    private static notification.Enum.RecipientType mapRecipientType(RecipientType type) {
        return notification.Enum.RecipientType.valueOf(type.name());
    }

    private static RecipientType mapRecipientType(notification.Enum.RecipientType type) {
        return RecipientType.valueOf(type.name());
    }

    private static notification.Enum.NotificationChannel mapChannel(NotificationChannel channel) {
        return notification.Enum.NotificationChannel.valueOf(channel.name());
    }

    private static NotificationChannel mapChannel(notification.Enum.NotificationChannel channel) {
        return NotificationChannel.valueOf(channel.name());
    }

    private static notification.Enum.NotificationStatus mapStatus(NotificationStatus status) {
        return notification.Enum.NotificationStatus.valueOf(status.name());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
