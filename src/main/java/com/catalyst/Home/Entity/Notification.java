package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.NotificationChannel;
import com.catalyst.Home.Utils.NotificationStatus;
import com.catalyst.Home.Utils.RecipientType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @Column(name = "RECIPIENT_ID")
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "RECIPIENT_TYPE")
    private RecipientType recipientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "CHANNEL")
    private NotificationChannel channel;

    @Column(name = "TITLE")
    private String title;

    @Column(name = "BODY")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private NotificationStatus status;

    @Column(name = "CREATED_AT")
    private String createdAt;

    @Column(name = "READ_AT")
    private String readAt;
}
