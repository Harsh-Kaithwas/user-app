package com.harsh.lwinr;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
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

import java.util.Locale;

public class WalletActivity extends AppCompatActivity {

    private static final String DB_URL = "https://lwinr-6f076-default-rtdb.asia-southeast1.firebasedatabase.app";

    private TextView tvWithdrawableWallet, tvMainWallet, tvBonusWallet, tvReferralBonus,
            tvWalletTotal, tvTotalWithdrawn, tvWalletRs;
    private Button btnWithdrawNow;

    private FirebaseAuth mAuth;
    private DatabaseReference userRef;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("My Wallet");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null || currentUser.getUid() == null || currentUser.getUid().trim().isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        userId = currentUser.getUid();
        userRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(userId);

        initViews();
        loadWalletData();
    }

    private void initViews() {
        tvWithdrawableWallet = findViewById(R.id.tvWithdrawableWallet);
        tvMainWallet = findViewById(R.id.tvMainWallet);
        tvBonusWallet = findViewById(R.id.tvBonusWallet);
        tvReferralBonus = findViewById(R.id.tvReferralBonus);
        tvWalletTotal = findViewById(R.id.tvWalletTotal);
        tvTotalWithdrawn = findViewById(R.id.tvTotalWithdrawn);
        tvWalletRs = findViewById(R.id.tvWalletRs);
        btnWithdrawNow = findViewById(R.id.btnWithdrawNow);

        btnWithdrawNow.setOnClickListener(v ->
                startActivity(new Intent(WalletActivity.this, WithdrawActivity.class))
        );
    }

    private void loadWalletData() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long mainWallet = getLong(snapshot.child("mainWallet"));
                long bonusWalletTop = getLong(snapshot.child("bonusWallet"));
                long totalWithdrawn = getLong(snapshot.child("totalWithdrawn"));
                long referralBonusTop = getLong(snapshot.child("referralBonusTotal"));

                long walletWithdrawable = getLong(snapshot.child("wallet").child("withdrawable"));
                long walletBonus = getLong(snapshot.child("wallet").child("bonus"));
                long walletReferralBonus = getLong(snapshot.child("wallet").child("referralBonus"));
                long walletTotal = getLong(snapshot.child("wallet").child("total"));

                long finalWithdrawable = walletWithdrawable > 0 ? walletWithdrawable : mainWallet;
                long finalBonus = walletBonus > 0 ? walletBonus : bonusWalletTop;
                long finalReferralBonus = walletReferralBonus > 0 ? walletReferralBonus : referralBonusTop;
                long finalTotal = walletTotal > 0 ? walletTotal : (finalWithdrawable + finalBonus + finalReferralBonus);

                double withdrawableRs = finalWithdrawable / 100.0;

                tvWithdrawableWallet.setText(finalWithdrawable + " pts");
                tvMainWallet.setText(mainWallet + " pts");
                tvBonusWallet.setText(finalBonus + " pts");
                tvReferralBonus.setText(finalReferralBonus + " pts");
                tvWalletTotal.setText(finalTotal + " pts");
                tvTotalWithdrawn.setText(totalWithdrawn + " pts");
                tvWalletRs.setText("₹" + String.format(Locale.getDefault(), "%.2f", withdrawableRs));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(WalletActivity.this, "Failed to load wallet", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private long getLong(DataSnapshot snapshot) {
        Long value = snapshot.getValue(Long.class);
        return value != null ? value : 0L;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userRef != null) {
            loadWalletData();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}