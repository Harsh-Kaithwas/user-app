package com.harsh.lwinr;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class CheckInActivity extends AppCompatActivity {

    private static final String TAG = "LwinR_AUTH";
    private static final String DB_URL = "https://lwinr-6f076-default-rtdb.asia-southeast1.firebasedatabase.app";

    private final int[] rewardCycle = {10, 20, 30, 40, 50, 60, 100};

    private TextView tvUserName, tvWalletPoints, tvTitle, tvSubtitle, tvStreak, tvCountdownLabel, tvCountdown, tvHelper;
    private CardView[] dayCards = new CardView[7];
    private TextView[] dayTitles = new TextView[7];
    private TextView[] dayPoints = new TextView[7];
    private TextView[] dayStatus = new TextView[7];

    private DatabaseReference rootRef;
    private DatabaseReference userRef;
    private DatabaseReference walletRef;
    private DatabaseReference serverTimeRef;

    private String userId;

    private long lastCheckIn = 0L;
    private long streak = 0L;
    private long currentWallet = 0L;
    private int reward = 10;

    private boolean alreadyCheckedToday = false;
    private int claimedDayCount = 0;
    private int claimableDayIndex = 0;
    private String lastCheckInDateKey = "";
    private long serverNow = 0L;

    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_in);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Daily Check-In");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        Log.d(TAG, "CheckIn onCreate currentUser=" + (currentUser != null)
                + ", uid=" + (currentUser != null ? currentUser.getUid() : "null"));

        if (currentUser == null || currentUser.getUid() == null || currentUser.getUid().trim().isEmpty()) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(CheckInActivity.this, LoginActivity.class));
            finish();
            return;
        }

        userId = currentUser.getUid();

        rootRef = FirebaseDatabase.getInstance(DB_URL).getReference();
        userRef = rootRef.child("users").child(userId);
        walletRef = userRef.child("mainWallet");
        serverTimeRef = userRef.child("serverNow");

        initViews();
        setupDayTileClicks();

        if (!isAutoDateTimeEnabled()) {
            Toast.makeText(this, "Please enable automatic date & time", Toast.LENGTH_LONG).show();
        }

        ensureWalletStructure();
        loadCheckInDataFromServerTime();
    }

    private void initViews() {
        tvUserName = findViewById(R.id.tvUserName);
        tvWalletPoints = findViewById(R.id.tvWalletPoints);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvStreak = findViewById(R.id.tvStreak);
        tvCountdownLabel = findViewById(R.id.tvCountdownLabel);
        tvCountdown = findViewById(R.id.tvCountdown);
        tvHelper = findViewById(R.id.tvHelper);

        dayCards[0] = findViewById(R.id.cardDay1);
        dayCards[1] = findViewById(R.id.cardDay2);
        dayCards[2] = findViewById(R.id.cardDay3);
        dayCards[3] = findViewById(R.id.cardDay4);
        dayCards[4] = findViewById(R.id.cardDay5);
        dayCards[5] = findViewById(R.id.cardDay6);
        dayCards[6] = findViewById(R.id.cardDay7);

        dayTitles[0] = findViewById(R.id.tvDay1Title);
        dayTitles[1] = findViewById(R.id.tvDay2Title);
        dayTitles[2] = findViewById(R.id.tvDay3Title);
        dayTitles[3] = findViewById(R.id.tvDay4Title);
        dayTitles[4] = findViewById(R.id.tvDay5Title);
        dayTitles[5] = findViewById(R.id.tvDay6Title);
        dayTitles[6] = findViewById(R.id.tvDay7Title);

        dayPoints[0] = findViewById(R.id.tvDay1Points);
        dayPoints[1] = findViewById(R.id.tvDay2Points);
        dayPoints[2] = findViewById(R.id.tvDay3Points);
        dayPoints[3] = findViewById(R.id.tvDay4Points);
        dayPoints[4] = findViewById(R.id.tvDay5Points);
        dayPoints[5] = findViewById(R.id.tvDay6Points);
        dayPoints[6] = findViewById(R.id.tvDay7Points);

        dayStatus[0] = findViewById(R.id.tvDay1Status);
        dayStatus[1] = findViewById(R.id.tvDay2Status);
        dayStatus[2] = findViewById(R.id.tvDay3Status);
        dayStatus[3] = findViewById(R.id.tvDay4Status);
        dayStatus[4] = findViewById(R.id.tvDay5Status);
        dayStatus[5] = findViewById(R.id.tvDay6Status);
        dayStatus[6] = findViewById(R.id.tvDay7Status);
    }

    private void ensureWalletStructure() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.child("wallet").exists()) return;

                Long mainWallet = snapshot.child("mainWallet").getValue(Long.class);
                Long bonusWallet = snapshot.child("bonusWallet").getValue(Long.class);

                long main = mainWallet != null ? mainWallet : 0L;
                long bonus = bonusWallet != null ? bonusWallet : 0L;

                Map<String, Object> updates = new HashMap<>();
                updates.put("wallet/total", main + bonus);
                updates.put("wallet/withdrawable", main);
                updates.put("wallet/bonus", bonus);
                updates.put("wallet/referralBonus", 0L);
                updates.put("wallet/updatedAt", ServerValue.TIMESTAMP);

                userRef.updateChildren(updates);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void creditCheckInRewardToWallet(int claimReward, long newStreak, String todayKey) {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long total = snapshot.child("wallet").child("total").getValue(Long.class);
                Long withdrawable = snapshot.child("wallet").child("withdrawable").getValue(Long.class);
                Long oldMainWallet = snapshot.child("mainWallet").getValue(Long.class);

                long totalVal = total != null ? total : 0L;
                long withdrawableVal = withdrawable != null ? withdrawable : 0L;
                long legacyMainVal = oldMainWallet != null ? oldMainWallet : 0L;

                Map<String, Object> updates = new HashMap<>();
                updates.put("wallet/total", totalVal + claimReward);
                updates.put("wallet/withdrawable", withdrawableVal + claimReward);
                updates.put("wallet/updatedAt", ServerValue.TIMESTAMP);

                updates.put("mainWallet", legacyMainVal + claimReward);
                updates.put("checkInStreak", newStreak);
                updates.put("lastCheckInDateKey", todayKey);
                updates.put("lastCheckIn", ServerValue.TIMESTAMP);

                userRef.updateChildren(updates, (error, ref) -> {
                    enableAllTiles();

                    if (error != null) {
                        Toast.makeText(CheckInActivity.this,
                                "Check-in failed: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(CheckInActivity.this,
                            "+" + claimReward + " points claimed! Streak: " + newStreak,
                            Toast.LENGTH_LONG).show();

                    loadCheckInDataFromServerTime();
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                enableAllTiles();
                Toast.makeText(CheckInActivity.this, "Check-in failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupDayTileClicks() {
        for (int i = 0; i < dayCards.length; i++) {
            final int index = i;
            dayCards[i].setOnClickListener(v -> onDayTileClicked(index));
        }
    }

    private void onDayTileClicked(int index) {
        if (!isAutoDateTimeEnabled()) {
            Toast.makeText(this, "Enable automatic date & time first", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
            } catch (Exception ignored) {
            }
            return;
        }

        if (alreadyCheckedToday) {
            Toast.makeText(this, "Today's reward already claimed ⏰", Toast.LENGTH_SHORT).show();
            return;
        }

        if (index != claimableDayIndex) {
            if (index < claimableDayIndex) {
                Toast.makeText(this, "This day is already claimed ✓", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "This reward is locked 🔒", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        claimCheckInWithServerTime();
    }

    private void loadCheckInDataFromServerTime() {
        fetchServerTime(new ServerTimeCallback() {
            @Override
            public void onTimeReceived(long serverTimestamp) {
                serverNow = serverTimestamp;
                loadUserData();
            }

            @Override
            public void onError() {
                Toast.makeText(CheckInActivity.this, "Failed to sync server time", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUserData() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long lastCheckInObj = snapshot.child("lastCheckIn").getValue(Long.class);
                Long streakObj = snapshot.child("checkInStreak").getValue(Long.class);
                Long walletObj = snapshot.child("wallet").child("total").getValue(Long.class);
                if (walletObj == null) {
                    walletObj = snapshot.child("mainWallet").getValue(Long.class);
                }

                String name = snapshot.child("name").getValue(String.class);
                String displayName = snapshot.child("displayName").getValue(String.class);
                String username = snapshot.child("username").getValue(String.class);

                if (name == null || name.trim().isEmpty()) name = displayName;
                if (name == null || name.trim().isEmpty()) name = username;
                if (name == null || name.trim().isEmpty()) name = "LwinR";

                String savedDateKey = snapshot.child("lastCheckInDateKey").getValue(String.class);

                lastCheckIn = lastCheckInObj != null ? lastCheckInObj : 0L;
                streak = streakObj != null ? streakObj : 0L;
                currentWallet = walletObj != null ? walletObj : 0L;
                lastCheckInDateKey = savedDateKey != null ? savedDateKey : "";

                String todayKey = getDateKeyFromTimestamp(serverNow);

                alreadyCheckedToday = todayKey.equals(lastCheckInDateKey);
                claimedDayCount = (int) (streak % 7);
                claimableDayIndex = claimedDayCount % 7;
                reward = rewardCycle[claimableDayIndex];

                tvUserName.setText(name);
                tvWalletPoints.setText(currentWallet + " PTS");
                tvTitle.setText("DAILY CHECK-IN");
                tvSubtitle.setText("7-Day Streak");
                tvStreak.setText(streak + " Day Streak!");

                updateDayCards();
                updateBottomSection();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CheckInActivity.this, "Failed to load check-in data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDayCards() {
        for (int i = 0; i < 7; i++) {
            dayTitles[i].setText(i == 6 ? "DAY 7: MEGA REWARD!" : "DAY " + (i + 1));
            dayPoints[i].setText("+" + rewardCycle[i] + " Points");

            if (alreadyCheckedToday) {
                if (i < claimedDayCount) {
                    setClaimedState(i);
                } else {
                    setLockedState(i);
                }
            } else {
                if (i < claimedDayCount) {
                    setClaimedState(i);
                } else if (i == claimableDayIndex) {
                    setActiveState(i);
                } else {
                    setLockedState(i);
                }
            }

            dayCards[i].setClickable(true);
            dayCards[i].setFocusable(true);
            dayCards[i].setEnabled(true);
        }
    }

    private void setClaimedState(int index) {
        if (index == 6) {
            dayCards[index].setBackgroundResource(R.drawable.bg_day7_claimed);
        } else {
            dayCards[index].setBackgroundResource(R.drawable.bg_checkin_day_claimed);
        }

        dayStatus[index].setText("Claimed ✓");
        dayStatus[index].setTextColor(0xFF65D38A);
        dayTitles[index].setTextColor(0xFFFFFFFF);
        dayPoints[index].setTextColor(0xFFB8F5CB);
        dayCards[index].setAlpha(1f);
    }

    private void setActiveState(int index) {
        if (index == 6) {
            dayCards[index].setBackgroundResource(R.drawable.bg_day7_active);
            dayStatus[index].setText("MEGA ACTIVE");
        } else {
            dayCards[index].setBackgroundResource(R.drawable.bg_checkin_day_current);
            dayStatus[index].setText("ACTIVE");
        }

        dayStatus[index].setTextColor(0xFF55C2FF);
        dayTitles[index].setTextColor(0xFFFFFFFF);
        dayPoints[index].setTextColor(0xFF7DD3FC);
        dayCards[index].setAlpha(1f);
    }

    private void setLockedState(int index) {
        if (index == 6) {
            dayCards[index].setBackgroundResource(R.drawable.bg_day7_locked);
        } else {
            dayCards[index].setBackgroundResource(R.drawable.bg_checkin_day_upcoming);
        }

        dayStatus[index].setText("Locked 🔒");
        dayStatus[index].setTextColor(0xFF94A3B8);
        dayTitles[index].setTextColor(0xFFD1D5DB);
        dayPoints[index].setTextColor(0xFF94A3B8);
        dayCards[index].setAlpha(0.78f);
    }

    private void updateBottomSection() {
        stopCountdown();

        if (alreadyCheckedToday) {
            tvCountdownLabel.setText("NEXT REWARD CLAIMABLE AFTER MIDNIGHT");
            startMidnightCountdownFromServer();
            int nextDay = (claimableDayIndex % 7) + 1;
            tvHelper.setText("Come back after 12:00 AM to claim Day " + nextDay);
        } else {
            tvCountdownLabel.setText("TODAY'S REWARD READY");
            tvCountdown.setText("Tap Day " + (claimableDayIndex + 1));
            tvHelper.setText("Tap the active tile to claim +" + reward + " points");
        }
    }

    private void startMidnightCountdownFromServer() {
        long millis = getMillisUntilNextMidnight(serverNow);
        countDownTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText(formatTime(millisUntilFinished));
            }

            @Override
            public void onFinish() {
                loadCheckInDataFromServerTime();
            }
        };
        countDownTimer.start();
    }

    private void claimCheckInWithServerTime() {
        fetchServerTime(new ServerTimeCallback() {
            @Override
            public void onTimeReceived(long claimServerTime) {
                String todayKey = getDateKeyFromTimestamp(claimServerTime);

                if (todayKey.equals(lastCheckInDateKey)) {
                    Toast.makeText(CheckInActivity.this, "Today's reward already claimed ⏰", Toast.LENGTH_SHORT).show();
                    return;
                }

                final int claimReward = reward;
                final long newStreak = streak + 1;

                for (CardView card : dayCards) {
                    card.setEnabled(false);
                }

                userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String latestDateKey = snapshot.child("lastCheckInDateKey").getValue(String.class);
                        if (latestDateKey == null) latestDateKey = "";

                        if (todayKey.equals(latestDateKey)) {
                            enableAllTiles();
                            Toast.makeText(CheckInActivity.this, "Today's reward already claimed ⏰", Toast.LENGTH_SHORT).show();
                            loadCheckInDataFromServerTime();
                            return;
                        }

                        creditCheckInRewardToWallet(claimReward, newStreak, todayKey);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        enableAllTiles();
                        Toast.makeText(CheckInActivity.this, "Check-in failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError() {
                Toast.makeText(CheckInActivity.this, "Failed to verify server time", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchServerTime(ServerTimeCallback callback) {
        serverTimeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener(unused ->
                serverTimeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Long ts = snapshot.getValue(Long.class);
                        if (ts == null || ts <= 0) {
                            callback.onError();
                            return;
                        }
                        callback.onTimeReceived(ts);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError();
                    }
                })
        ).addOnFailureListener(e -> callback.onError());
    }

    private String getDateKeyFromTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private long getMillisUntilNextMidnight(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return Math.max(calendar.getTimeInMillis() - timestamp, 0);
    }

    private boolean isAutoDateTimeEnabled() {
        try {
            return Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME, 0) == 1;
        } catch (Exception e) {
            return true;
        }
    }

    private void stopCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        return String.format(Locale.getDefault(),
                "%02d hrs : %02d mins : %02d secs",
                hours, minutes, seconds);
    }

    private void enableAllTiles() {
        for (CardView card : dayCards) {
            card.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCountdown();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private interface ServerTimeCallback {
        void onTimeReceived(long serverTimestamp);
        void onError();
    }
}