package com.harsh.lwinr;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SplashRouterActivity extends AppCompatActivity {

    private static final String TAG = "LwinR_AUTH";
    // Demo Firebase Realtime Database URL.
// Replace this with your own Firebase Database URL before running the project.
private static final String DB_URL =
        "https://YOUR-PROJECT-ID-default-rtdb.firebaseio.com";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_router);

        new Handler(Looper.getMainLooper()).postDelayed(this::routeNext, 700);
    }

    private void routeNext() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        Log.d(TAG, "Splash routeNext currentUser=" + (currentUser != null)
                + ", uid=" + (currentUser != null ? currentUser.getUid() : "null"));

        if (currentUser == null || currentUser.getUid() == null || currentUser.getUid().trim().isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String userId = currentUser.getUid();

        DatabaseReference userRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Splash user exists=" + snapshot.exists() + ", uid=" + userId);

                if (!snapshot.exists()) {
                    startActivity(new Intent(SplashRouterActivity.this, LoginActivity.class));
                    finish();
                    return;
                }

                Boolean profileSetupDone = snapshot.child("profileSetupDone").getValue(Boolean.class);
                String displayName = snapshot.child("displayName").getValue(String.class);

                boolean isProfileReady = Boolean.TRUE.equals(profileSetupDone)
                        && displayName != null
                        && !displayName.trim().isEmpty();

                Log.d(TAG, "Splash profileReady=" + isProfileReady
                        + ", profileSetupDone=" + profileSetupDone
                        + ", displayName=" + displayName);

                if (isProfileReady) {
                    startActivity(new Intent(SplashRouterActivity.this, MainActivity.class));
                } else {
                    startActivity(new Intent(SplashRouterActivity.this, UserProfileSetupActivity.class));
                }

                finish();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Splash DB cancelled: " + error.getMessage());
                startActivity(new Intent(SplashRouterActivity.this, LoginActivity.class));
                finish();
            }
        });
    }
}
