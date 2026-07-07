package com.harsh.lwinr;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LuckyDrawActivity extends AppCompatActivity {

    private static final String TAG = "LuckyDraw";
    private static final String DB_URL = "https://lwinr-6f076-default-rtdb.asia-southeast1.firebasedatabase.app";
    private static final int MAX_TOKENS_PER_USER = 20;
    private static final int MAX_TOTAL_TOKENS = 2500;
    private static final int MIN_TOKENS_REQUIRED = 1000;
    private static final String AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";

    private FirebaseAuth mAuth;
    private FirebaseDatabase database;
    private DatabaseReference contestRef;
    private DatabaseReference historyRef;
    private DatabaseReference userContestsRef;
    private DatabaseReference entriesRef;

    private String userId;
    private String currentContestId;
    private long currentTotalTokens = 0;
    private ValueEventListener contestListener;

    private TextView tvContestStatus;
    private TextView tvTimer;
    private TextView tvMyTokens;
    private TextView tvTotalParticipants;
    private TextView tvPrizePool;
    private TextView tvValidationStatus;
    private TextView tvGetTokenLabel;

    private View btnGetToken;
    private Button btnViewHistory;
    private Button btnMyContests;

    private RecyclerView rvParticipants;
    private ProgressBar progressLoading;
    private LinearLayout layoutMyTokensList;
    private LinearLayout layoutContestClosed;

    private RewardedAd rewardedAd;
    private CountDownTimer contestTimer;

    private int myTokenCount = 0;
    private final List<Integer> myTokens = new ArrayList<>();
    private final List<Participant> participantList = new ArrayList<>();
    private ParticipantAdapter participantAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lucky_draw);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("🎲 Lucky Draw");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (!initFirebase()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        setupButtons();
        loadRewardedAd();
        loadCurrentContest();
    }

    private boolean initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            return false;
        }

        userId = currentUser.getUid();
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }

        database = FirebaseDatabase.getInstance(DB_URL);
        contestRef = database.getReference("luckyDraw/currentContest");
        historyRef = database.getReference("luckyDraw/history");
        userContestsRef = database.getReference("userContests").child(userId);
        entriesRef = database.getReference("luckyDrawEntries");

        Log.d(TAG, "Firebase initialized. UserId: " + userId);
        return true;
    }

    private void initViews() {
        tvContestStatus = findViewById(R.id.tvContestStatus);
        tvTimer = findViewById(R.id.tvTimer);
        tvMyTokens = findViewById(R.id.tvMyTokens);
        tvTotalParticipants = findViewById(R.id.tvTotalParticipants);
        tvPrizePool = findViewById(R.id.tvPrizePool);
        tvValidationStatus = findViewById(R.id.tvValidationStatus);

        btnGetToken = findViewById(R.id.btnGetToken);
        tvGetTokenLabel = findViewById(R.id.tvGetTokenLabel);
        btnViewHistory = findViewById(R.id.btnViewHistory);
        btnMyContests = findViewById(R.id.btnMyContests);

        rvParticipants = findViewById(R.id.rvParticipants);
        progressLoading = findViewById(R.id.progressLoading);
        layoutMyTokensList = findViewById(R.id.layoutMyTokensList);
        layoutContestClosed = findViewById(R.id.layoutContestClosed);

        if (tvPrizePool != null) {
            tvPrizePool.setText("₹50 (500 Points)");
        }

        if (rvParticipants != null) {
            rvParticipants.setLayoutManager(new LinearLayoutManager(this));
            participantAdapter = new ParticipantAdapter(participantList);
            rvParticipants.setAdapter(participantAdapter);
        }

        updateMyTokensList();
    }

    private void setupButtons() {
        if (btnGetToken != null) {
            btnGetToken.setOnClickListener(v -> showAdForToken());
        }

        if (btnViewHistory != null) {
            btnViewHistory.setOnClickListener(v -> showHistoryDialog());
        }

        if (btnMyContests != null) {
            btnMyContests.setOnClickListener(v -> showMyContestsDialog());
        }
    }

    private void loadLiveParticipants() {
        if (currentContestId == null || currentContestId.trim().isEmpty()) {
            participantList.clear();
            if (participantAdapter != null) participantAdapter.notifyDataSetChanged();
            return;
        }

        entriesRef.child(currentContestId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                participantList.clear();
                int index = 1;

                if (snapshot.exists()) {
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        String uId = userSnapshot.getKey();
                        if (uId == null) continue;

                        String displayName = userSnapshot.child("name").getValue(String.class);
                        if (displayName == null || displayName.trim().isEmpty()) {
                            displayName = "Player";
                        }

                        DataSnapshot tokensSnap = userSnapshot.child("tokens");
                        if (tokensSnap.exists()) {
                            for (DataSnapshot tokenSnapshot : tokensSnap.getChildren()) {
                                Integer tokenVal = tokenSnapshot.getValue(Integer.class);
                                if (tokenVal != null) {
                                    participantList.add(new Participant(
                                            index++,
                                            displayName,
                                            "Token #" + tokenVal
                                    ));
                                }
                            }
                        }
                    }
                }

                if (participantAdapter != null) {
                    participantAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load participants: " + error.getMessage());
            }
        });
    }

    // 1. Data Model Class
    public static class Participant {
        int index;
        String username;
        String tokenNo;

        public Participant(int index, String username, String tokenNo) {
            this.index = index;
            this.username = username;
            this.tokenNo = tokenNo;
        }
    }

    public class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.ViewHolder> {
        private final List<Participant> list;

        public ParticipantAdapter(List<Participant> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_participant_token, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int firstIndex = position * 2;
            int secondIndex = firstIndex + 1;

            Participant p1 = list.get(firstIndex);
            holder.tvIndex1.setText(String.valueOf(p1.index));
            holder.tvUsername1.setText(p1.username);
            holder.tvTokenNo1.setText(p1.tokenNo);
            holder.leftContainer.setVisibility(View.VISIBLE);

            if (secondIndex < list.size()) {
                Participant p2 = list.get(secondIndex);
                holder.tvIndex2.setText(String.valueOf(p2.index));
                holder.tvUsername2.setText(p2.username);
                holder.tvTokenNo2.setText(p2.tokenNo);
                holder.rightContainer.setVisibility(View.VISIBLE);
            } else {
                holder.rightContainer.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return (list.size() + 1) / 2;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout leftContainer, rightContainer;
            TextView tvIndex1, tvUsername1, tvTokenNo1;
            TextView tvIndex2, tvUsername2, tvTokenNo2;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                leftContainer = itemView.findViewById(R.id.leftContainer);
                rightContainer = itemView.findViewById(R.id.rightContainer);

                tvIndex1 = itemView.findViewById(R.id.tvIndex1);
                tvUsername1 = itemView.findViewById(R.id.tvUsername1);
                tvTokenNo1 = itemView.findViewById(R.id.tvTokenNo1);

                tvIndex2 = itemView.findViewById(R.id.tvIndex2);
                tvUsername2 = itemView.findViewById(R.id.tvUsername2);
                tvTokenNo2 = itemView.findViewById(R.id.tvTokenNo2);
            }
        }
    }

    private void loadCurrentContest() {
        if (progressLoading != null) {
            progressLoading.setVisibility(View.VISIBLE);
        }

        if (contestListener != null && contestRef != null) {
            contestRef.removeEventListener(contestListener);
        }

        contestListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (progressLoading != null) {
                    progressLoading.setVisibility(View.GONE);
                }

                if (!snapshot.exists()) {
                    showNoContestUI();
                    return;
                }

                String status = snapshot.child("status").getValue(String.class);
                Long endTimeValue = snapshot.child("endTime").getValue(Long.class);
                long endTime = endTimeValue != null ? endTimeValue : 0L;
                currentContestId = snapshot.child("contestId").getValue(String.class);

                Long totalTokensValue = snapshot.child("totalTokens").getValue(Long.class);
                currentTotalTokens = totalTokensValue != null ? totalTokensValue : 0L;

                long now = System.currentTimeMillis();

                if (currentContestId == null || currentContestId.trim().isEmpty()) {
                    showNoContestUI();
                    return;
                }

                if ("open".equals(status)) {
                    if (now > endTime) {
                        updateEndedPendingUI(snapshot);
                    } else {
                        updateOpenContestUI(snapshot, endTime);
                    }
                } else if ("processing".equals(status)) {
                    updateProcessingUI(snapshot);
                } else if ("completed".equals(status) || "cancelled".equals(status)) {
                    updateFinishedUI(snapshot, status);
                } else {
                    showNoContestUI();
                }
                loadLiveParticipants();
                loadMyEntryForCurrentContest();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (progressLoading != null) {
                    progressLoading.setVisibility(View.GONE);
                }
                Toast.makeText(LuckyDrawActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        contestRef.addValueEventListener(contestListener);
    }

    private void loadMyEntryForCurrentContest() {
        if (currentContestId == null || currentContestId.trim().isEmpty()) {
            myTokenCount = 0;
            myTokens.clear();

            if (tvMyTokens != null) {
                tvMyTokens.setText("My Tokens: 0/20");
            }

            updateMyTokensList();
            return;
        }

        entriesRef.child(currentContestId).child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        myTokens.clear();
                        myTokenCount = getIntValue(snapshot.child("tokenCount"));

                        for (DataSnapshot token : snapshot.child("tokens").getChildren()) {
                            Integer tokenValue = token.getValue(Integer.class);
                            if (tokenValue != null) {
                                myTokens.add(tokenValue);
                            }
                        }

                        Collections.sort(myTokens);

                        if (tvMyTokens != null) {
                            tvMyTokens.setText("My Tokens: " + myTokenCount + "/20");
                        }

                        updateMyTokensList();
                        updateTokenButtonState((int) currentTotalTokens);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(LuckyDrawActivity.this, "Error loading tokens", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateOpenContestUI(DataSnapshot snapshot, long endTime) {
        stopContestTimerIfRunning();

        if (tvContestStatus != null) {
            tvContestStatus.setText("Contest LIVE");
            tvContestStatus.setBackgroundResource(R.drawable.bg_gold_status_chip);
            tvContestStatus.setTextColor(0xFF1D1D1D); // Clean dark readable text
        }
        if (layoutContestClosed != null) layoutContestClosed.setVisibility(View.GONE);
        if (btnGetToken != null) btnGetToken.setVisibility(View.VISIBLE);

        int tokens = getIntValue(snapshot.child("totalTokens"));
        if (tvTotalParticipants != null) {
            tvTotalParticipants.setText("Participants: " + tokens + " / " + MAX_TOTAL_TOKENS);
        }

        if (tvValidationStatus != null) {
            if (tokens < MIN_TOKENS_REQUIRED) {
                int needed = MIN_TOKENS_REQUIRED - tokens;
                tvValidationStatus.setText("⚠️ Need " + needed + " more tokens to validate");
                tvValidationStatus.setTextColor(0xFFE4C17B);
                tvValidationStatus.setBackgroundResource(R.drawable.bg_warning_chip_dark);
            } else {
                tvValidationStatus.setText("✅ Contest VALID");
                tvValidationStatus.setTextColor(0xFF35E6FF);
                tvValidationStatus.setBackgroundResource(R.drawable.bg_blue_chip_dark);
            }
        }

        startContestTimer(endTime);
        updateTokenButtonState(tokens);
    }

    private void updateEndedPendingUI(DataSnapshot snapshot) {
        stopContestTimerIfRunning();

        if (tvContestStatus != null) tvContestStatus.setText("⏳ Contest Ended");
        if (tvTimer != null) tvTimer.setText("Result pending from admin");
        if (btnGetToken != null) btnGetToken.setVisibility(View.GONE);
        if (layoutContestClosed != null) layoutContestClosed.setVisibility(View.VISIBLE);

        int tokens = getIntValue(snapshot.child("totalTokens"));
        if (tvTotalParticipants != null) {
            tvTotalParticipants.setText("Participants: " + tokens + " / " + MAX_TOTAL_TOKENS);
        }

        if (tvValidationStatus != null) {
            if (tokens < MIN_TOKENS_REQUIRED) {
                tvValidationStatus.setText("⚠️ Waiting for admin action (possible cancel/refund)");
                tvValidationStatus.setTextColor(0xFFE67E22);
            } else {
                tvValidationStatus.setText("⏳ Waiting for admin to declare result");
                tvValidationStatus.setTextColor(0xFFE67E22);
            }
        }
    }

    private void updateProcessingUI(DataSnapshot snapshot) {
        stopContestTimerIfRunning();

        if (tvContestStatus != null) {
            tvContestStatus.setText("Result Processing");
            tvContestStatus.setBackgroundResource(R.drawable.bg_warning_chip_dark); // Using dark golden warning accent
            tvContestStatus.setTextColor(0xFFE4C17B);
        }
        if (tvTimer != null) tvTimer.setText("Please wait");
        if (btnGetToken != null) btnGetToken.setVisibility(View.GONE);
        if (layoutContestClosed != null) layoutContestClosed.setVisibility(View.VISIBLE);

        if (tvValidationStatus != null) {
            tvValidationStatus.setText("Admin is processing this contest");
            tvValidationStatus.setTextColor(0xFFE4C17B);
            tvValidationStatus.setBackgroundResource(R.drawable.bg_warning_chip_dark);
        }

        int tokens = getIntValue(snapshot.child("totalTokens"));
        if (tvTotalParticipants != null) {
            tvTotalParticipants.setText("Participants: " + tokens + " / " + MAX_TOTAL_TOKENS);
        }
    }

    private void updateFinishedUI(DataSnapshot snapshot, String status) {
        stopContestTimerIfRunning();

        if (btnGetToken != null) btnGetToken.setVisibility(View.GONE);
        if (layoutContestClosed != null) layoutContestClosed.setVisibility(View.VISIBLE);

        int tokens = getIntValue(snapshot.child("totalTokens"));
        if (tvTotalParticipants != null) {
            tvTotalParticipants.setText("Participants: " + tokens + " / " + MAX_TOTAL_TOKENS);
        }

        if ("completed".equals(status)) {
            if (tvContestStatus != null) {
                tvContestStatus.setText("Contest Completed");
                tvContestStatus.setBackgroundResource(R.drawable.bg_gold_status_chip);
                tvContestStatus.setTextColor(0xFF1D1D1D);
            }
            if (tvTimer != null) tvTimer.setText("Check history for result");
            if (tvValidationStatus != null) {
                tvValidationStatus.setText("Result declared");
                tvValidationStatus.setTextColor(0xFF27AE60);
                tvValidationStatus.setBackgroundResource(R.drawable.bg_blue_chip_dark);
            }
        } else {
            if (tvContestStatus != null) {
                tvContestStatus.setText("Contest Cancelled");
                tvContestStatus.setBackgroundResource(R.drawable.bg_warning_chip_dark);
                tvContestStatus.setTextColor(0xFFE74C3C); // Solid neon warning red
            }
            if (tvTimer != null) tvTimer.setText("Check history / refund status");
            if (tvValidationStatus != null) {
                tvValidationStatus.setText("Contest was cancelled");
                tvValidationStatus.setTextColor(0xFFE74C3C);
                tvValidationStatus.setBackgroundResource(R.drawable.bg_warning_chip_dark);
            }
        }
    }

    private void showNoContestUI() {
        stopContestTimerIfRunning();

        currentContestId = null;
        currentTotalTokens = 0;
        myTokenCount = 0;
        myTokens.clear();
        updateMyTokensList();

        if (tvContestStatus != null) tvContestStatus.setText("⏳ No Live Contest");
        if (tvTimer != null) tvTimer.setText("Wait for admin to start next contest");
        if (tvMyTokens != null) tvMyTokens.setText("My Tokens: 0/20");
        if (tvTotalParticipants != null) tvTotalParticipants.setText("Participants: 0 / " + MAX_TOTAL_TOKENS);

        if (tvValidationStatus != null) {
            tvValidationStatus.setText("New contest will appear here");
            tvValidationStatus.setTextColor(0xFF7F8C8D);
        }

        if (btnGetToken != null) btnGetToken.setVisibility(View.GONE);
        if (layoutContestClosed != null) layoutContestClosed.setVisibility(View.VISIBLE);
    }

    private void updateTokenButtonState(int totalTokens) {
        if (btnGetToken == null || tvGetTokenLabel == null) return;

        if (currentContestId == null || currentContestId.trim().isEmpty()) {
            btnGetToken.setEnabled(false);
            tvGetTokenLabel.setText("NO LIVE CONTEST");
        } else if (totalTokens >= MAX_TOTAL_TOKENS) {
            btnGetToken.setEnabled(false);
            tvGetTokenLabel.setText("CONTEST FULL (2500 TOKENS)");
        } else if (myTokenCount >= MAX_TOKENS_PER_USER) {
            btnGetToken.setEnabled(false);
            tvGetTokenLabel.setText("MAX TOKENS REACHED (20/20)");
        } else {
            btnGetToken.setEnabled(true);
            tvGetTokenLabel.setText("🎁 WATCH AD → GET TOKEN");
        }
    }

    private void startContestTimer(long endTime) {
        stopContestTimerIfRunning();

        long remainingTime = endTime - System.currentTimeMillis();
        if (remainingTime <= 0) {
            if (tvTimer != null) tvTimer.setText("Result pending");
            if (btnGetToken != null) btnGetToken.setVisibility(View.GONE);
            if (layoutContestClosed != null) layoutContestClosed.setVisibility(View.VISIBLE);
            return;
        }

        contestTimer = new CountDownTimer(remainingTime, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long hours = millisUntilFinished / (60 * 60 * 1000);
                long mins = (millisUntilFinished % (60 * 60 * 1000)) / (60 * 1000);
                long secs = (millisUntilFinished % (60 * 1000)) / 1000;

                if (tvTimer != null) {
                    tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs));
                }
            }

            @Override
            public void onFinish() {
                if (tvTimer != null) tvTimer.setText("Result pending");
                if (btnGetToken != null) btnGetToken.setVisibility(View.GONE);
                if (layoutContestClosed != null) layoutContestClosed.setVisibility(View.VISIBLE);
                if (tvContestStatus != null) tvContestStatus.setText("⏳ Contest Ended");

                if (tvValidationStatus != null) {
                    tvValidationStatus.setText("Waiting for admin result");
                    tvValidationStatus.setTextColor(0xFFE67E22);
                }
            }
        }.start();
    }

    private void stopContestTimerIfRunning() {
        if (contestTimer != null) {
            contestTimer.cancel();
            contestTimer = null;
        }
    }

    private void showAdForToken() {
        if (currentContestId == null) {
            Toast.makeText(this, "No live contest available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rewardedAd == null) {
            Toast.makeText(this, "Ad not ready. Please wait...", Toast.LENGTH_SHORT).show();
            loadRewardedAd();
            return;
        }

        rewardedAd.show(this, this::onUserEarnedReward);
    }

    private void onUserEarnedReward(@NonNull RewardItem rewardItem) {
        addTokenToUserEntry();
        rewardedAd = null;
        loadRewardedAd();
    }

    private void addTokenToUserEntry() {
        if (currentContestId == null || currentContestId.trim().isEmpty()) {
            Toast.makeText(this, "No live contest available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnGetToken != null) {
            btnGetToken.setEnabled(false);
        }

        contestRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot contestSnapshot) {
                String status = contestSnapshot.child("status").getValue(String.class);
                Long endTimeValue = contestSnapshot.child("endTime").getValue(Long.class);
                long endTime = endTimeValue != null ? endTimeValue : 0L;
                String contestId = contestSnapshot.child("contestId").getValue(String.class);

                if (contestId == null || !contestId.equals(currentContestId)) {
                    enableTokenButtonAfterAction();
                    Toast.makeText(LuckyDrawActivity.this, "Contest changed. Please try again.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!"open".equals(status) || System.currentTimeMillis() > endTime) {
                    enableTokenButtonAfterAction();
                    Toast.makeText(LuckyDrawActivity.this, "Contest closed", Toast.LENGTH_SHORT).show();
                    return;
                }

                DatabaseReference myEntryRef = entriesRef.child(currentContestId).child(userId);

                myEntryRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot myEntrySnapshot) {
                        int existingCount = getIntValue(myEntrySnapshot.child("tokenCount"));

                        if (existingCount >= MAX_TOKENS_PER_USER) {
                            enableTokenButtonAfterAction();
                            Toast.makeText(LuckyDrawActivity.this, "Max token limit reached", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        contestRef.child("totalTokens").runTransaction(new Transaction.Handler() {
                            @NonNull
                            @Override
                            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                                Long current = currentData.getValue(Long.class);
                                if (current == null) current = 0L;

                                if (current >= MAX_TOTAL_TOKENS) {
                                    return Transaction.abort();
                                }

                                currentData.setValue(current + 1);
                                return Transaction.success(currentData);
                            }

                            @Override
                            public void onComplete(DatabaseError error, boolean committed, DataSnapshot totalSnapshot) {
                                if (error != null) {
                                    enableTokenButtonAfterAction();
                                    Toast.makeText(LuckyDrawActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                if (!committed) {
                                    enableTokenButtonAfterAction();
                                    Toast.makeText(LuckyDrawActivity.this, "Contest full", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Long newTotalLong = totalSnapshot.getValue(Long.class);
                                int newTokenNumber = newTotalLong != null ? newTotalLong.intValue() : 0;

                                myEntryRef.runTransaction(new Transaction.Handler() {
                                    @NonNull
                                    @Override
                                    public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                                        Integer tokenCountValue = currentData.child("tokenCount").getValue(Integer.class);
                                        int tokenCount = tokenCountValue != null ? tokenCountValue : 0;

                                        if (tokenCount >= MAX_TOKENS_PER_USER) {
                                            return Transaction.abort();
                                        }

                                        List<Integer> tokens = new ArrayList<>();
                                        for (MutableData token : currentData.child("tokens").getChildren()) {
                                            Integer value = token.getValue(Integer.class);
                                            if (value != null) {
                                                tokens.add(value);
                                            }
                                        }

                                        if (!tokens.contains(newTokenNumber)) {
                                            tokens.add(newTokenNumber);
                                        }

                                        Collections.sort(tokens);

                                        currentData.child("tokens").setValue(tokens);
                                        currentData.child("tokenCount").setValue(tokens.size());
                                        currentData.child("updatedAt").setValue(System.currentTimeMillis());

                                        return Transaction.success(currentData);
                                    }

                                    @Override
                                    public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                                        if (error != null) {
                                            rollbackContestTokenIncrement();
                                            enableTokenButtonAfterAction();
                                            Toast.makeText(LuckyDrawActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                            return;
                                        }

                                        if (!committed) {
                                            rollbackContestTokenIncrement();
                                            enableTokenButtonAfterAction();
                                            Toast.makeText(LuckyDrawActivity.this, "Max token limit reached", Toast.LENGTH_SHORT).show();
                                            return;
                                        }

                                        Integer tokenCount = currentData.child("tokenCount").getValue(Integer.class);
                                        int usedTokens = tokenCount != null ? tokenCount : 0;

                                        userContestsRef.child(currentContestId).child("contestId").setValue(currentContestId);
                                        userContestsRef.child(currentContestId).child("timestamp").setValue(System.currentTimeMillis());
                                        userContestsRef.child(currentContestId).child("tokensUsed").setValue(usedTokens);
                                        userContestsRef.child(currentContestId).child("won").setValue(false);
                                        userContestsRef.child(currentContestId).child("cancelled").setValue(false);

                                        currentTotalTokens = newTokenNumber;

                                        Toast.makeText(LuckyDrawActivity.this, "✅ Token Added!", Toast.LENGTH_SHORT).show();
                                        loadMyEntryForCurrentContest();
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        enableTokenButtonAfterAction();
                        Toast.makeText(LuckyDrawActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                enableTokenButtonAfterAction();
                Toast.makeText(LuckyDrawActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void rollbackContestTokenIncrement() {
        contestRef.child("totalTokens").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Long current = currentData.getValue(Long.class);
                if (current == null || current <= 0) {
                    currentData.setValue(0);
                } else {
                    currentData.setValue(current - 1);
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            }
        });
    }

    private void enableTokenButtonAfterAction() {
        if (btnGetToken != null) {
            btnGetToken.setEnabled(true);
        }
    }

    private void updateMyTokensList() {
        if (layoutMyTokensList == null) return;

        layoutMyTokensList.removeAllViews();

        if (myTokens.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No tokens yet. Watch ads to get tokens!");
            tv.setTextSize(14);
            tv.setTextColor(0xFFB8C0D1); // Dynamic grey text mismatch dur karne ke liye
            tv.setPadding(16, 16, 16, 16);
            layoutMyTokensList.addView(tv);
            return;
        }

        for (int token : myTokens) {
            TextView tv = new TextView(this);
            tv.setText("🎟 Token #" + token);
            tv.setTextSize(15);
            tv.setTextColor(0xFFEAF0FA); // Text bright white/blue mix
            tv.setPadding(28, 16, 28, 16); // Padding increase ki premium spacing ke liye
            tv.setBackgroundResource(R.drawable.bg_token_item_chip); // Jo naya chip background banaya tha

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 10, 0, 10); // Thoda door door rahenge tokens list me
            tv.setLayoutParams(params);

            layoutMyTokensList.addView(tv);
        }
    }

    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(this, AD_UNIT_ID, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                rewardedAd = ad;
                Log.d(TAG, "Ad loaded");
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                rewardedAd = null;
                Log.d(TAG, "Ad failed: " + error.getMessage());
            }
        });
    }

    private void showHistoryDialog() {
        if (progressLoading != null) {
            progressLoading.setVisibility(View.VISIBLE);
        }

        historyRef.orderByChild("timestamp").limitToLast(20)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (progressLoading != null) {
                            progressLoading.setVisibility(View.GONE);
                        }

                        if (!snapshot.exists()) {
                            Toast.makeText(LuckyDrawActivity.this, "No history yet", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        StringBuilder historyText = new StringBuilder();
                        List<DataSnapshot> contests = new ArrayList<>();

                        for (DataSnapshot contest : snapshot.getChildren()) {
                            contests.add(contest);
                        }

                        Collections.reverse(contests);
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

                        for (DataSnapshot contest : contests) {
                            String winnerUid = contest.child("winnerUid").getValue(String.class);
                            String status = contest.child("status").getValue(String.class);
                            Long timestamp = contest.child("timestamp").getValue(Long.class);

                            String date = timestamp != null ? sdf.format(new Date(timestamp)) : "N/A";
                            String result;

                            if ("cancelled".equals(status)) {
                                result = "❌ Cancelled";
                            } else if ("none".equals(winnerUid)) {
                                result = "No Winner";
                            } else if (userId.equals(winnerUid)) {
                                result = "🎉 YOU WON!";
                            } else {
                                result = "User " + getShortUserId(winnerUid) + "...";
                            }

                            historyText.append("📅 ").append(date).append("\n");
                            historyText.append("🏆 ").append(result).append("\n");
                            historyText.append("━━━━━━━━━━━━━━━\n");
                        }

                        showTextDialog("📜 Contest History", historyText.toString());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (progressLoading != null) {
                            progressLoading.setVisibility(View.GONE);
                        }

                        Toast.makeText(LuckyDrawActivity.this, "Error loading history", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showMyContestsDialog() {
        if (progressLoading != null) {
            progressLoading.setVisibility(View.VISIBLE);
        }

        userContestsRef.orderByChild("timestamp").limitToLast(20)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (progressLoading != null) {
                            progressLoading.setVisibility(View.GONE);
                        }

                        if (!snapshot.exists()) {
                            new AlertDialog.Builder(LuckyDrawActivity.this)
                                    .setTitle("📋 My Contests")
                                    .setMessage("You haven't participated yet!\n\nWatch ads to get tokens and join contests.")
                                    .setPositiveButton("OK", null)
                                    .show();
                            return;
                        }

                        StringBuilder myContestsText = new StringBuilder();
                        List<DataSnapshot> contests = new ArrayList<>();

                        for (DataSnapshot contest : snapshot.getChildren()) {
                            contests.add(contest);
                        }

                        Collections.reverse(contests);
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

                        int totalWins = 0;
                        int totalTokensUsed = 0;
                        int totalCancelled = 0;

                        for (DataSnapshot contest : contests) {
                            Integer tokensUsed = contest.child("tokensUsed").getValue(Integer.class);
                            Boolean won = contest.child("won").getValue(Boolean.class);
                            Boolean cancelled = contest.child("cancelled").getValue(Boolean.class);
                            Long timestamp = contest.child("timestamp").getValue(Long.class);

                            String date = timestamp != null ? sdf.format(new Date(timestamp)) : "N/A";
                            int tokens = tokensUsed != null ? tokensUsed : 0;
                            boolean isWinner = won != null && won;
                            boolean isCancelled = cancelled != null && cancelled;

                            if (isWinner) totalWins++;
                            if (isCancelled) totalCancelled++;
                            totalTokensUsed += tokens;

                            if (isCancelled) {
                                myContestsText.append("❌ ");
                            } else if (isWinner) {
                                myContestsText.append("🏆 ");
                            } else {
                                myContestsText.append("🎟 ");
                            }

                            myContestsText.append(date).append("\n");
                            myContestsText.append("Tokens: ").append(tokens).append(" | ");

                            if (isCancelled) {
                                myContestsText.append("Cancelled");
                            } else if (isWinner) {
                                myContestsText.append("WON ₹50!");
                            } else {
                                myContestsText.append("Lost");
                            }

                            myContestsText.append("\n━━━━━━━━━━━━━━━\n");
                        }

                        String stats =
                                "Total Contests: " + contests.size() + "\n" +
                                        "Wins: " + totalWins + " 🎉 | Cancelled: " + totalCancelled + "\n" +
                                        "Tokens Used: " + totalTokensUsed + "\n" +
                                        "Win Rate: " + (contests.size() > 0 ? (totalWins * 100 / contests.size()) : 0) + "%\n\n" +
                                        "━━━━━━━━━━━━━━━\n";

                        showTextDialog("📋 My Contests", stats + myContestsText);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (progressLoading != null) {
                            progressLoading.setVisibility(View.GONE);
                        }

                        Toast.makeText(LuckyDrawActivity.this, "Error loading data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showTextDialog(String title, String text) {
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(14);
        textView.setPadding(32, 32, 32, 32);
        scrollView.addView(textView);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .show();
    }

    private int getIntValue(DataSnapshot snapshot) {
        Integer intValue = snapshot.getValue(Integer.class);
        if (intValue != null) return intValue;

        Long longValue = snapshot.getValue(Long.class);
        return longValue != null ? longValue.intValue() : 0;
    }

    private String getShortUserId(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return "???";
        }

        return uid.length() >= 6 ? uid.substring(0, 6) : uid;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopContestTimerIfRunning();

        if (contestListener != null && contestRef != null) {
            contestRef.removeEventListener(contestListener);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}