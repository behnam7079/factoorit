package ir.factoorit.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

import org.json.JSONException;

// ایمپورت‌های کتابخانه پرداخت مایکت
import ir.factoorit.app.util.IabHelper;
import ir.factoorit.app.util.IabResult;
import ir.factoorit.app.util.Inventory;
import ir.factoorit.app.util.Purchase;

public class MainActivity extends BridgeActivity {
    static final String TAG = "FactooritBilling";
    IabHelper mHelper;
    
    // شناسه محصول یا اشتراک شما در پنل مایکت
    static final String SKU_SUBSCRIPTION = "factoorit_sub_1"; 
    static final int RC_REQUEST = 10001;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // استفاده مستقیم از کلید عمومی واقعی شما که از پنل مایکت دریافت شد
        String base64PublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4GNACBiQGQC1PBVkAi1xa7PdL1urzPi/2t7MXDtscC5XFnyWV/VZ+EFZdMH7YjScAzAn04864tmR+8Pv77OVh1mSZ7s+iNRv8WzwJiA41UzUB9UuP3b5GXBhljfd0yXjYJ7y8VM1K6h2jK5rTuOtYV9tpcc4WZ11TI1A3izhEYDr02DejDCwlDAQAB";

        Log.d(TAG, "Initializing IAB helper...");
        mHelper = new IabHelper(this, base64PublicKey);

        // راه‌اندازی اتصال به مایکت
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    Log.e(TAG, "Problem setting up In-app Billing: " + result);
                    return;
                }
                Log.d(TAG, "In-app Billing set up OK.");
            }
        });
    }

    // متد برای شروع فرآیند خرید که از جاوااسکریپت یا دکمه‌ها قابل صدا زدن است
    public void startMyketPurchase() {
        if (mHelper != null) {
            try {
                mHelper.launchPurchaseFlow(this, SKU_SUBSCRIPTION, RC_REQUEST, 
                    new IabHelper.OnIabPurchaseFinishedListener() {
                        @Override
                        public void onIabPurchaseFinished(IabResult result, Purchase info) {
                            if (result.isFailure()) {
                                Log.e(TAG, "Error purchasing: " + result);
                                return;
                            }
                            Log.d(TAG, "Purchase successful!");
                            // اینجا می‌توانید وضعیت خرید را در اپلیکیشن ثبت کنید
                        }
                    }, "my-developer-payload");
            } catch (Exception e) {
                Log.e(TAG, "Exception launching purchase flow", e);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mHelper == null) super.onActivityResult(requestCode, resultCode, data);

        // انتقال نتیجه خرید به IabHelper
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        } else {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHelper != null) {
            mHelper.disposeWhenFinished();
            mHelper = null;
        }
    }
}
