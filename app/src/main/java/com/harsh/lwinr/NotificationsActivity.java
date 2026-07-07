package com.harsh.lwinr;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationsActivity extends AppCompatActivity {

    private static final String DB_URL = "https://lwinr-6f076-default-rtdb.asia-southeast1.firebasedatabase.app";

    private FirebaseAuth mAuth;
    private LinearLayout layoutNotificationsContainer;
    private ProgressBar progressBar;
    private TextView tvSummary, tvUnreadBadge;

    private String userId;
    private DatabaseReference notificationsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Notifications");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        userId = currentUser != null ? currentUser.getUid() : "";

        if (userId == null || userId.trim().isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        layoutNotificationsContainer = findViewById(R.id.layoutNotificationsContainer);
        progressBar = findViewById(R.id.progressBar);
        tvSummary = findViewById(R.id.tvSummary);
        tvUnreadBadge = findViewById(R.id.tvUnreadBadge);

        notificationsRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(userId)
                .child("notifications");

        loadNotifications();
    }

    private void loadNotifications() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (layoutNotificationsContainer != null) layoutNotificationsContainer.removeAllViews();
        if (tvSummary != null) tvSummary.setText("Loading...");
        if (tvUnreadBadge != null) tvUnreadBadge.setText("0");

        notificationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                if (!snapshot.exists()) {
                    if (tvSummary != null) tvSummary.setText("0 notifications");
                    if (tvUnreadBadge != null) tvUnreadBadge.setText("0");
                    addEmptyMessage("No notifications yet.");
                    return;
                }

                List<DataSnapshot> notificationList = new ArrayList<>();
                int unreadCount = 0;

                for (DataSnapshot notifSnap : snapshot.getChildren()) {
                    notificationList.add(notifSnap);

                    Boolean isRead = notifSnap.child("isRead").getValue(Boolean.class);
                    if (isRead == null || !isRead) {
                        unreadCount++;
                    }
                }

                Collections.sort(notificationList, (a, b) -> {
                    Long ta = a.child("createdAt").getValue(Long.class);
                    Long tb = b.child("createdAt").getValue(Long.class);
                    long va = ta != null ? ta : 0L;
                    long vb = tb != null ? tb : 0L;
                    return Long.compare(vb, va);
                });

                if (tvSummary != null) {
                    tvSummary.setText("Total: " + notificationList.size() + " | Unread: " + unreadCount);
                }
                if (tvUnreadBadge != null) {
                    tvUnreadBadge.setText(String.valueOf(unreadCount));
                }

                for (DataSnapshot notifSnap : notificationList) {
                    addNotificationCard(notifSnap);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (tvSummary != null) tvSummary.setText("Load failed");

                Toast.makeText(
                        NotificationsActivity.this,
                        "Error: " + error.getMessage(),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void addEmptyMessage(String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(16f);
        tv.setPadding(24, 24, 24, 24);
        layoutNotificationsContainer.addView(tv);
    }

    private void addNotificationCard(DataSnapshot notifSnap) {
        String notifId = notifSnap.getKey() != null ? notifSnap.getKey() : "";
        String title = getStringValue(notifSnap.child("title"));
        String message = getStringValue(notifSnap.child("message"));
        String type = getStringValue(notifSnap.child("type"));
        long createdAt = getLongValue(notifSnap.child("createdAt"));
        boolean isRead = getBooleanValue(notifSnap.child("isRead"));

        String typeLabel = getTypeLabel(type);
        String icon = getTypeIcon(type);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 28, 28, 28);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 24);
        card.setLayoutParams(cardParams);
        card.setBackgroundResource(R.drawable.bg_login_card_premium);
        card.setAlpha(isRead ? 0.85f : 1f);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(icon + " " + (title.isEmpty() ? "Notification" : title) + (isRead ? "" : " • New"));
        tvTitle.setTextSize(17f);
        tvTitle.setTypeface(null, isRead ? Typeface.NORMAL : Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, 10);
        card.addView(tvTitle);

        TextView tvMessage = new TextView(this);
        tvMessage.setText(message.isEmpty() ? "-" : message);
        tvMessage.setTextSize(14f);
        tvMessage.setPadding(0, 0, 0, 10);
        card.addView(tvMessage);

        TextView tvMeta = new TextView(this);
        tvMeta.setText(
                "Category: " + typeLabel + "\n" +
                        "Time: " + formatTime(createdAt) + "\n" +
                        "Status: " + (isRead ? "Read" : "Unread")
        );
        tvMeta.setTextSize(12f);
        card.addView(tvMeta);

        card.setOnClickListener(v -> markAsRead(notifId, isRead));

        layoutNotificationsContainer.addView(card);
    }

    private void markAsRead(String notifId, boolean alreadyRead) {
        if (notifId == null || notifId.trim().isEmpty() || alreadyRead) {
            return;
        }

        notificationsRef.child(notifId).child("isRead").setValue(true)
                .addOnSuccessListener(unused -> loadNotifications())
                .addOnFailureListener(e ->
                        Toast.makeText(
                                NotificationsActivity.this,
                                "Failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    private String getTypeLabel(String type) {
        if ("withdraw_paid".equalsIgnoreCase(type)) return "Withdraw Approved";
        if ("withdraw_rejected".equalsIgnoreCase(type)) return "Withdraw Rejected";
        if ("lucky_draw_win".equalsIgnoreCase(type)) return "Lucky Draw Winner";
        if ("announcement".equalsIgnoreCase(type)) return "Announcement";
        if ("accountwarning".equalsIgnoreCase(type) || "account_warning".equalsIgnoreCase(type)) return "Account Warning";
        return "General";
    }

    private String getTypeIcon(String type) {
        if ("withdraw_paid".equalsIgnoreCase(type)) return "✅";
        if ("withdraw_rejected".equalsIgnoreCase(type)) return "❌";
        if ("lucky_draw_win".equalsIgnoreCase(type)) return "🎉";
        if ("announcement".equalsIgnoreCase(type)) return "📢";
        if ("accountwarning".equalsIgnoreCase(type) || "account_warning".equalsIgnoreCase(type)) return "⚠️";
        return "🔔";
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "N/A";
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private String getStringValue(DataSnapshot snapshot) {
        String value = snapshot.getValue(String.class);
        return value != null ? value : "";
    }

    private long getLongValue(DataSnapshot snapshot) {
        Long value = snapshot.getValue(Long.class);
        return value != null ? value : 0L;
    }

    private boolean getBooleanValue(DataSnapshot snapshot) {
        Boolean value = snapshot.getValue(Boolean.class);
        return value != null && value;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}