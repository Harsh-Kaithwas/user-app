package com.harsh.lwinr;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LwinR_DEBUG";
    private static final String AUTH_TAG = "LwinR_AUTH";
    private static final String DB_URL = "https://lwinr-6f076-default-rtdb.asia-southeast1.firebasedatabase.app";
    private static final String AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";
    private static final int AD_REWARD_POINTS = 10;
    private static final long REFERRAL_RELEASE_POINTS = 5L;

    private FirebaseAuth mAuth;

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private FirebaseDatabase database;
    private DatabaseReference rootRef;
    private DatabaseReference userRef;
    private DatabaseReference walletRef;

    private String userId;

    private TextView tvTotalPoints;
    private TextView tvUserName;
    private View btnWatchAd;

    private RewardedAd rewardedAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        initPrefsAndFirebase();
        if (!validateUserSession()) return;

        initViews();
        setupInsets();
        setupDrawer();
        setupButtons();
        setupAdMob();

        loadUserProfile();
        loadUserPoints();
        updateNavHeader();
        loadRewardedAd();
    }

    private void initPrefsAndFirebase() {
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            userId = currentUser.getUid();
        } else {
            userId = null;
        }

        database = FirebaseDatabase.getInstance(DB_URL);
        rootRef = database.getReference();
    }

    private boolean validateUserSession() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        Log.d(AUTH_TAG, "Main validateUserSession currentUser=" + (currentUser != null)
                + ", uid=" + (currentUser != null ? currentUser.getUid() : "null"));

        if (currentUser == null || currentUser.getUid() == null || currentUser.getUid().trim().isEmpty()) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return false;
        }

        userId = currentUser.getUid();
        userRef = rootRef.child("users").child(userId);
        walletRef = userRef.child("mainWallet");
        return true;
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        tvTotalPoints = findViewById(R.id.tvTotalPoints);
        tvUserName = findViewById(R.id.tvUserName);
        btnWatchAd = findViewById(R.id.btnWatchAd);
    }

    private void setupInsets() {
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }
    }

    private void setupDrawer() {
        ImageButton btnProfile = findViewById(R.id.btnProfile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        View navCheckIn = findViewById(R.id.navCheckIn);
        View navWallet = findViewById(R.id.navWallet);
        View navReferrals = findViewById(R.id.navReferrals);
        View navLeaderboard = findViewById(R.id.navLeaderboard);
        View navWithdraw = findViewById(R.id.navWithdraw);
        View navLogout = findViewById(R.id.navLogout);

        if (navCheckIn != null) {
            navCheckIn.setOnClickListener(v -> {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                Log.d(AUTH_TAG, "Open CheckIn from Main currentUser=" + (currentUser != null)
                        + ", uid=" + (currentUser != null ? currentUser.getUid() : "null"));

                if (currentUser == null || currentUser.getUid() == null || currentUser.getUid().trim().isEmpty()) {
                    Toast.makeText(MainActivity.this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                    return;
                }

                startActivity(new Intent(MainActivity.this, CheckInActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        if (navWallet != null) {
            navWallet.setOnClickListener(v -> {
                startActivity(new Intent(this, WalletActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        if (navReferrals != null) {
            navReferrals.setOnClickListener(v -> {
                startActivity(new Intent(this, ReferEarnActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        if (navLeaderboard != null) {
            navLeaderboard.setOnClickListener(v -> {
                startActivity(new Intent(this, LeaderboardActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        if (navWithdraw != null) {
            navWithdraw.setOnClickListener(v -> {
                openWithdrawScreen();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        if (navLogout != null) {
            navLogout.setOnClickListener(v -> {
                showSettingsDialog();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }
    }

    private void setupButtons() {
        View btnLuckyDraw = findViewById(R.id.btnLuckyDraw);
        if (btnLuckyDraw != null) {
            btnLuckyDraw.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, LuckyDrawActivity.class)));
        }

        View btnReferEarn = findViewById(R.id.btnReferEarn);
        if (btnReferEarn != null) {
            btnReferEarn.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, ReferEarnActivity.class)));
        }

        View btnWithdraw = findViewById(R.id.btnWithdraw);
        if (btnWithdraw != null) {
            btnWithdraw.setOnClickListener(v -> openWithdrawScreen());
        }

        ImageButton btnNotification = findViewById(R.id.btnNotification);
        if (btnNotification != null) {
            btnNotification.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, NotificationsActivity.class)));
        }

        View tileReferNews = findViewById(R.id.tileReferNews);
        if (tileReferNews != null) {
            tileReferNews.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, ReferEarnActivity.class)));
        }

        View tileLeaderboardNews = findViewById(R.id.tileLeaderboardNews);
        if (tileLeaderboardNews != null) {
            tileLeaderboardNews.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, LeaderboardActivity.class)));
        }

        View tileGoodiesNews = findViewById(R.id.tileGoodiesNews);
        if (tileGoodiesNews != null) {
            tileGoodiesNews.setOnClickListener(v ->
                    Toast.makeText(MainActivity.this, "LwinR goodies update coming soon", Toast.LENGTH_SHORT).show());
        }

        if (btnWatchAd != null) {
            btnWatchAd.setOnClickListener(v -> {
                if (rewardedAd != null) {
                    showRewardedAd();
                } else {
                    Toast.makeText(this, "Ad not ready yet, please wait...", Toast.LENGTH_SHORT).show();
                    loadRewardedAd();
                }
            });
        }
    }

    private void setupAdMob() {
        MobileAds.initialize(this, initializationStatus -> { });

        RequestConfiguration configuration = new RequestConfiguration.Builder()
                .setTestDeviceIds(Arrays.asList("F1B6698297E8C5BFBE45EF01C3D13EB3"))
                .build();

        MobileAds.setRequestConfiguration(configuration);
    }

    private void loadUserProfile() {
        userRef.child("displayName").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.getValue(String.class);
                if (tvUserName != null) {
                    if (name != null && !name.trim().isEmpty()) {
                        tvUserName.setText("Welcome, " + name);
                    } else {
                        tvUserName.setText("Welcome back");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Name load error: " + error.getMessage());
                if (tvUserName != null) {
                    tvUserName.setText("Welcome!");
                }
            }
        });
    }

    private void loadUserPoints() {
        if (walletRef == null) {
            if (tvTotalPoints != null) tvTotalPoints.setText("0");
            return;
        }

        walletRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long points = snapshot.getValue(Long.class);
                long safePoints = points != null ? points : 0L;

                if (tvTotalPoints != null) {
                    tvTotalPoints.setText(String.valueOf(safePoints));
                }

                updateHeaderPoints(safePoints);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Points load error: " + error.getMessage());
                if (tvTotalPoints != null) {
                    tvTotalPoints.setText("0");
                }
                updateHeaderPoints(0);
            }
        });
    }

    private void updateHeaderPoints(long points) {
        if (navView == null) return;

        TextView navPoints = navView.findViewById(R.id.tvHeaderPoints);
        if (navPoints != null) {
            navPoints.setText("Points: " + points);
        }
    }

    private void addPoints(int value) {
        if (userRef == null) return;

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long mainWallet = getLong(snapshot.child("mainWallet"));
                long walletWithdrawable = getLong(snapshot.child("wallet").child("withdrawable"));
                long walletBonus = getLong(snapshot.child("wallet").child("bonus"));
                long walletReferralBonus = getLong(snapshot.child("wallet").child("referralBonus"));
                long walletTotal = getLong(snapshot.child("wallet").child("total"));

                if (walletWithdrawable <= 0) {
                    walletWithdrawable = mainWallet;
                }

                if (walletTotal <= 0) {
                    walletTotal = walletWithdrawable + walletBonus;
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("mainWallet", mainWallet + value);
                updates.put("wallet/withdrawable", walletWithdrawable + value);
                updates.put("wallet/bonus", walletBonus);
                updates.put("wallet/referralBonus", walletReferralBonus);
                updates.put("wallet/total", walletTotal + value);
                updates.put("wallet/updatedAt", System.currentTimeMillis());

                userRef.updateChildren(updates)
                        .addOnSuccessListener(unused -> {
                            loadUserPoints();
                            updateNavHeader();
                            releaseReferralBonusOnAdWatch();
                            Toast.makeText(MainActivity.this, "+ " + value + " points!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Add points failed: " + e.getMessage());
                            Toast.makeText(MainActivity.this, "Failed to add points", Toast.LENGTH_SHORT).show();
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Add points read error: " + error.getMessage());
                Toast.makeText(MainActivity.this, "Failed to add points", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(this, AD_UNIT_ID, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                rewardedAd = null;
                Log.d(TAG, "Ad failed to load: " + error.getMessage());
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                rewardedAd = ad;
                Log.d(TAG, "Ad was loaded.");
            }
        });
    }

    private void showRewardedAd() {
        if (rewardedAd == null) {
            Toast.makeText(this, "Ad not ready, loading again...", Toast.LENGTH_SHORT).show();
            loadRewardedAd();
            return;
        }

        rewardedAd.show(this, rewardItem -> {
            addPoints(AD_REWARD_POINTS);
            rewardedAd = null;
            loadRewardedAd();
        });
    }

    private void releaseReferralBonusOnAdWatch() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                String referrerUid = userSnapshot.child("referredByUserId").getValue(String.class);
                if (referrerUid == null || referrerUid.trim().isEmpty()) {
                    return;
                }

                DatabaseReference referrerRef = rootRef.child("users").child(referrerUid);

                referrerRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot refSnap) {
                        if (!refSnap.exists()) {
                            return;
                        }

                        DataSnapshot earningSnap = refSnap.child("referralEarnings").child(userId);
                        long remainingBonus = getLong(earningSnap.child("remainingBonus"));
                        long releasedToMain = getLong(earningSnap.child("releasedToMain"));
                        long adsCount = getLong(earningSnap.child("adsCount"));

                        if (remainingBonus < REFERRAL_RELEASE_POINTS) {
                            return;
                        }

                        long mainWallet = getLong(refSnap.child("mainWallet"));
                        long bonusWallet = getLong(refSnap.child("bonusWallet"));
                        long referralReleasedTotal = getLong(refSnap.child("referralWithdrawableReleased"));

                        long walletBonus = getLong(refSnap.child("wallet").child("bonus"));
                        long walletReferralBonus = getLong(refSnap.child("wallet").child("referralBonus"));
                        long walletTotal = getLong(refSnap.child("wallet").child("total"));
                        long walletWithdrawable = getLong(refSnap.child("wallet").child("withdrawable"));

                        if (walletWithdrawable <= 0) {
                            walletWithdrawable = mainWallet;
                        }

                        if (walletTotal <= 0) {
                            walletTotal = walletWithdrawable + walletBonus;
                        }
                        if (bonusWallet < REFERRAL_RELEASE_POINTS || walletBonus < REFERRAL_RELEASE_POINTS) {
                            return;
                        }

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("mainWallet", mainWallet + REFERRAL_RELEASE_POINTS);
                        updates.put("bonusWallet", Math.max(0L, bonusWallet - REFERRAL_RELEASE_POINTS));
                        updates.put("referralWithdrawableReleased", referralReleasedTotal + REFERRAL_RELEASE_POINTS);

                        updates.put("wallet/bonus", Math.max(0L, walletBonus - REFERRAL_RELEASE_POINTS));
                        updates.put("wallet/referralBonus", walletReferralBonus);
                        updates.put("wallet/total", walletTotal);
                        updates.put("wallet/withdrawable", walletWithdrawable + REFERRAL_RELEASE_POINTS);
                        updates.put("wallet/updatedAt", System.currentTimeMillis());

                        updates.put("referralEarnings/" + userId + "/releasedToMain",
                                releasedToMain + REFERRAL_RELEASE_POINTS);
                        updates.put("referralEarnings/" + userId + "/remainingBonus",
                                remainingBonus - REFERRAL_RELEASE_POINTS);
                        updates.put("referralEarnings/" + userId + "/adsCount", adsCount + 1);
                        updates.put("referralEarnings/" + userId + "/updatedAt", System.currentTimeMillis());

                        referrerRef.updateChildren(updates)
                                .addOnSuccessListener(unused -> Log.d(TAG, "Referral bonus released successfully"))
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "Referral release failed: " + e.getMessage()));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Referrer load error: " + error.getMessage());
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "User load error during referral release: " + error.getMessage());
            }
        });
    }

    private long getLong(DataSnapshot snapshot) {
        Long value = snapshot.getValue(Long.class);
        return value != null ? value : 0L;
    }

    private void showLeaderboardDialog() {
        startActivity(new Intent(MainActivity.this, LeaderboardActivity.class));
    }

    private void openWithdrawScreen() {
        startActivity(new Intent(MainActivity.this, WithdrawActivity.class));
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(new String[]{"Logout"}, (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finishAffinity();
                })
                .show();
    }

    private void pushNotification(String userId, String title, String message, String type) {
        DatabaseReference notifRef = rootRef.child("users").child(userId).child("notifications").push();

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("message", message);
        data.put("type", type);
        data.put("isRead", false);
        data.put("createdAt", System.currentTimeMillis());

        notifRef.setValue(data);
    }

    private void updateNavHeader() {
        if (navView == null) return;

        TextView navName = navView.findViewById(R.id.tvHeaderName);
        TextView navEmail = navView.findViewById(R.id.tvHeaderEmail);
        TextView navPoints = navView.findViewById(R.id.tvHeaderPoints);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        String email = currentUser != null && currentUser.getEmail() != null
                ? currentUser.getEmail()
                : "user@example.com";

        if (navEmail != null) {
            navEmail.setText(email);
        }

        userRef.child("displayName").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.getValue(String.class);

                if (navName != null) {
                    navName.setText(name != null && !name.trim().isEmpty() ? name : "User");
                }

                if (walletRef != null && navPoints != null) {
                    walletRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot pointSnapshot) {
                            Long points = pointSnapshot.getValue(Long.class);
                            long safePoints = points != null ? points : 0L;
                            navPoints.setText("Points: " + safePoints);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            navPoints.setText("Points: 0");
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (navName != null) {
                    navName.setText("User");
                }
                if (navPoints != null) {
                    navPoints.setText("Points: 0");
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserPoints();
        loadUserProfile();
        updateNavHeader();
    }

    public static void goToMainScreen(AppCompatActivity activity) {
        activity.startActivity(new Intent(activity, MainActivity.class));
        activity.finishAffinity();
    }
}