package com.harsh.lwinr;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private static final String TAG = "LwinR_DEBUG";
    private static final String DB_URL =
            "https://lwinr-6f076-default-rtdb.asia-southeast1.firebasedatabase.app";

    private FirebaseAuth mAuth;
    private DatabaseReference rootRef;

    private GoogleSignInClient mGoogleSignInClient;
    private Button btnGoogleSignIn;
    private boolean isSignInInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        rootRef = FirebaseDatabase.getInstance(DB_URL).getReference();

        setupGoogleSignIn();
        setupUI();
        checkExistingSession();
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void setupUI() {
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);

        if (btnGoogleSignIn != null) {
            btnGoogleSignIn.setOnClickListener(v -> {
                if (isSignInInProgress) return;

                isSignInInProgress = true;
                btnGoogleSignIn.setEnabled(false);
                signInWithGoogle();
            });
        }
    }

    private void checkExistingSession() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        Log.d(TAG, "LoginActivity firebaseUser=" + (currentUser != null));

        if (currentUser == null) {
            return;
        }

        String currentUid = currentUser.getUid();
        if (currentUid == null || currentUid.trim().isEmpty()) {
            mAuth.signOut();
            return;
        }

        checkAndCreateUserInRealtimeDB(currentUser);
    }

    private void signInWithGoogle() {
        if (mGoogleSignInClient == null) {
            isSignInInProgress = false;
            if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
            Toast.makeText(this, "Google client not ready", Toast.LENGTH_SHORT).show();
            setupGoogleSignIn();
            return;
        }

        mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != RC_GOOGLE_SIGN_IN) {
            return;
        }

        isSignInInProgress = false;
        if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);

            if (account == null || account.getIdToken() == null) {
                Toast.makeText(this, "Google account/token NULL", Toast.LENGTH_SHORT).show();
                return;
            }

            firebaseAuthWithGoogle(account.getIdToken());

        } catch (ApiException e) {
            Log.e(TAG, "Google sign-in failed", e);
            Toast.makeText(this, "Google Sign-In failed: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                isSignInInProgress = false;
                if (btnGoogleSignIn != null) btnGoogleSignIn.setEnabled(true);
                Log.e(TAG, "Firebase auth failed", task.getException());
                Toast.makeText(LoginActivity.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null || user.getUid() == null || user.getUid().trim().isEmpty()) {
                Toast.makeText(LoginActivity.this, "UserId NULL after login!", Toast.LENGTH_LONG).show();
                return;
            }

            checkAndCreateUserInRealtimeDB(user);
        });
    }

    private void checkAndCreateUserInRealtimeDB(@NonNull FirebaseUser user) {
        String userId = user.getUid();
        DatabaseReference userRef = rootRef.child("users").child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("email", user.getEmail() != null ? user.getEmail() : "");
                    userData.put("mainWallet", 250L);
                    userData.put("bonusWallet", 0L);
                    userData.put("profileSetupDone", false);
                    userData.put("createdAt", System.currentTimeMillis());
                    userData.put("referralsCount", 0L);
                    userData.put("referralBonusTotal", 0L);
                    userData.put("referralWithdrawableReleased", 0L);
                    userData.put("referralRewardGiven", false);
                    userData.put("referredBy", "");
                    userData.put("referredByUserId", "");

                    String codePart = userId.length() >= 6 ? userId.substring(0, 6) : userId;
                    userData.put("referralCode", "LwinR" + codePart);

                    Map<String, Object> walletMap = new HashMap<>();
                    walletMap.put("bonus", 0L);
                    walletMap.put("referralBonus", 0L);
                    walletMap.put("total", 250L);
                    walletMap.put("withdrawable", 250L);
                    walletMap.put("updatedAt", System.currentTimeMillis());
                    userData.put("wallet", walletMap);

                    userRef.setValue(userData)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "User created in RTDB");
                                openProfileSetup();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "User create failed: " + e.getMessage());
                                openProfileSetup();
                            });
                } else {
                    Log.d(TAG, "User already exists in RTDB");
                    ensureUserDefaultsAndProceed(userRef);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "DB check cancelled: " + error.getMessage());
                openProfileSetup();
            }
        });
    }

    private void ensureUserDefaultsAndProceed(DatabaseReference userRef) {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, Object> patch = new HashMap<>();

                if (!snapshot.child("mainWallet").exists()) patch.put("mainWallet", 250L);
                if (!snapshot.child("bonusWallet").exists()) patch.put("bonusWallet", 0L);
                if (!snapshot.child("profileSetupDone").exists()) patch.put("profileSetupDone", false);
                if (!snapshot.child("referralsCount").exists()) patch.put("referralsCount", 0L);
                if (!snapshot.child("referralBonusTotal").exists()) patch.put("referralBonusTotal", 0L);
                if (!snapshot.child("referralWithdrawableReleased").exists()) patch.put("referralWithdrawableReleased", 0L);
                if (!snapshot.child("referralRewardGiven").exists()) patch.put("referralRewardGiven", false);
                if (!snapshot.child("referredBy").exists()) patch.put("referredBy", "");
                if (!snapshot.child("referredByUserId").exists()) patch.put("referredByUserId", "");

                if (!snapshot.child("referralCode").exists()) {
                    String uid = snapshot.getKey() != null ? snapshot.getKey() : "";
                    String codePart = uid.length() >= 6 ? uid.substring(0, 6) : uid;
                    patch.put("referralCode", "LwinR" + codePart);
                }

                if (!snapshot.child("wallet").exists()) {
                    patch.put("wallet/bonus", 0L);
                    patch.put("wallet/referralBonus", 0L);
                    patch.put("wallet/total", getLong(snapshot.child("mainWallet"), 250L));
                    patch.put("wallet/withdrawable", getLong(snapshot.child("mainWallet"), 250L));
                    patch.put("wallet/updatedAt", System.currentTimeMillis());
                } else {
                    if (!snapshot.child("wallet/bonus").exists()) patch.put("wallet/bonus", 0L);
                    if (!snapshot.child("wallet/referralBonus").exists()) patch.put("wallet/referralBonus", 0L);
                    if (!snapshot.child("wallet/total").exists()) patch.put("wallet/total", getLong(snapshot.child("mainWallet"), 250L));
                    if (!snapshot.child("wallet/withdrawable").exists()) patch.put("wallet/withdrawable", getLong(snapshot.child("mainWallet"), 250L));
                    if (!snapshot.child("wallet/updatedAt").exists()) patch.put("wallet/updatedAt", System.currentTimeMillis());
                }

                if (patch.isEmpty()) {
                    checkProfileAndProceed(snapshot.getKey());
                    return;
                }

                userRef.updateChildren(patch).addOnCompleteListener(task -> {
                    checkProfileAndProceed(snapshot.getKey());
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Defaults patch cancelled: " + error.getMessage());
                openProfileSetup();
            }
        });
    }

    private void checkProfileAndProceed(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            openProfileSetup();
            return;
        }

        rootRef.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean profileSetupDone = snapshot.child("profileSetupDone").getValue(Boolean.class);
                String displayName = snapshot.child("displayName").getValue(String.class);

                boolean isProfileReady = Boolean.TRUE.equals(profileSetupDone)
                        && displayName != null
                        && !displayName.trim().isEmpty();

                if (isProfileReady) {
                    goToMainScreen();
                } else {
                    openProfileSetup();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Profile check cancelled: " + error.getMessage());
                openProfileSetup();
            }
        });
    }

    private long getLong(DataSnapshot snapshot, long fallback) {
        Long value = snapshot.getValue(Long.class);
        return value != null ? value : fallback;
    }

    private void openProfileSetup() {
        Intent profileIntent = new Intent(LoginActivity.this, UserProfileSetupActivity.class);
        startActivity(profileIntent);
        finish();
    }

    private void goToMainScreen() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finishAffinity();
    }
}