package com.catalyst.Home.Entity;

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
@Table(name = "notification_preferences")
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @Column(name = "RECIPIENT_ID", unique = true)
    private String recipientId;

    @Column(name = "SMS_ENABLED")
    private Boolean smsEnabled;

    @Column(name = "PUSH_ENABLED")
    private Boolean pushEnabled;

    @Column(name = "WHATSAPP_ENABLED")
    private Boolean whatsappEnabled;
}
