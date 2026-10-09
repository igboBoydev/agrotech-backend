package com.catalyst.Home.Services;

import com.catalyst.Home.DTO.response.GrpcResponse;
import notification.NotificationOuterClass;
import notification.Preference;

public interface NotificationService {

    GrpcResponse<NotificationOuterClass.SendNotificationResponse> sendNotification(NotificationOuterClass.SendNotificationRequest request);

    GrpcResponse<NotificationOuterClass.GetNotificationResponse> getNotification(NotificationOuterClass.GetNotificationRequest request);

    GrpcResponse<NotificationOuterClass.GetNotificationsByRecipientResponse> getNotificationsByRecipient(NotificationOuterClass.GetNotificationsByRecipientRequest request);

    GrpcResponse<NotificationOuterClass.MarkAsReadResponse> markAsRead(NotificationOuterClass.MarkAsReadRequest request);

    GrpcResponse<NotificationOuterClass.GetUnreadCountResponse> getUnreadCount(NotificationOuterClass.GetUnreadCountRequest request);

    GrpcResponse<Preference.SetNotificationPreferencesResponse> setNotificationPreferences(Preference.SetNotificationPreferencesRequest request);

    GrpcResponse<Preference.GetNotificationPreferencesResponse> getNotificationPreferences(Preference.GetNotificationPreferencesRequest request);
}
