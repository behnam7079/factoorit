package ir.factoorit.app.util;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an in-app product's listing details.
 */
public class SkuDetails {
    String mItemType;
    String mSku;
    String mType;
    String mPrice;
    long mPriceAmountMicros;
    String mPriceCurrencyCode;
    String mTitle;
    String mDescription;
    String mJson;

    public SkuDetails(String jsonSkuDetails) throws JSONException {
        this(IabHelper.ITEM_TYPE_INAPP, jsonSkuDetails);
    }

    public SkuDetails(String itemType, String jsonSkuDetails) throws JSONException {
        mItemType = itemType;
        mJson = jsonSkuDetails;
        JSONObject o = new JSONObject(mJson);
        mSku = o.optString("productId");
        mType = o.optString("type");
        mPrice = o.optString("price");
        if (o.has("price_amount_micros")) {
            mPriceAmountMicros = o.optLong("price_amount_micros");
        }
        mPriceCurrencyCode = o.optString("price_currency_code");
        mTitle = o.optString("title");
        mDescription = o.optString("description");
    }

    public String getSku() { return mSku; }
    public String getType() { return mType; }
    public String getPrice() { return mPrice; }
    public long getPriceAmountMicros() { return mPriceAmountMicros; }
    public String getPriceCurrencyCode() { return mPriceCurrencyCode; }
    public String getTitle() { return mTitle; }
    public String getDescription() { return mDescription; }

    @Override
    public String toString() {
        return "SkuDetails:" + mJson;
    }
}
