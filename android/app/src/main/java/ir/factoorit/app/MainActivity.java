package ir.factoorit.app;

import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;

import java.util.ArrayList;
import java.util.List;

import ir.myket.billingclient.IabHelper;
import ir.myket.billingclient.util.Inventory;
import ir.myket.billingclient.util.Purchase;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "FactooritBilling";

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

        mHelper.startSetup(result -> {
            if (!result.isSuccess()) {
                Log.e(TAG, "Problem setting up in-app billing: " + result);
                return;
            }

            Log.d(TAG, "Myket billing setup successful.");
            checkSubscriptions();
        });
    }

    // استعلام خریدهای قبلی از مایکت برای بازیابی هنگام نصب مجدد
    private void checkSubscriptions() {
        if (mHelper == null) {
            return;
        }

        List<String> skuList = new ArrayList<>();
        skuList.add("monthly");
        skuList.add("sixMonths");
        skuList.add("yearly");

        try {
            mHelper.queryInventoryAsync(true, skuList, (result, inventory) -> {
                if (result.isFailure() || inventory == null) {
                    Log.e(TAG, "Failed to query inventory: " + result);
                    return;
                }

                Log.d(TAG, "Inventory query successful");

                // بررسی پلن‌های خریداری‌شده
                for (String sku : skuList) {
                    Purchase purchase = inventory.getPurchase(sku);
                    if (purchase == null) {
                        continue;
                    }

                    String productId = purchase.getSku();
                    long purchaseTime = purchase.getPurchaseTime();
                    String token = purchase.getToken();

                    Log.d(
                            TAG,
                            "Active Myket subscription found: "
                                    + productId
                                    + " at "
                                    + purchaseTime
                    );

                    runOnUiThread(() -> {
                        try {
                            WebView webView = getBridge().getWebView();
                            if (webView != null) {
                                String jsCode = String.format(
                                        "if(window.onRestoreSubscription){"
                                                + "window.onRestoreSubscription('%s', %d, '%s');"
                                                + "}",
                                        productId,
                                        purchaseTime,
                                        token
                                );
                                webView.evaluateJavascript(jsCode, null);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error sending restored subscription to JS", e);
                        }
                    });

                    break;
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in queryInventoryAsync", e);
        }
    }

    public class AndroidBridgeInterface {
        @JavascriptInterface
        public void checkPurchases() {
            runOnUiThread(() -> checkSubscriptions());
        }

        @JavascriptInterface
        public void buySubscription(String planId, int days, int price) {
            Log.d(TAG, "buySubscription called: plan=" + planId + ", days=" + days);
            pendingDays = days;

            String sku;
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
                    Toast.makeText(
                            MainActivity.this,
                            "سرویس پرداخت مایکت آماده نیست.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                try {
                    mHelper.launchPurchaseFlow(
                            MainActivity.this,
                            targetSku,
                            mPurchaseFinishedListener,
                            ""
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Error launching purchase flow", e);
                    Toast.makeText(
                            MainActivity.this,
                            "خطا در شروع فرایند خرید",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }
    }

    private final IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener =
            (result, purchase) -> {
                Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

                if (mHelper == null) {
                    return;
                }

                if (result.isFailure()) {
                    Log.e(TAG, "Error purchasing: " + result);
                    Toast.makeText(
                            MainActivity.this,
                            "پرداخت لغو شد یا با خطا مواجه شد",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                Log.d(
                        TAG,
                        "Purchase successful: "
                                + (purchase != null ? purchase.getSku() : "unknown")
                );

                Toast.makeText(
                        MainActivity.this,
                        "پرداخت با موفقیت انجام شد",
                        Toast.LENGTH_SHORT
                ).show();

                runOnUiThread(() -> {
                    try {
                        WebView webView = getBridge().getWebView();

                        if (webView != null && purchase != null) {
                            String productId = purchase.getSku();
                            long purchaseTime = purchase.getPurchaseTime();
                            String token = purchase.getToken();

                            String jsCode = String.format(
                                    "if(window.onPaymentSuccess){"
                                            + "window.onPaymentSuccess('%s', %d, '%s');"
                                            + "}",
                                    productId,
                                    purchaseTime,
                                    token
                            );
                            webView.evaluateJavascript(jsCode, null);
                        } else if (webView != null) {
                            String jsCode =
                                    "if(window.onPaymentSuccess){"
                                            + "window.onPaymentSuccess("
                                            + pendingDays
                                            + ");"
                                            + "}";
                            webView.evaluateJavascript(jsCode, null);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "JS callback error", e);
                    }
                });
            };

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying helper.");

        if (mHelper != null) {
            try {
                mHelper.dispose();
            } catch (Exception ignored) {
                // نادیده گرفتن خطا هنگام آزادسازی
            }
            mHelper = null;
        }
    }
}
