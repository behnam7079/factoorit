package ir.factoorit.app;

import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;

import org.json.JSONObject;

import ir.cafebazaar.poolakey.Payment;
import ir.cafebazaar.poolakey.config.PaymentConfiguration;
import ir.cafebazaar.poolakey.config.SecurityCheck;
import ir.cafebazaar.poolakey.entity.PurchaseInfo;
import ir.cafebazaar.poolakey.request.PurchaseRequest;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "FactooritBilling";

    private Payment payment;
    private int pendingDays = 0;
    private boolean poolakeyConnected = false;

    // اگر پاسخ بازار قبل از آماده‌شدن JavaScript برسد، اینجا نگه‌داری می‌شود.
    private PurchaseInfo pendingRestoredPurchase;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        attachAndroidBridge();

        PaymentConfiguration paymentConfig = new PaymentConfiguration(
                new SecurityCheck.Enable(BuildConfig.IAB_PUBLIC_KEY),
                true
        );

        payment = new Payment(this, paymentConfig);

        payment.connect(connection -> {
            connection.connectionSucceed(() -> {
                Log.d(TAG, "Poolakey connected successfully");
                poolakeyConnected = true;

                // استعلام اولیه؛ اگر فرانت آماده نباشد، نتیجه نگه‌داری می‌شود.
                checkSubscriptions();
                return null;
            });

            connection.connectionFailed(throwable -> {
                poolakeyConnected = false;
                Log.e(TAG, "Poolakey connection failed", throwable);
                return null;
            });

            return null;
        });
    }

    private void attachAndroidBridge() {
        try {
            WebView webView = getBridge().getWebView();

            if (webView == null) {
                Log.e(TAG, "WebView is null; AndroidBridge was not attached");
                return;
            }

            webView.addJavascriptInterface(
                    new AndroidBridgeInterface(),
                    "AndroidBridge"
            );

            Log.d(TAG, "AndroidBridge attached successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error attaching AndroidBridge", e);
        }
    }

    private void checkSubscriptions() {
        if (payment == null) {
            Log.w(TAG, "Cannot query subscriptions: payment is null");
            return;
        }

        if (!poolakeyConnected) {
            Log.w(TAG, "Cannot query subscriptions: Poolakey is not connected");
            return;
        }

        payment.getSubscribedProducts(queryResult -> {
            queryResult.querySucceed(purchases -> {
                int count = purchases != null ? purchases.size() : 0;

                Log.d(
                        TAG,
                        "Subscribed products queried successfully. Count: " + count
                );

                if (purchases == null || purchases.isEmpty()) {
                    pendingRestoredPurchase = null;
                    Log.d(TAG, "No active subscription found");
                    return null;
                }

                // براساس زمان خرید انتخاب می‌کنیم، نه ترتیب لیست بازار.
                PurchaseInfo latestPurchase = purchases.get(0);

                for (PurchaseInfo purchase : purchases) {
                    if (purchase.getPurchaseTime()
                            > latestPurchase.getPurchaseTime()) {
                        latestPurchase = purchase;
                    }
                }

                pendingRestoredPurchase = latestPurchase;

                Log.d(
                        TAG,
                        "Active subscription found: "
                                + latestPurchase.getProductId()
                                + " at "
                                + latestPurchase.getPurchaseTime()
                );

                sendRestoredPurchaseToJs(latestPurchase);
                return null;
            });

            queryResult.queryFailed(throwable -> {
                Log.e(TAG, "Query subscribed products failed", throwable);
                return null;
            });

            return null;
        });
    }

    private void sendRestoredPurchaseToJs(PurchaseInfo purchase) {
        if (purchase == null) {
            return;
        }

        String productId = purchase.getProductId();
        String token = purchase.getPurchaseToken();
        long purchaseTime = purchase.getPurchaseTime();

        String jsCode =
                "(function() {"
                        + "if (typeof window.onSubscriptionRestored === 'function') {"
                        + "window.onSubscriptionRestored("
                        + JSONObject.quote(productId)
                        + ","
                        + purchaseTime
                        + ","
                        + JSONObject.quote(token)
                        + ");"
                        + "return 'delivered';"
                        + "}"
                        + "return 'not_ready';"
                        + "})();";

        runOnUiThread(() -> {
            try {
                WebView webView = getBridge().getWebView();

                if (webView == null) {
                    Log.w(TAG, "Cannot restore subscription: WebView is null");
                    return;
                }

                webView.evaluateJavascript(jsCode, result -> {
                    Log.d(TAG, "Restore callback result: " + result);

                    if ("\"delivered\"".equals(result)) {
                        pendingRestoredPurchase = null;
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error sending restored subscription to JS", e);
            }
        });
    }

    public class AndroidBridgeInterface {

        @JavascriptInterface
        public void checkPurchases() {
            runOnUiThread(() -> {
                if (pendingRestoredPurchase != null) {
                    sendRestoredPurchaseToJs(pendingRestoredPurchase);
                }

                checkSubscriptions();
            });
        }

        @JavascriptInterface
        public void buySubscription(String planId, int days, int price) {
            pendingDays = days;

            final String targetSku;

            if (days >= 360 || "yearly".equalsIgnoreCase(planId)) {
                targetSku = "yearly";
            } else if (days >= 180 || "sixMonths".equalsIgnoreCase(planId)) {
                targetSku = "sixMonths";
            } else {
                targetSku = "monthly";
            }

            runOnUiThread(() -> {
                if (payment == null || !poolakeyConnected) {
                    Toast.makeText(
                            MainActivity.this,
                            "ارتباط با کافه‌بازار برقرار نیست.",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                PurchaseRequest request =
                        new PurchaseRequest(targetSku, "", null);

                payment.subscribeProduct(
                        getActivityResultRegistry(),
                        request,
                        result -> {
                            result.purchaseFlowBegan(() -> {
                                Log.d(TAG, "purchaseFlowBegan");
                                return null;
                            });

                            result.purchaseSucceed(purchaseInfo -> {
                                Log.d(
                                        TAG,
                                        "purchaseSucceed orderId="
                                                + purchaseInfo.getOrderId()
                                );

                                Toast.makeText(
                                        MainActivity.this,
                                        "پرداخت با موفقیت انجام شد!",
                                        Toast.LENGTH_SHORT
                                ).show();

                                sendPaymentSuccessToJs(
                                        pendingDays,
                                        purchaseInfo,
                                        targetSku
                                );

                                return null;
                            });

                            result.purchaseCanceled(() -> {
                                Toast.makeText(
                                        MainActivity.this,
                                        "پرداخت لغو شد.",
                                        Toast.LENGTH_SHORT
                                ).show();
                                return null;
                            });

                            result.purchaseFailed(throwable -> {
                                Log.e(TAG, "purchaseFailed", throwable);

                                String errorMessage =
                                        throwable != null
                                                && throwable.getMessage() != null
                                                ? throwable.getMessage()
                                                : "خطای نامشخص";

                                Toast.makeText(
                                        MainActivity.this,
                                        "خطا در خرید: " + errorMessage,
                                        Toast.LENGTH_LONG
                                ).show();

                                return null;
                            });

                            return null;
                        }
                );
            });
        }
    }

    private void sendPaymentSuccessToJs(
            int days,
            PurchaseInfo purchaseInfo,
            String sku
    ) {
        String token = purchaseInfo.getPurchaseToken();
        long purchaseTime = purchaseInfo.getPurchaseTime();

        String jsCode =
                "if (typeof window.onPaymentSuccess === 'function') {"
                        + "window.onPaymentSuccess("
                        + days
                        + ","
                        + JSONObject.quote(token)
                        + ","
                        + JSONObject.quote(sku)
                        + ","
                        + purchaseTime
                        + ");"
                        + "}";

        try {
            WebView webView = getBridge().getWebView();

            if (webView != null) {
                webView.evaluateJavascript(jsCode, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "JS payment callback error", e);
        }
    }

    @Override
    public void onDestroy() {
        poolakeyConnected = false;
        // روش disconnect حذف شد زیرا در Poolakey وجود ندارد
        super.onDestroy();
    }
}
