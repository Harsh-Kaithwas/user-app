package com.harsh.lwinr;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReferEarnActivity extends AppCompatActivity {

    private static final String TAG = "ReferEarnActivity";
    // Demo Firebase Realtime Database URL.
// Replace this with your own Firebase Database URL before running the project.
private static final String DB_URL =
        "https://YOUR-PROJECT-ID-default-rtdb.firebaseio.com";
    private FirebaseAuth mAuth;
    private TextView tvMyCode;
    private TextView tvTotalEarnings;
    private TextView tvReferralInfo;
    private TextView tabLeaderboard;
    private TextView tabMyReferrals;
    private ImageView btnCopyCode;
    private Button btnShareCode;
    private ProgressBar progressBar;

    private RecyclerView rvReferralLeaderboard;
    private ReferralListAdapter referralAdapter;
    private final List<ReferRankItem> referRankList = new ArrayList<>();

    private DatabaseReference usersRef;
    private DatabaseReference rootRef;
    private String userId;
    private String myReferralCode = "";
    private boolean showingMyReferrals = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refer_earn);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Referral Board");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }

        userId = currentUser.getUid();
        rootRef = FirebaseDatabase.getInstance(DB_URL).getReference();
        usersRef = rootRef.child("users");

        initViews();
        setupActions();
        loadReferralData();
        setLeaderboardTabSelected();
        loadReferralLeaderboard();
    }

    private void initViews() {
        tvMyCode = findViewById(R.id.tvMyCode);
        tvTotalEarnings = findViewById(R.id.tvTotalEarnings);
        tvReferralInfo = findViewById(R.id.tvReferralInfo);
        tabLeaderboard = findViewById(R.id.tabLeaderboard);
        tabMyReferrals = findViewById(R.id.tabMyReferrals);
        btnCopyCode = findViewById(R.id.btnCopyCode);
        btnShareCode = findViewById(R.id.btnShareCode);
        progressBar = findViewById(R.id.progressBar);
        rvReferralLeaderboard = findViewById(R.id.rvReferralLeaderboard);

        if (rvReferralLeaderboard != null) {
            rvReferralLeaderboard.setLayoutManager(new LinearLayoutManager(this));
            referralAdapter = new ReferralListAdapter(referRankList);
            rvReferralLeaderboard.setAdapter(referralAdapter);
        }
    }

    private void setupActions() {
        if (btnCopyCode != null) {
            btnCopyCode.setOnClickListener(v -> {
                String code = tvMyCode.getText().toString().trim();
                if (!code.isEmpty() && !code.equals("Loading...")) {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Referral Code", code));
                        Toast.makeText(this, "Code copied! 📋", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (btnShareCode != null) {
            btnShareCode.setOnClickListener(v -> shareReferralCode());
        }

        if (tabLeaderboard != null) {
            tabLeaderboard.setOnClickListener(v -> {
                showingMyReferrals = false;
                setLeaderboardTabSelected();
                loadReferralLeaderboard();
            });
        }

        if (tabMyReferrals != null) {
            tabMyReferrals.setOnClickListener(v -> {
                showingMyReferrals = true;
                setMyReferralsTabSelected();
                loadMyReferralsData();
            });
        }
    }

    private void setLeaderboardTabSelected() {
        if (tabLeaderboard != null) {
            tabLeaderboard.setBackgroundResource(R.drawable.bg_blue_chip_dark);
            tabLeaderboard.setTextColor(0xFF35E6FF);
        }

        if (tabMyReferrals != null) {
            tabMyReferrals.setBackgroundResource(R.drawable.bg_lucky_action_card);
            tabMyReferrals.setTextColor(0xFF7E6A45);
        }
    }

    private void setMyReferralsTabSelected() {
        if (tabMyReferrals != null) {
            tabMyReferrals.setBackgroundResource(R.drawable.bg_blue_chip_dark);
            tabMyReferrals.setTextColor(0xFF35E6FF);
        }

        if (tabLeaderboard != null) {
            tabLeaderboard.setBackgroundResource(R.drawable.bg_lucky_action_card);
            tabLeaderboard.setTextColor(0xFF7E6A45);
        }
    }

    private void loadReferralData() {
        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                myReferralCode = getString(snapshot.child("referralCode"));
                if (myReferralCode.isEmpty()) {
                    myReferralCode = "LwinRLtyThR";
                }

                long referralBonusTotal = getLong(snapshot.child("referralBonusTotal"));
                long referralsCount = getLong(snapshot.child("referralsCount"));
                long releasedTotal = getLong(snapshot.child("referralWithdrawableReleased"));
                String referredBy = getString(snapshot.child("referredBy"));

                if (tvMyCode != null) {
                    tvMyCode.setText(myReferralCode);
                }

                if (tvTotalEarnings != null) {
                    tvTotalEarnings.setText(referralBonusTotal + " PTS");
                }

                if (tvReferralInfo != null) {
                    String info = "Approved referrals: " + referralsCount
                            + " • Released: " + releasedTotal + " PTS";

                    if (!referredBy.isEmpty()) {
                        info = info + " • Referred by: " + referredBy;
                    }

                    tvReferralInfo.setText(info);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading referral data: " + error.getMessage());
            }
        });
    }

    private void loadReferralLeaderboard() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                referRankList.clear();
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                if (!snapshot.exists()) {
                    if (tvReferralInfo != null) {
                        tvReferralInfo.setText("No referral data found");
                    }
                    if (referralAdapter != null) referralAdapter.notifyDataSetChanged();
                    return;
                }

                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    String uid = userSnap.getKey();
                    String name = getDisplayName(userSnap);
                    long referralsCount = getLong(userSnap.child("referralsCount"));
                    long referralBonusTotal = getLong(userSnap.child("referralBonusTotal"));
                    long releasedTotal = getLong(userSnap.child("referralWithdrawableReleased"));

                    referRankList.add(new ReferRankItem(
                            uid,
                            name,
                            referralsCount,
                            referralBonusTotal,
                            releasedTotal,
                            "leaderboard"
                    ));
                }

                Collections.sort(referRankList, (o1, o2) -> {
                    int inviteCompare = Long.compare(o2.invites, o1.invites);
                    if (inviteCompare != 0) return inviteCompare;

                    int totalCompare = Long.compare(o2.bonusPoints, o1.bonusPoints);
                    if (totalCompare != 0) return totalCompare;

                    return o1.name.compareToIgnoreCase(o2.name);
                });

                if (tvReferralInfo != null) {
                    tvReferralInfo.setText("Leaderboard members: " + referRankList.size());
                }

                if (referralAdapter != null) {
                    referralAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Toast.makeText(ReferEarnActivity.this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMyReferralsData() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        rootRef.child("referralRequests").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot requestsSnapshot) {
                usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot usersSnapshot) {
                        referRankList.clear();
                        if (progressBar != null) progressBar.setVisibility(View.GONE);

                        int pendingCount = 0;
                        int approvedCount = 0;

                        if (usersSnapshot.exists()) {
                            for (DataSnapshot userSnap : usersSnapshot.getChildren()) {
                                String uid = userSnap.getKey();
                                if (uid == null) continue;

                                String referredByUserId = getString(userSnap.child("referredByUserId"));
                                if (!userId.equals(referredByUserId)) continue;

                                String name = getDisplayName(userSnap);
                                DataSnapshot earningSnap = usersSnapshot.child(userId)
                                        .child("referralEarnings")
                                        .child(uid);

                                long totalBonus = getLong(earningSnap.child("rewardTotal"));
                                long releasedToMain = getLong(earningSnap.child("releasedToMain"));
                                long remainingBonus = getLong(earningSnap.child("remainingBonus"));
                                long adsCount = getLong(earningSnap.child("adsCount"));

                                approvedCount++;
                                referRankList.add(new ReferRankItem(
                                        uid, name, 1, totalBonus, releasedToMain,
                                        "approved", remainingBonus, adsCount
                                ));
                            }
                        }

                        if (requestsSnapshot.exists()) {
                            for (DataSnapshot reqSnap : requestsSnapshot.getChildren()) {
                                String requestUserId = reqSnap.getKey();
                                if (requestUserId == null) continue;

                                String referrerUserId = getString(reqSnap.child("referredByUserId"));
                                String status = getString(reqSnap.child("status"));

                                if (!userId.equals(referrerUserId)) continue;
                                if (!status.isEmpty() && !status.equalsIgnoreCase("pending")) continue;

                                String pendingName = getString(reqSnap.child("userName"));
                                if (pendingName.isEmpty()) pendingName = "Pending User";

                                pendingCount++;
                                referRankList.add(new ReferRankItem(
                                        requestUserId, pendingName, 1, 0, 0,
                                        "pending", 0, 0
                                ));
                            }
                        }

                        Collections.sort(referRankList, (o1, o2) -> {
                            if (!o1.status.equalsIgnoreCase(o2.status)) {
                                if (o1.status.equalsIgnoreCase("pending")) return -1;
                                if (o2.status.equalsIgnoreCase("pending")) return 1;
                            }
                            return o1.name.compareToIgnoreCase(o2.name);
                        });

                        if (tvReferralInfo != null) {
                            tvReferralInfo.setText("Pending: " + pendingCount + " • Approved: " + approvedCount);
                        }

                        if (referralAdapter != null) referralAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        Toast.makeText(ReferEarnActivity.this, "Failed to load referrals", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Toast.makeText(ReferEarnActivity.this, "Failed to load referral requests", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void shareReferralCode() {
        String codeToShare = myReferralCode == null || myReferralCode.trim().isEmpty()
                ? tvMyCode.getText().toString().trim()
                : myReferralCode;

        String shareText = "Hey! Use my referral code: " + codeToShare
                + " to join LwinR and earn reward points! 🎁";

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Share via"));
    }

    private String getDisplayName(DataSnapshot userSnap) {
        String name = getString(userSnap.child("displayName"));
        if (name.isEmpty()) name = getString(userSnap.child("name"));
        if (name.isEmpty()) name = getString(userSnap.child("username"));

        String uid = userSnap.getKey();
        if (name.isEmpty()) {
            name = "User_" + (uid != null && uid.length() >= 4 ? uid.substring(0, 4) : "Guest");
        }

        return name;
    }

    private String getString(DataSnapshot snapshot) {
        String value = snapshot.getValue(String.class);
        return value != null ? value.trim() : "";
    }

    private long getLong(DataSnapshot snapshot) {
        Long value = snapshot.getValue(Long.class);
        return value != null ? value : 0L;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private static class ReferRankItem {
        String uid;
        String name;
        long invites;
        long bonusPoints;
        long releasedPoints;
        String status;
        long remainingBonus;
        long adsCount;

        ReferRankItem(String uid, String name, long invites, long bonusPoints,
                      long releasedPoints, String status) {
            this(uid, name, invites, bonusPoints, releasedPoints, status, 0L, 0L);
        }

        ReferRankItem(String uid, String name, long invites, long bonusPoints,
                      long releasedPoints, String status, long remainingBonus, long adsCount) {
            this.uid = uid;
            this.name = name;
            this.invites = invites;
            this.bonusPoints = bonusPoints;
            this.releasedPoints = releasedPoints;
            this.status = status;
            this.remainingBonus = remainingBonus;
            this.adsCount = adsCount;
        }
    }

    private class ReferralListAdapter extends RecyclerView.Adapter<ReferralListAdapter.ViewHolder> {
        private final List<ReferRankItem> list;

        ReferralListAdapter(List<ReferRankItem> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_leaderboard_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ReferRankItem item = list.get(position);
            boolean isCurrentUser = item.uid != null && item.uid.equals(userId);

            holder.tvRank.setText(String.valueOf(position + 1));
            holder.tvName.setText(isCurrentUser ? item.name + " (You)" : item.name);

            if (showingMyReferrals) {
                if ("pending".equalsIgnoreCase(item.status)) {
                    holder.tvInvites.setText("Status: Pending approval");
                    holder.tvPoints.setText("Waiting for admin approval");
                } else {
                    holder.tvInvites.setText("Ads watched: " + item.adsCount);
                    holder.tvPoints.setText(
                            "Total: " + item.bonusPoints
                                    + " • Released: " + item.releasedPoints
                                    + " • Left: " + item.remainingBonus
                    );
                }
            } else {
                holder.tvInvites.setText("Invites: " + item.invites);
                holder.tvPoints.setText(
                        item.bonusPoints + " PTS • Released: " + item.releasedPoints
                );
            }

            if (isCurrentUser) {
                holder.layoutRowContainer.setBackgroundResource(R.drawable.bg_lucky_action_card);
                holder.tvName.setTextColor(0xFF35E6FF);
            } else {
                holder.layoutRowContainer.setBackgroundResource(android.R.color.transparent);
                holder.tvName.setTextColor(0xFFEAF0FA);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvRank, tvName, tvInvites, tvPoints;
            LinearLayout layoutRowContainer;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRank = itemView.findViewById(R.id.tvRank);
                tvName = itemView.findViewById(R.id.tvName);
                tvInvites = itemView.findViewById(R.id.tvInvites);
                tvPoints = itemView.findViewById(R.id.tvPoints);
                layoutRowContainer = itemView.findViewById(R.id.layoutRowContainer);
            }
        }
    }
}
