package com.harsh.lwinr;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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

import java.util.HashMap;
import java.util.Map;

public class UserProfileSetupActivity extends AppCompatActivity {

    private static final String TAG = "LwinR_DEBUG";
    // Demo Firebase Realtime Database URL.
// Replace this with your own Firebase Database URL before running the project.
private static final String DB_URL =
        "https://YOUR-PROJECT-ID-default-rtdb.firebaseio.com";
    private static final long REFERRAL_REWARD = 100L;

    private EditText etUserName, etReferralCode;
    private TextView tvUserNameStatus, tvReferralStatus;
    private Button btnSaveProfile;

    private FirebaseAuth mAuth;
    private String userId;
    private DatabaseReference rootRef;

    private boolean isUserNameValid = false;
    private boolean isReferralValid = true;
    private String validatedReferralOwnerId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile_setup);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null || currentUser.getUid() == null || currentUser.getUid().trim().isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        userId = currentUser.getUid();
        rootRef = FirebaseDatabase.getInstance(DB_URL).getReference();

        etUserName = findViewById(R.id.etUserName);
        etReferralCode = findViewById(R.id.etReferralCode);
        tvUserNameStatus = findViewById(R.id.tvUserNameStatus);
        tvReferralStatus = findViewById(R.id.tvReferralStatus);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);

        btnSaveProfile.setEnabled(false);

        setupInputValidation();
        btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    private void setupInputValidation() {
        etUserName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                validateUserName(s.toString().trim());
            }
        });

        etReferralCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                validateReferralCode(s.toString().trim());
            }
        });
    }

    private void validateUserName(String name) {
        isUserNameValid = false;
        updateSaveButton();

        if (name.length() < 3) {
            tvUserNameStatus.setText("Min 3 characters");
            tvUserNameStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            return;
        }

        if (!name.matches("^[a-zA-Z0-9]+$")) {
            tvUserNameStatus.setText("Letters & numbers only");
            tvUserNameStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            return;
        }

        rootRef.child("users")
                .orderByChild("displayName")
                .equalTo(name)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean takenByAnotherUser = false;

                        for (DataSnapshot userSnap : snapshot.getChildren()) {
                            String foundUserId = userSnap.getKey();
                            if (foundUserId != null && !foundUserId.equals(userId)) {
                                takenByAnotherUser = true;
                                break;
                            }
                        }

                        if (takenByAnotherUser) {
                            tvUserNameStatus.setText("Name already taken ❌");
                            tvUserNameStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                            isUserNameValid = false;
                        } else {
                            tvUserNameStatus.setText("Name available ✅");
                            tvUserNameStatus.setTextColor(getColor(android.R.color.holo_green_dark));
                            isUserNameValid = true;
                        }

                        updateSaveButton();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Name check error: " + error.getMessage());
                        tvUserNameStatus.setText("Check failed");
                        tvUserNameStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                        isUserNameValid = false;
                        updateSaveButton();
                    }
                });
    }

    private void validateReferralCode(String code) {
        validatedReferralOwnerId = "";

        if (code.isEmpty()) {
            tvReferralStatus.setText("Optional");
            tvReferralStatus.setTextColor(getColor(android.R.color.darker_gray));
            isReferralValid = true;
            updateSaveButton();
            return;
        }

        if (!code.matches("^[A-Za-z0-9]{6,20}$")) {
            tvReferralStatus.setText("Invalid referral format");
            tvReferralStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            isReferralValid = false;
            updateSaveButton();
            return;
        }

        rootRef.child("users")
                .orderByChild("referralCode")
                .equalTo(code)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            tvReferralStatus.setText("Invalid code");
                            tvReferralStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                            isReferralValid = false;
                            updateSaveButton();
                            return;
                        }

                        String ownerId = "";
                        for (DataSnapshot userSnap : snapshot.getChildren()) {
                            ownerId = userSnap.getKey();
                            break;
                        }

                        if (ownerId == null || ownerId.isEmpty()) {
                            tvReferralStatus.setText("Invalid code");
                            tvReferralStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                            isReferralValid = false;
                        } else if (ownerId.equals(userId)) {
                            tvReferralStatus.setText("You can't use your own code");
                            tvReferralStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                            isReferralValid = false;
                        } else {
                            validatedReferralOwnerId = ownerId;
                            tvReferralStatus.setText("Valid referrer ✅");
                            tvReferralStatus.setTextColor(getColor(android.R.color.holo_green_dark));
                            isReferralValid = true;
                        }

                        updateSaveButton();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Referral check error: " + error.getMessage());
                        tvReferralStatus.setText("Check failed");
                        tvReferralStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                        isReferralValid = false;
                        updateSaveButton();
                    }
                });
    }

    private void updateSaveButton() {
        if (btnSaveProfile != null) {
            btnSaveProfile.setEnabled(isUserNameValid && isReferralValid);
        }
    }

    private void saveProfile() {
        String displayName = etUserName.getText().toString().trim();
        String referralCode = etReferralCode.getText().toString().trim();

        if (!isUserNameValid) {
            Toast.makeText(this, "Enter valid username", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isReferralValid) {
            Toast.makeText(this, "Referral code is invalid", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveProfile.setEnabled(false);
        DatabaseReference userRef = rootRef.child("users").child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot currentUserSnap) {
                String existingReferredByUserId = getString(currentUserSnap.child("referredByUserId"));
                boolean profileAlreadyDone = getBoolean(currentUserSnap.child("profileSetupDone"));

                Map<String, Object> updates = new HashMap<>();
                updates.put("username", displayName);
                updates.put("displayName", displayName);
                updates.put("name", displayName);
                updates.put("profileSetupDone", true);

                if (!currentUserSnap.child("bonusWallet").exists()) {
                    updates.put("bonusWallet", 0L);
                }

                if (!currentUserSnap.child("referralsCount").exists()) {
                    updates.put("referralsCount", 0L);
                }

                if (!currentUserSnap.child("referralBonusTotal").exists()) {
                    updates.put("referralBonusTotal", 0L);
                }

                if (!currentUserSnap.child("referralWithdrawableReleased").exists()) {
                    updates.put("referralWithdrawableReleased", 0L);
                }

                if (!currentUserSnap.child("referralRewardGiven").exists()) {
                    updates.put("referralRewardGiven", false);
                }

                if (!currentUserSnap.child("wallet/bonus").exists()) {
                    updates.put("wallet/bonus", 0L);
                }

                if (!currentUserSnap.child("wallet/referralBonus").exists()) {
                    updates.put("wallet/referralBonus", 0L);
                }

                if (!currentUserSnap.child("wallet/total").exists()) {
                    updates.put("wallet/total", getLong(currentUserSnap.child("mainWallet")));
                }

                if (!currentUserSnap.child("wallet/withdrawable").exists()) {
                    updates.put("wallet/withdrawable", getLong(currentUserSnap.child("mainWallet")));
                }

                if (!currentUserSnap.child("wallet/updatedAt").exists()) {
                    updates.put("wallet/updatedAt", System.currentTimeMillis());
                }

                boolean canApplyReferral =
                        !referralCode.isEmpty()
                                && !validatedReferralOwnerId.isEmpty()
                                && existingReferredByUserId.isEmpty()
                                && !profileAlreadyDone;

                if (canApplyReferral) {
                    updates.put("referredBy", referralCode);
                    updates.put("referredByUserId", validatedReferralOwnerId);
                    updates.put("referralRewardGiven", false);
                }

                userRef.updateChildren(updates).addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        btnSaveProfile.setEnabled(true);
                        Toast.makeText(UserProfileSetupActivity.this, "Failed to save profile", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (canApplyReferral) {
                        createReferralRequest(displayName, referralCode, validatedReferralOwnerId);
                    } else {
                        btnSaveProfile.setEnabled(true);
                        Toast.makeText(
                                UserProfileSetupActivity.this,
                                "Profile saved! Welcome " + displayName + " 👋",
                                Toast.LENGTH_LONG
                        ).show();
                        MainActivity.goToMainScreen(UserProfileSetupActivity.this);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                btnSaveProfile.setEnabled(true);
                Log.e(TAG, "Profile load error: " + error.getMessage());
                Toast.makeText(UserProfileSetupActivity.this, "Failed to save profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createReferralRequest(String displayName, String referralCode, String referrerUserId) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("userId", userId);
        requestMap.put("userName", displayName);
        requestMap.put("referredByCode", referralCode);
        requestMap.put("referredByUserId", referrerUserId);
        requestMap.put("status", "pending");
        requestMap.put("rewardPoints", REFERRAL_REWARD);
        requestMap.put("createdAt", System.currentTimeMillis());

        rootRef.child("referralRequests").child(userId)
                .setValue(requestMap)
                .addOnCompleteListener(task -> {
                    btnSaveProfile.setEnabled(true);

                    if (task.isSuccessful()) {
                        Toast.makeText(
                                UserProfileSetupActivity.this,
                                "Profile saved! Referral request submitted.",
                                Toast.LENGTH_LONG
                        ).show();
                    } else {
                        Toast.makeText(
                                UserProfileSetupActivity.this,
                                "Profile saved, but referral request failed.",
                                Toast.LENGTH_LONG
                        ).show();
                    }

                    MainActivity.goToMainScreen(UserProfileSetupActivity.this);
                });
    }

    private long getLong(DataSnapshot snapshot) {
        Long value = snapshot.getValue(Long.class);
        return value != null ? value : 0L;
    }

    private String getString(DataSnapshot snapshot) {
        String value = snapshot.getValue(String.class);
        return value != null ? value.trim() : "";
    }

    private boolean getBoolean(DataSnapshot snapshot) {
        Boolean value = snapshot.getValue(Boolean.class);
        return value != null && value;
    }
}
