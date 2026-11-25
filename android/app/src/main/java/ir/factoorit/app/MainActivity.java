package ir.factoorit.app;

import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;

import ir.cafebazaar.poolakey.Payment;
import ir.cafebazaar.poolakey.config.PaymentConfiguration;
import ir.cafebazaar.poolakey.config.SecurityCheck;
import ir.cafebazaar.poolakey.entity.PurchaseInfo;
import ir.cafebazaar.poolakey.request.PurchaseRequest;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "FactooritBilling";

    private Payment payment;
    private int pendingDays = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // تنظیمات اتصال به کافه‌بازار
        PaymentConfiguration paymentConfig = new PaymentConfiguration(
                new SecurityCheck.Enable(BuildConfig.IAB_PUBLIC_KEY),
                true
        );
        
        payment = new Payment(this, paymentConfig);

        payment.connect(connection -> {
            connection.connectionSucceed(() -> {
                Log.d(TAG, "Poolakey connected successfully");
                // استعلام خودکار اشتراک‌های فعال به محض اتصال موفق
                checkSubscriptions();
                return null;
            });
            connection.connectionFailed(throwable -> {
                Log.e(TAG, "Poolakey connection failed", throwable);
                return null;
            });
            return null;
        });

        try {
            WebView webView = getBridge().getWebView();
            webView.addJavascriptInterface(new AndroidBridgeInterface(), "AndroidBridge");
            Log.d(TAG, "AndroidBridge attached successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error attaching AndroidBridge", e);
        }
    }

    // متد دریافت و بررسی اشتراک‌های فعال کاربر از کافه‌بازار
    private void checkSubscriptions() {
        if (payment == null) return;

        payment.getSubscribedProducts(queryResult -> {
            queryResult.querySucceed(purchases -> {
                Log.d(TAG, "Subscribed products queried successfully. Count: " + (purchases != null ? purchases.size() : 0));
                if (purchases != null && !purchases.isEmpty()) {
                    // آخرین اشتراک فعال را استخراج می‌کنیم
                    PurchaseInfo latestPurchase = purchases.get(purchases.size() - 1);
                    String productId = latestPurchase.getProductId();
                    long purchaseTime = latestPurchase.getPurchaseTime();
                    String token = latestPurchase.getPurchaseToken();

                    Log.d(TAG, "Active subscription found: " + productId + " at " + purchaseTime);

                    runOnUiThread(() -> {
                        try {
                            WebView wv = getBridge().getWebView();
                            if (wv != null) {
                                String jsCode = String.format(
                                    "if(window.onSubscriptionRestored){ window.onSubscriptionRestored('%s', %d, '%s'); }",
                                    productId, purchaseTime, token
                                );
                                wv.evaluateJavascript(jsCode, null);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error sending restored subscription to JS", e);
                        }
                    });
                }
                return null;
            });
            queryResult.queryFailed(throwable -> {
                Log.e(TAG, "Query subscribed products failed", throwable);
                return null;
            });
            return null;
        });
    }

    public class AndroidBridgeInterface {

        // متدی برای اینکه فرانت‌اند هم بتواند دستی یا هنگام لود کامل صفحه استعلام بگیرد
        @JavascriptInterface
        public void checkPurchases() {
            runOnUiThread(() -> checkSubscriptions());
        }

        @JavascriptInterface
        public void buySubscription(String planId, int days, int price) {
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
                PurchaseRequest request = new PurchaseRequest(targetSku, "", null);
                
                payment.subscribeProduct(
                    getActivityResultRegistry(),
                    request,
                    result -> {
                        result.purchaseFlowBegan(() -> {
                            Log.d(TAG, "purchaseFlowBegan");
                            return null;
                        });
                        result.purchaseSucceed(purchaseInfo -> {
                            Log.d(TAG, "purchaseSucceed orderId=" + purchaseInfo.getOrderId());
                            Toast.makeText(MainActivity.this, "پرداخت با موفقیت انجام شد!", Toast.LENGTH_SHORT).show();

                            try {
                                WebView wv = getBridge().getWebView();
                                if (wv != null) {
                                    String token = purchaseInfo.getPurchaseToken();
                                    long purchaseTime = purchaseInfo.getPurchaseTime();
                                    String jsCode = "if(window.onPaymentSuccess){ window.onPaymentSuccess(" + pendingDays + ", '" + token + "', '" + targetSku + "', " + purchaseTime + "); }";
                                    wv.evaluateJavascript(jsCode, null);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "JS callback error", e);
                            }
                            return null;
                        });
                        result.purchaseCanceled(() -> {
                            Toast.makeText(MainActivity.this, "پرداخت لغو شد.", Toast.LENGTH_SHORT).show();
                            return null;
                        });
                        result.purchaseFailed(throwable -> {
                            Log.e(TAG, "purchaseFailed", throwable);
                            String errorMsg = throwable != null ? throwable.getMessage() : "خطای نامشخص";
                            Toast.makeText(MainActivity.this, "خطا در خرید: " + errorMsg, Toast.LENGTH_LONG).show();
                            return null;
                        });
                        return null;
                    }
                );
            });
        }
    }
}
