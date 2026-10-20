package ir.factoorit.app;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;

import ir.cafebazaar.poolakey.Payment;
import ir.cafebazaar.poolakey.config.PaymentConfiguration;
import ir.cafebazaar.poolakey.config.SecurityCheck;
import ir.cafebazaar.poolakey.request.PurchaseRequest;
import kotlin.Unit;

public class MainActivity extends BridgeActivity {

    private Payment payment;

    // کلید عمومی بازار شما
    private static final String RSA_KEY = "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwDIWJ0LK7YPk+qsOEHdpZOgbW6pnzufPOjoGN5LPDgi3a20rmwVFlhgF9inW/z69tmVYvJpArNpxHSBva9qLLG2q36jqAKvklas+3iIVIwXG0C0IpiWg4IwgZpmsqmhyE4SEU/KIkfiJZ02l/nDcds7CxdepqUtSjm3penEEVli1L22pJm7pHh/GqnYyyWVhbgcY3d0dw34F+NcAWVsRHRLooiOYhUGx2QLiM9Q0G0CAwEAAQ==";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SecurityCheck securityCheck = (RSA_KEY == null || RSA_KEY.isEmpty()) 
                ? SecurityCheck.Disable.INSTANCE 
                : new SecurityCheck.Enable(RSA_KEY);

        PaymentConfiguration paymentConfig = new PaymentConfiguration(securityCheck);
        payment = new Payment(this, paymentConfig);

        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        }
    }

    public class WebAppInterface {

        @JavascriptInterface
        public void buySubscription(final String planId, final int days, final long price) {
            runOnUiThread(() -> {
                // پیام تست برای اینکه مطمئن شویم کلیک از وب به جاوا رسیده است
                Toast.makeText(MainActivity.this, "تلاش برای خرید پلن: " + planId + " (" + days + " روزه)", Toast.LENGTH_SHORT).show();

                final String sku;
                if (days == 30) {
                    sku = "monthly";
                } else if (days == 180) {
                    sku = "sixMonths";
                } else if (days == 365) {
                    sku = "yearly";
                } else {
                    sku = "monthly";
                }

                PurchaseRequest request = new PurchaseRequest(
                        sku,
                        null,
                        null
                );

                payment.subscribeProduct(
                        getActivityResultRegistry(),
                        request,
                        callback -> {
                            callback.purchaseSucceed(purchaseEntity -> {
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "پرداخت با موفقیت انجام شد", Toast.LENGTH_SHORT).show();
                                    if (getBridge() != null && getBridge().getWebView() != null) {
                                        getBridge().getWebView().evaluateJavascript(
                                                "if(window.onPaymentSuccess){ window.onPaymentSuccess(" + days + "); }",
                                                null
                                        );
                                    }
                                });
                                return Unit.INSTANCE;
                            });

                            callback.purchaseFailed(throwable -> {
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "پرداخت انجام نشد یا لغو شد", Toast.LENGTH_SHORT).show();
                                });
                                return Unit.INSTANCE;
                            });

                            return Unit.INSTANCE;
                        }
                );
            });
        }
    }
}
