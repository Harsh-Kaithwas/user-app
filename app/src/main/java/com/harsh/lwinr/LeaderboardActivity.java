package com.harsh.lwinr;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
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

public class LeaderboardActivity extends AppCompatActivity {

    private static final String TAG = "LeaderboardActivity";
    // Demo Firebase Realtime Database URL.
    // Replace this with your own Firebase Database URL in your local project.
    private static final String DB_URL =
        "https://YOUR-PROJECT-ID-default-rtdb.firebaseio.com";
    
    private FirebaseAuth mAuth;
    private TextView tvSummary;
    private ProgressBar progressBar;

    // Podium Views
    private TextView tvPod1Name, tvPod1Points;
    private TextView tvPod2Name, tvPod2Points;
    private TextView tvPod3Name, tvPod3Points;

    // Bottom Sticky Views
    private LinearLayout layoutCurrentUserRow;
    private TextView tvCurrentRank, tvCurrentName, tvCurrentPoints;

    private RecyclerView rvLeaderboard;
    private GlobalBoardAdapter leaderboardAdapter;
    private final List<GlobalUserRankItem> globalRankList = new ArrayList<>();

    private DatabaseReference usersRef;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Leaderboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        currentUserId = currentUser != null ? currentUser.getUid() : "";

        usersRef = FirebaseDatabase.getInstance(DB_URL).getReference("users");

        initViews();
        loadLeaderboardData();
    }

    private void initViews() {
        tvSummary = findViewById(R.id.tvSummary);
        progressBar = findViewById(R.id.progressBar);
        rvLeaderboard = findViewById(R.id.rvLeaderboard);

        // Podium Setup
        tvPod1Name = findViewById(R.id.tvPod1Name);
        tvPod1Points = findViewById(R.id.tvPod1Points);
        tvPod2Name = findViewById(R.id.tvPod2Name);
        tvPod2Points = findViewById(R.id.tvPod2Points);
        tvPod3Name = findViewById(R.id.tvPod3Name);
        tvPod3Points = findViewById(R.id.tvPod3Points);

        // Sticky Bottom Setup
        layoutCurrentUserRow = findViewById(R.id.layoutCurrentUserRow);
        tvCurrentRank = findViewById(R.id.tvCurrentRank);
        tvCurrentName = findViewById(R.id.tvCurrentName);
        tvCurrentPoints = findViewById(R.id.tvCurrentPoints);

        if (rvLeaderboard != null) {
            rvLeaderboard.setLayoutManager(new LinearLayoutManager(this));
            leaderboardAdapter = new GlobalBoardAdapter(globalRankList);
            rvLeaderboard.setAdapter(leaderboardAdapter);
        }
    }

    private void loadLeaderboardData() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                globalRankList.clear();
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                if (snapshot.exists()) {
                    List<GlobalUserRankItem> fullList = new ArrayList<>();

                    for (DataSnapshot userSnap : snapshot.getChildren()) {
                        String uid = userSnap.getKey();
                        String name = userSnap.child("displayName").getValue(String.class);
                        Long walletPoints = userSnap.child("mainWallet").getValue(Long.class);
                        Long inviteCount = userSnap.child("totalInvites").getValue(Long.class);

                        long points = walletPoints != null ? walletPoints : 0L;
                        long invites = inviteCount != null ? inviteCount : 0L;

                        if (name == null || name.trim().isEmpty()) {
                            name = "User_" + (uid != null && uid.length() >= 4 ? uid.substring(0, 4) : "Guest");
                        }

                        fullList.add(new GlobalUserRankItem(uid, name, points, invites));
                    }

                    // Sort descending based on points
                    Collections.sort(fullList, (o1, o2) -> Long.compare(o2.points, o1.points));

                    // 1. Populate Top 3 Podium
                    if (fullList.size() >= 1) {
                        GlobalUserRankItem p1 = fullList.get(0);
                        if (tvPod1Name != null) tvPod1Name.setText(p1.name);
                        if (tvPod1Points != null) tvPod1Points.setText(p1.points + " Pts");
                    } else {
                        if (tvPod1Name != null) tvPod1Name.setText("---");
                        if (tvPod1Points != null) tvPod1Points.setText("0 Pts");
                    }

                    if (fullList.size() >= 2) {
                        GlobalUserRankItem p2 = fullList.get(1);
                        if (tvPod2Name != null) tvPod2Name.setText(p2.name);
                        if (tvPod2Points != null) tvPod2Points.setText(p2.points + " Pts");
                    } else {
                        if (tvPod2Name != null) tvPod2Name.setText("---");
                        if (tvPod2Points != null) tvPod2Points.setText("0 Pts");
                    }

                    if (fullList.size() >= 3) {
                        GlobalUserRankItem p3 = fullList.get(2);
                        if (tvPod3Name != null) tvPod3Name.setText(p3.name);
                        if (tvPod3Points != null) tvPod3Points.setText(p3.points + " Pts");
                    } else {
                        if (tvPod3Name != null) tvPod3Name.setText("---");
                        if (tvPod3Points != null) tvPod3Points.setText("0 Pts");
                    }

                    // 2. Populate RecyclerView from 4th Rank onwards
                    for (int i = 3; i < fullList.size(); i++) {
                        globalRankList.add(fullList.get(i));
                    }

                    // 3. Update Sticky User Bottom Card
                    updateStickyUserBottomCard(fullList);

                    if (leaderboardAdapter != null) {
                        leaderboardAdapter.notifyDataSetChanged();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Toast.makeText(LeaderboardActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStickyUserBottomCard(List<GlobalUserRankItem> fullList) {
        if (layoutCurrentUserRow == null) return;

        int userRank = -1;
        GlobalUserRankItem currentUserItem = null;

        for (int i = 0; i < fullList.size(); i++) {
            if (fullList.get(i).uid != null && fullList.get(i).uid.equals(currentUserId)) {
                userRank = i + 1;
                currentUserItem = fullList.get(i);
                break;
            }
        }

        if (currentUserItem != null) {
            layoutCurrentUserRow.setVisibility(View.VISIBLE);
            if (tvCurrentRank != null) tvCurrentRank.setText(userRank + "th:");
            if (tvCurrentName != null) tvCurrentName.setText(currentUserItem.name + " (You)");
            if (tvCurrentPoints != null) tvCurrentPoints.setText("- " + currentUserItem.points + " Pts");
        } else {
            layoutCurrentUserRow.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private static class GlobalUserRankItem {
        String uid;
        String name;
        long points;
        long invites;

        GlobalUserRankItem(String uid, String name, long points, long invites) {
            this.uid = uid;
            this.name = name;
            this.points = points;
            this.invites = invites;
        }
    }

    private class GlobalBoardAdapter extends RecyclerView.Adapter<GlobalBoardAdapter.ViewHolder> {
        private final List<GlobalUserRankItem> list;

        public GlobalBoardAdapter(List<GlobalUserRankItem> list) {
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
            GlobalUserRankItem item = list.get(position);

            // Because list starts from 4th rank (index 3 in full list)
            int actualRank = position + 4;

            holder.tvRank.setText(actualRank + "th:");
            holder.tvName.setText(item.name);
            holder.tvInvites.setText("Invites: " + item.invites);
            holder.tvPoints.setText("- " + item.points + " Pts");

            // Regular rows transparent structure
            holder.layoutRowContainer.setBackgroundResource(android.R.color.transparent);
            holder.tvName.setTextColor(0xFFEAF0FA);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public TextView tvRank, tvName, tvInvites, tvPoints;
            public LinearLayout layoutRowContainer;

            public ViewHolder(@NonNull View itemView) {
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
