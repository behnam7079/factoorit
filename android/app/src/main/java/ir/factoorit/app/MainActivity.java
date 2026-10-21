package ir.factoorit.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "FactooritBilling";
    private static final int RC_MYKET_PAYMENT = 1001;

    // متغیری برای نگه‌داری تعداد روزهای اشتراک خریداری شده
    private int pendingDays = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // اضافه کردن پل ارتباطی جاوااسکریپت به WebView خازنی (Capacitor)
        try {
            WebView webView = getBridge().getWebView();
            webView.addJavascriptInterface(new AndroidBridgeInterface(), "AndroidBridge");
            Log.d(TAG, "AndroidBridge successfully attached to WebView");
        } catch (Exception e) {
            Log.e(TAG, "Error attaching AndroidBridge", e);
        }
    }

    // این کلاسی است که فرانت‌اند مستقیماً به آن دسترسی دارد
    public class AndroidBridgeInterface {

        @JavascriptInterface
        public void buySubscription(String planId, int days, int price) {
            Log.d(TAG, "buySubscription called: plan=" + planId + ", days=" + days);
            pendingDays = days;

            // تبدیل شناسه فرانت‌اند به شناسه محصول مایکت
            String sku = "monthly";
            if (days >= 360 || "yearly".equalsIgnoreCase(planId)) {
                sku = "yearly";
            } else if (days >= 180 || "sixMonths".equalsIgnoreCase(planId)) {
                sku = "sixMonths";
            } else {
                sku = "monthly";
            }

            final String targetSku = sku;

            // اجرای روی ترد اصلی UI
            runOnUiThread(() -> {
                try {
                    // ارسال اینتنت خرید مستقیم به مایکت
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("myket://inapp_payment?sku=" + targetSku + "&package_name=" + getPackageName()));
                    intent.setPackage("ir.mservices.market");
                    startActivityForResult(intent, RC_MYKET_PAYMENT);
                } catch (Exception e) {
                    Log.e(TAG, "Myket app not found or intent failed", e);
                    Toast.makeText(MainActivity.this, "لطفاً برنامه مایکت را نصب یا به‌روزرسانی کنید.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_MYKET_PAYMENT) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Payment was successful!");
                Toast.makeText(this, "پرداخت با موفقیت انجام شد!", Toast.LENGTH_SHORT).show();

                // صدا زدن تابع موفقیت در فرانت‌اند
                runOnUiThread(() -> {
                    try {
                        WebView webView = getBridge().getWebView();
                        if (webView != null) {
                            webView.evaluateJavascript("if(window.onPaymentSuccess){ window.onPaymentSuccess(" + pendingDays + "); }", null);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error executing JS callback", e);
                    }
                });
            } else {
                Log.w(TAG, "Payment canceled or failed with resultCode: " + resultCode);
                Toast.makeText(this, "پرداخت لغو شد یا انجام نشد.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
