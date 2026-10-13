package com.catalyst.Home.GrpcControllers;

import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Services.NotificationService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import notification.NotificationOuterClass;
import notification.NotificationServiceGrpc;
import notification.Preference;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class NotificationController extends NotificationServiceGrpc.NotificationServiceImplBase {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void sendNotification(NotificationOuterClass.SendNotificationRequest request, StreamObserver<NotificationOuterClass.SendNotificationResponse> responseObserver) {
        respond(notificationService.sendNotification(request), responseObserver);
    }

    @Override
    public void getNotification(NotificationOuterClass.GetNotificationRequest request, StreamObserver<NotificationOuterClass.GetNotificationResponse> responseObserver) {
        respond(notificationService.getNotification(request), responseObserver);
    }

    @Override
    public void getNotificationsByRecipient(NotificationOuterClass.GetNotificationsByRecipientRequest request, StreamObserver<NotificationOuterClass.GetNotificationsByRecipientResponse> responseObserver) {
        respond(notificationService.getNotificationsByRecipient(request), responseObserver);
    }

    @Override
    public void markAsRead(NotificationOuterClass.MarkAsReadRequest request, StreamObserver<NotificationOuterClass.MarkAsReadResponse> responseObserver) {
        respond(notificationService.markAsRead(request), responseObserver);
    }

    @Override
    public void getUnreadCount(NotificationOuterClass.GetUnreadCountRequest request, StreamObserver<NotificationOuterClass.GetUnreadCountResponse> responseObserver) {
        respond(notificationService.getUnreadCount(request), responseObserver);
    }

    @Override
    public void setNotificationPreferences(Preference.SetNotificationPreferencesRequest request, StreamObserver<Preference.SetNotificationPreferencesResponse> responseObserver) {
        respond(notificationService.setNotificationPreferences(request), responseObserver);
    }

    @Override
    public void getNotificationPreferences(Preference.GetNotificationPreferencesRequest request, StreamObserver<Preference.GetNotificationPreferencesResponse> responseObserver) {
        respond(notificationService.getNotificationPreferences(request), responseObserver);
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
