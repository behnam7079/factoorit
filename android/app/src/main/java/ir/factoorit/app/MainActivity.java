package ir.factoorit.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.getcapacitor.BridgeActivity;

import ir.myket.billingclient.util.IabHelper;
import ir.myket.billingclient.util.IabResult;
import ir.myket.billingclient.util.Purchase;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "FactooritBilling";
    private static final int RC_REQUEST = 10001;

    private IabHelper mHelper;
    private int pendingDays = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // راه‌اندازی پل ارتباطی با وب‌ویو فرانت‌اند
        try {
            WebView webView = getBridge().getWebView();
            webView.addJavascriptInterface(new AndroidBridgeInterface(), "AndroidBridge");
            Log.d(TAG, "AndroidBridge attached successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error attaching AndroidBridge", e);
        }

        // مقداردهی اولیه مایکت با کلید عمومی (IAB_PUBLIC_KEY)
        String base64EncodedPublicKey = BuildConfig.IAB_PUBLIC_KEY;
        mHelper = new IabHelper(this, base64EncodedPublicKey);
        mHelper.enableDebugLogging(true, TAG);

        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    Log.e(TAG, "Problem setting up in-app billing: " + result);
                    return;
                }
                Log.d(TAG, "Myket billing setup successful.");
            }
        });
    }

    public class AndroidBridgeInterface {
        @JavascriptInterface
        public void buySubscription(String planId, int days, int price) {
            Log.d(TAG, "buySubscription called: plan=" + planId + ", days=" + days);
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
                    Toast.makeText(MainActivity.this, "سرویس پرداخت مایکت آماده نیست.", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    // طبق داکیومنت مایکت: شروع فرایند خرید از طریق launchPurchaseFlow
                    mHelper.launchPurchaseFlow(
                            MainActivity.this,
                            targetSku,
                            RC_REQUEST,
                            mPurchaseFinishedListener,
                            ""
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Error launching purchase flow", e);
                    Toast.makeText(MainActivity.this, "خطا در شروع فرایند خرید", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // نتیجه خرید از مایکت
    private final IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        @Override
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

            if (mHelper == null) return;

            if (result.isFailure()) {
                Log.e(TAG, "Error purchasing: " + result);
                Toast.makeText(MainActivity.this, "پرداخت لغو شد یا با خطا مواجه شد", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Purchase successful: " + (purchase != null ? purchase.getSku() : "unknown"));
            Toast.makeText(MainActivity.this, "پرداخت با موفقیت انجام شد", Toast.LENGTH_SHORT).show();

            // خبر دادن به وب‌ویو جهت اعمال روزهای خریداری شده
            runOnUiThread(() -> {
                try {
                    WebView wv = getBridge().getWebView();
                    if (wv != null && purchase != null) {
                        String jsCode = "if(window.onPaymentSuccess){ window.onPaymentSuccess(" + pendingDays + "); }";
                        wv.evaluateJavascript(jsCode, null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "JS callback error", e);
                }
            });
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (mHelper != null && mHelper.handleActivityResult(requestCode, resultCode, data)) {
            Log.d(TAG, "onActivityResult handled by IabHelper.");
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying helper.");
        if (mHelper != null) {
            mHelper.disposeWhenFinished();
            mHelper = null;
        }
    }
}
