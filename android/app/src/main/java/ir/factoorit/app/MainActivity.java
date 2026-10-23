package ir.factoorit.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.getcapacitor.BridgeActivity;

import ir.myket.billingplugin.IabHelper;
import ir.myket.billingplugin.IabResult;
import ir.myket.billingplugin.Purchase;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "FactooritBilling";
    private static final int RC_REQUEST = 10001;

    private IabHelper mHelper;
    private int pendingDays = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // کلید عمومی مایکت که در پنل مایکت بهت داده شده
        String base64PublicKey = BuildConfig.IAB_PUBLIC_KEY;

        mHelper = new IabHelper(this, base64PublicKey);
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    Log.e(TAG, "Problem setting up in-app billing: " + result);
                    return;
                }
                Log.d(TAG, "Myket billing setup successful.");
            }
        });

        // اتصال پل جاوااسکریپت به فرانت‌اند
        try {
            WebView webView = getBridge().getWebView();
            webView.addJavascriptInterface(new AndroidBridgeInterface(), "AndroidBridge");
            Log.d(TAG, "AndroidBridge attached to WebView");
        } catch (Exception e) {
            Log.e(TAG, "Error attaching AndroidBridge", e);
        }
    }

    public class AndroidBridgeInterface {

        @JavascriptInterface
        public void buySubscription(String planId, int days, int price) {
            Log.d(TAG, "buySubscription: plan=" + planId + ", days=" + days);
            pendingDays = days;

            String sku = "monthly";
            if (days >= 360 || "yearly".equalsIgnoreCase(planId)) {
                sku = "yearly";
            } else if (days >= 180 || "sixMonths".equalsIgnoreCase(planId)) {
                sku = "sixMonths";
            } else {
                sku = "monthly";
            }

            final String targetSku = sku;

            runOnUiThread(() -> {
                if (mHelper == null) {
                    Toast.makeText(MainActivity.this, "سیستم پرداخت در دسترس نیست.", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    // شروع عملیات خرید با هندلر رسمی مایکت
                    mHelper.launchPurchaseFlow(MainActivity.this, targetSku, RC_REQUEST, mPurchaseFinishedListener, "");
                } catch (Exception e) {
                    Log.e(TAG, "Error launching purchase flow", e);
                    Toast.makeText(MainActivity.this, "خطا در شروع فرایند خرید: لطفاً مایکت را چک کنید.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // شنونده نتیجه خرید از مایکت
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            if (result.isFailure()) {
                Log.w(TAG, "Error purchasing: " + result);
                Toast.makeText(MainActivity.this, "خرید انجام نشد یا لغو شد.", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Purchase successful: " + purchase.getSku());
            Toast.makeText(MainActivity.this, "پرداخت با موفقیت انجام شد!", Toast.LENGTH_SHORT).show();

            // مصرف کردن محصول (Consume) اگر محصول مصرفی/تکرارپذیر است
            try {
                mHelper.consumeAsync(purchase, (p, consumeResult) -> {
                    Log.d(TAG, "Purchase consumed");
                });
            } catch (Exception e) {
                Log.e(TAG, "Consume error", e);
            }

            // اطلاع دادن به فرانت‌اند
            runOnUiThread(() -> {
                try {
                    WebView webView = getBridge().getWebView();
                    if (webView != null) {
                        webView.evaluateJavascript("if(window.onPaymentSuccess){ window.onPaymentSuccess(" + pendingDays + "); }", null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error running JS callback", e);
                }
            });
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // انتقال پاسخ خرید به هلپر مایکت
        if (mHelper != null && mHelper.handleActivityResult(requestCode, resultCode, data)) {
            Log.d(TAG, "onActivityResult handled by IabHelper.");
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHelper != null) {
            try {
                mHelper.dispose();
            } catch (Exception ignored) {}
            mHelper = null;
        }
    }
}
