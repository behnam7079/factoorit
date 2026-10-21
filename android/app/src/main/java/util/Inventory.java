package ir.factoorit.app.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a block of information about in-app items.
 */
public class Inventory {
    Map<String, SkuDetails> mSkuMap = new HashMap<String, SkuDetails>();
    Map<String, Purchase> mPurchaseMap = new HashMap<String, Purchase>();

    public Inventory() { }

    public Purchase getPurchase(String sku) {
        return mPurchaseMap.get(sku);
    }

    public boolean hasPurchase(String sku) {
        return mPurchaseMap.containsKey(sku);
    }

    public boolean hasDetails(String sku) {
        return mSkuMap.containsKey(sku);
    }

    public void erasePurchase(String sku) {
        if (mPurchaseMap.containsKey(sku)) mPurchaseMap.remove(sku);
    }

    public List<String> getAllSkus(String itemType) {
        List<String> result = new ArrayList<String>();
        for (SkuDetails stated : mSkuMap.values()) {
            if (stated.getType().equals(itemType)) result.add(stated.getSku());
        }
        return result;
    }

    public List<Purchase> getAllPurchases() {
        return new ArrayList<Purchase>(mPurchaseMap.values());
    }

    public void addSkuDetails(SkuDetails d) {
        mSkuMap.put(d.getSku(), d);
    }

    public void addPurchase(Purchase p) {
        mPurchaseMap.put(p.getSku(), p);
    }
}
