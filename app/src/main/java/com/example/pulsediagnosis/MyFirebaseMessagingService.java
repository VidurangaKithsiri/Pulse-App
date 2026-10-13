package com.example.pulsediagnosis;

import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.auth.FirebaseAuth;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(@NonNull String token) { GuardianNotifications.register(token); }
    @Override public void onMessageReceived(@NonNull RemoteMessage message) {
        String recipient = message.getData().get("guardianId");
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || (recipient != null && !uid.equals(recipient))) return;
        String title = "PulseDiagnosis guardian alert", body = "Open your guardian dashboard.";
        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
        }
        String id = message.getData().get("alertId");
        if (id == null) id = String.valueOf(message.getMessageId());
        GuardianNotifications.show(this, id, title, body);
    }
}
