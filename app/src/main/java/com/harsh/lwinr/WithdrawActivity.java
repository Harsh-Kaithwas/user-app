package com.harsh.lwinr;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WithdrawActivity extends AppCompatActivity {

    private static final int MIN_WITHDRAW = 1000;
    private static final long COOLDOWN_MILLIS = 24L * 60L * 60L * 1000L;
    // Demo Firebase Realtime Database URL.
// Replace this with your own Firebase Database URL before running the project.
private static final String DB_URL =
        "https://YOUR-PROJECT-ID-default-rtdb.firebaseio.com";
    private FirebaseDatabase database;
    private FirebaseAuth mAuth;
    private DatabaseReference usersRef, withdrawRef;
    private String userId;

    private TextView tvCurrentBalance, tvMinAmount;
    private RadioGroup rgPaymentMethod;
    private EditText etUpiId, etMobile, etAccountName, etAmount;
    private Button btnSubmitRequest, btnViewHistory;
    private ProgressBar progressBar;

    private long currentBalance = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_withdraw);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("💰 Withdraw");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initFirebase();

        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        loadUserBalance();
    }

    private void initFirebase() {
        database = FirebaseDatabase.getInstance(DB_URL);
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        userId = currentUser != null ? currentUser.getUid() : "";
        usersRef = database.getReference("users");
        withdrawRef = database.getReference("withdrawRequests");
    }

    private void initViews() {
        tvCurrentBalance = findViewById(R.id.tvCurrentBalance);
        tvMinAmount = findViewById(R.id.tvMinAmount);
        rgPaymentMethod = findViewById(R.id.rgPaymentMethod);
        etUpiId = findViewById(R.id.etUpiId);
        etMobile = findViewById(R.id.etMobile);
        etAccountName = findViewById(R.id.etAccountName);
        etAmount = findViewById(R.id.etAmount);
        btnSubmitRequest = findViewById(R.id.btnSubmitRequest);
        btnViewHistory = findViewById(R.id.btnViewHistory);
        progressBar = findViewById(R.id.progressBar);

        tvMinAmount.setText("Minimum: ₹10 (1000 points)");

        rgPaymentMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbUpi) {
                etUpiId.setVisibility(View.VISIBLE);
                etMobile.setVisibility(View.GONE);
            } else {
                etUpiId.setVisibility(View.GONE);
                etMobile.setVisibility(View.VISIBLE);
            }
        });

        btnSubmitRequest.setOnClickListener(v -> validateAndSubmit());
        btnViewHistory.setOnClickListener(v -> showWithdrawHistory());
    }

    private void loadUserBalance() {
        progressBar.setVisibility(View.VISIBLE);

        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);

                long mainWallet = getLong(snapshot.child("mainWallet"));
                long walletWithdrawable = getLong(snapshot.child("wallet").child("withdrawable"));

                currentBalance = walletWithdrawable > 0 ? walletWithdrawable : mainWallet;

                double rs = currentBalance / 100.0;
                tvCurrentBalance.setText("₹" + String.format(Locale.getDefault(), "%.2f", rs) + " (" + currentBalance + " pts)");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(WithdrawActivity.this, "Error loading balance", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void validateAndSubmit() {
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "User not found. Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmitRequest.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        String amountStr = etAmount.getText().toString().trim();
        String accountName = etAccountName.getText().toString().trim();

        if (TextUtils.isEmpty(amountStr)) {
            etAmount.setError("Enter amount in points");
            resetSubmitState();
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            etAmount.setError("Enter valid numeric points");
            resetSubmitState();
            return;
        }

        if (amount < MIN_WITHDRAW) {
            etAmount.setError("Minimum " + MIN_WITHDRAW + " points required");
            resetSubmitState();
            return;
        }

        if (amount > currentBalance) {
            etAmount.setError("Insufficient balance");
            resetSubmitState();
            return;
        }

        if (TextUtils.isEmpty(accountName)) {
            etAccountName.setError("Enter account holder name");
            resetSubmitState();
            return;
        }

        int selectedMethod = rgPaymentMethod.getCheckedRadioButtonId();
        String paymentInfo;
        String paymentType;

        if (selectedMethod == R.id.rbUpi) {
            String upiId = etUpiId.getText().toString().trim();
            if (TextUtils.isEmpty(upiId) || !upiId.contains("@")) {
                etUpiId.setError("Enter valid UPI ID");
                resetSubmitState();
                return;
            }
            paymentInfo = upiId;
            paymentType = "UPI";
        } else {
            String mobile = etMobile.getText().toString().trim();
            if (TextUtils.isEmpty(mobile) || !mobile.matches("\\d{10}")) {
                etMobile.setError("Enter valid 10-digit mobile");
                resetSubmitState();
                return;
            }
            paymentInfo = mobile;
            paymentType = "Mobile";
        }

        checkCooldownAndSubmit(amount, paymentInfo, paymentType, accountName);
    }

    private void checkCooldownAndSubmit(int amount, String paymentInfo, String paymentType, String accountName) {
        usersRef.child(userId).child("lastWithdrawTime")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Long lastWithdraw = snapshot.getValue(Long.class);
                        long now = System.currentTimeMillis();

                        if (lastWithdraw != null) {
                            long diff = now - lastWithdraw;
                            if (diff < COOLDOWN_MILLIS) {
                                long remainingMillis = COOLDOWN_MILLIS - diff;
                                long hoursLeft = Math.max(1, (remainingMillis + (60L * 60L * 1000L) - 1) / (60L * 60L * 1000L));
                                resetSubmitState();
                                Toast.makeText(
                                        WithdrawActivity.this,
                                        "Please wait " + hoursLeft + " hours before next withdrawal",
                                        Toast.LENGTH_LONG
                                ).show();
                                return;
                            }
                        }

                        deductAndCreateRequest(amount, paymentInfo, paymentType, accountName, now);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        resetSubmitState();
                        Toast.makeText(WithdrawActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deductAndCreateRequest(int amount, String paymentInfo, String paymentType, String accountName, long now) {
        btnSubmitRequest.setText("⏳ PROCESSING...");

        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
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

                if (mainWallet < amount || walletWithdrawable < amount || walletTotal < amount) {
                    resetSubmitState();
                    Toast.makeText(WithdrawActivity.this, "Insufficient balance", Toast.LENGTH_SHORT).show();
                    return;
                }

                Map<String, Object> walletUpdates = new HashMap<>();
                walletUpdates.put("mainWallet", mainWallet - amount);
                walletUpdates.put("wallet/withdrawable", walletWithdrawable - amount);
                walletUpdates.put("wallet/bonus", walletBonus);
                walletUpdates.put("wallet/referralBonus", walletReferralBonus);
                walletUpdates.put("wallet/total", walletTotal - amount);
                walletUpdates.put("wallet/updatedAt", System.currentTimeMillis());

                usersRef.child(userId).updateChildren(walletUpdates)
                        .addOnSuccessListener(unused -> createWithdrawRequest(amount, paymentInfo, paymentType, accountName, now))
                        .addOnFailureListener(e -> {
                            resetSubmitState();
                            Toast.makeText(WithdrawActivity.this, "Wallet error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                resetSubmitState();
                Toast.makeText(WithdrawActivity.this, "Wallet error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createWithdrawRequest(int amount, String paymentInfo, String paymentType, String accountName, long now) {
        String requestId = withdrawRef.push().getKey();

        if (requestId == null) {
            refundWallet(amount);
            resetSubmitState();
            Toast.makeText(this, "Failed to create request ID", Toast.LENGTH_SHORT).show();
            loadUserBalance();
            return;
        }

        Map<String, Object> requestData = new HashMap<>();
        requestData.put("userId", userId);
        requestData.put("requestId", requestId);
        requestData.put("amount", amount);
        requestData.put("amountInRs", amount / 100.0);
        requestData.put("paymentType", paymentType);
        requestData.put("upiId", "UPI".equals(paymentType) ? paymentInfo : "");
        requestData.put("mobile", "Mobile".equals(paymentType) ? paymentInfo : "");
        requestData.put("accountName", accountName);
        requestData.put("status", "pending");
        requestData.put("timestamp", now);
        requestData.put("paidAt", 0);
        requestData.put("paidBy", "");
        requestData.put("rejectedReason", "");

        withdrawRef.child(requestId).setValue(requestData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                usersRef.child(userId).child("lastWithdrawTime").setValue(now);

                pushNotification(
                        userId,
                        "Withdraw Submitted",
                        "Your withdraw request has been submitted successfully.",
                        "withdraw_pending"
                );

                usersRef.child(userId).child("totalWithdrawn").runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData data) {
                        Long total = data.getValue(Long.class);
                        data.setValue((total != null ? total : 0L) + amount);
                        return Transaction.success(data);
                    }

                    @Override
                    public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                    }
                });

                resetSubmitState();

                new AlertDialog.Builder(WithdrawActivity.this)
                        .setTitle("✅ Request Submitted!")
                        .setMessage("Your withdrawal request of ₹" + (amount / 100.0) + " has been submitted.\n\nProcessing time: 24-48 hours")
                        .setPositiveButton("OK", (dialog, which) -> {
                            clearForm();
                            loadUserBalance();
                        })
                        .setCancelable(false)
                        .show();
            } else {
                refundWallet(amount);
                resetSubmitState();
                Toast.makeText(
                        WithdrawActivity.this,
                        "Withdraw request save failed. Please check Firebase rules. Amount refunded.",
                        Toast.LENGTH_LONG
                ).show();
                loadUserBalance();
            }
        });
    }

    private void refundWallet(int amount) {
        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
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
                updates.put("mainWallet", mainWallet + amount);
                updates.put("wallet/withdrawable", walletWithdrawable + amount);
                updates.put("wallet/bonus", walletBonus);
                updates.put("wallet/referralBonus", walletReferralBonus);
                updates.put("wallet/total", walletTotal + amount);
                updates.put("wallet/updatedAt", System.currentTimeMillis());

                usersRef.child(userId).updateChildren(updates);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void resetSubmitState() {
        progressBar.setVisibility(View.GONE);
        btnSubmitRequest.setEnabled(true);
        btnSubmitRequest.setText("💸 SUBMIT WITHDRAW REQUEST");
    }

    private void clearForm() {
        etAmount.setText("");
        etUpiId.setText("");
        etMobile.setText("");
        etAccountName.setText("");
    }

    private void pushNotification(String userId, String title, String message, String type) {
        DatabaseReference notifRef = usersRef.child(userId).child("notifications").push();
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("message", message);
        data.put("type", type);
        data.put("isRead", false);
        data.put("createdAt", System.currentTimeMillis());
        notifRef.setValue(data);
    }

    private void showWithdrawHistory() {
        progressBar.setVisibility(View.VISIBLE);

        withdrawRef.orderByChild("userId").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressBar.setVisibility(View.GONE);

                        if (!snapshot.exists()) {
                            Toast.makeText(WithdrawActivity.this, "No withdrawal history", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        List<DataSnapshot> requests = new ArrayList<>();
                        for (DataSnapshot request : snapshot.getChildren()) {
                            requests.add(request);
                        }

                        Collections.sort(requests, (a, b) -> {
                            Long ta = a.child("timestamp").getValue(Long.class);
                            Long tb = b.child("timestamp").getValue(Long.class);
                            long va = ta != null ? ta : 0L;
                            long vb = tb != null ? tb : 0L;
                            return Long.compare(vb, va);
                        });

                        StringBuilder history = new StringBuilder();
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

                        for (DataSnapshot request : requests) {
                            Long timestamp = request.child("timestamp").getValue(Long.class);
                            Double amountRs = request.child("amountInRs").getValue(Double.class);
                            String status = request.child("status").getValue(String.class);
                            String paymentType = request.child("paymentType").getValue(String.class);
                            String paymentInfo = "UPI".equals(paymentType)
                                    ? request.child("upiId").getValue(String.class)
                                    : request.child("mobile").getValue(String.class);

                            String statusIcon = "pending".equals(status) ? "⏳" :
                                    "paid".equals(status) ? "✅" : "❌";

                            history.append(statusIcon).append(" ₹").append(amountRs != null ? amountRs : 0).append("\n");
                            history.append(timestamp != null ? sdf.format(new Date(timestamp)) : "N/A").append("\n");
                            history.append(paymentType != null ? paymentType : "Unknown").append(": ")
                                    .append(paymentInfo != null ? paymentInfo : "-").append("\n");
                            history.append("Status: ").append(status != null ? status.toUpperCase(Locale.getDefault()) : "UNKNOWN").append("\n");
                            history.append("━━━━━━━━━━━━━━━\n");
                        }

                        new AlertDialog.Builder(WithdrawActivity.this)
                                .setTitle("📜 Withdrawal History")
                                .setMessage(history.toString())
                                .setPositiveButton("Close", null)
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(WithdrawActivity.this, "Error loading history", Toast.LENGTH_SHORT).show();
                    }
                });
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
}
