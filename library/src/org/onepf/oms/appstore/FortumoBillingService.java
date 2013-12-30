package org.onepf.oms.appstore;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import mp.MpUtils;
import mp.PaymentRequest;
import mp.PaymentResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.googleUtils.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by akarimova on 23.12.13.
 */
public class FortumoBillingService implements AppstoreInAppBillingService {
    private Context context;
    private int activityRequestCode;
    private IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener;

    public FortumoBillingService(Context context) {
        this.context = context;
    }

    @Override
    public void startSetup(IabHelper.OnIabSetupFinishedListener listener) {
        if (!MpUtils.isPaymentBroadcastEnabled(context)) {
            try {
                Class permissionClass = Class.forName(context.getPackageName() + ".Manifest$permission");
                Field paymentBroadcastPermission = permissionClass.getField("PAYMENT_BROADCAST_PERMISSION");
                String permissionString = (String) paymentBroadcastPermission.get(null);
                MpUtils.enablePaymentBroadcast(context, permissionString);
            } catch (Exception ignored) {
                listener.onIabSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, "PAYMENT_BROADCAST_PERMISSION is NOT declared."));
            }
        }
        if (listener != null) {
            listener.onIabSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_OK, "Setup successful"));
        }
    }

    @Override
    public void launchPurchaseFlow(final Activity act, String sku, String itemType, int requestCode, IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {
        this.purchaseFinishedListener = listener;
        if (FortumoStore.getSkuType(sku).equals(OpenIabHelper.ITEM_TYPE_SUBS)) {
            if (listener != null) {
                listener.onIabPurchaseFinished(new IabResult(IabHelper.IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE, "Fortumo: subscriptions are not supported"), null);
            }
        } else {
            String skuName = FortumoStore.getSkuName(sku);
            ArrayList<Purchase> consumablePurchases = getConsumablePurchases();
            for (Purchase purchase : consumablePurchases) {
                if (purchase.getSku().equals(skuName)) {
                    if (listener != null) {
                        listener.onIabPurchaseFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED, String.format("Fortumo: item %s already owned", skuName)), null);
                    }
                }
            }

            this.activityRequestCode = requestCode;
            PaymentRequest paymentRequest = new PaymentRequest.PaymentRequestBuilder().setService(FortumoStore.getSkuServiceId(sku), FortumoStore.getSkuAppSecret(sku)).
                    setConsumable(FortumoStore.isSkuConsumable(sku)).
                    setProductName(skuName).
                    build();
            FortumoStore.startPaymentActivityForResult(act, requestCode, paymentRequest);

        }
    }

    @Override
    public boolean handleActivityResult(int requestCode, int resultCode, Intent intent) {
        if (activityRequestCode != requestCode) return false;
        if (intent == null) {
            if (purchaseFinishedListener != null) {
                purchaseFinishedListener.onIabPurchaseFinished(new IabResult(IabHelper.IABHELPER_BAD_RESPONSE, "Null data in Fortumo IAB result"), null);
            }
        }
        PaymentResponse paymentResponse = new PaymentResponse(intent);
        boolean consumable = isItemConsumable(paymentResponse);
        Purchase purchase = purchaseFromPaymentResponse(context, paymentResponse);
        int errorCode = IabHelper.BILLING_RESPONSE_RESULT_ERROR;
        String errorMsg = "";
        if (resultCode == Activity.RESULT_OK) {
            if (paymentResponse.getBillingStatus() == MpUtils.MESSAGE_STATUS_BILLED) {
                errorCode = IabHelper.BILLING_RESPONSE_RESULT_OK;
                if (consumable) {
                    addConsumableItem(purchase);
                }
            }
        }
        if (purchaseFinishedListener != null) {
            purchaseFinishedListener.onIabPurchaseFinished(new IabResult(errorCode, errorMsg), purchase);
        }
        return true;
    }

    @Override
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus) throws IabException {
        Inventory inventory = new Inventory();
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoStore.SHARED_PREFS_FORTUMO, Context.MODE_PRIVATE);

        String messageId = sharedPreferences.getString(FortumoStore.SHARED_PREFS_PAYMENT_TO_HANDLE, "");
        if (!TextUtils.isEmpty(messageId)) {
            PaymentResponse paymentResponse = MpUtils.getPaymentResponse(context, Long.valueOf(messageId));
            if (paymentResponse != null) {
                Purchase purchase = purchaseFromPaymentResponse(context, paymentResponse);
                if (isItemConsumable(paymentResponse)) {
                    addConsumableItem(purchase);
                }
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.remove(FortumoStore.SHARED_PREFS_PAYMENT_TO_HANDLE);
                editor.commit();
            }
        }

        //Non-consumable items from Fortumo
        List<String> allFortumoSkus = OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO);
        for (String sku : allFortumoSkus) {
            boolean consumable = FortumoStore.isSkuConsumable(sku);
            String skuName = FortumoStore.getSkuName(sku);
            if (!consumable) {
                List purchaseHistory = MpUtils.getPurchaseHistory(context, FortumoStore.getSkuServiceId(sku), FortumoStore.getSkuAppSecret(sku), 5000);
                for (int i = 0; i < purchaseHistory.size(); i++) {
                    PaymentResponse paymentResponse = (PaymentResponse) purchaseHistory.get(i);
                    if (paymentResponse.getProductName().equals(skuName)) {
                        inventory.addPurchase(purchaseFromPaymentResponse(context, paymentResponse));
                        break;
                    }
                }
            }
        }

        //Consumable items from shared prefs
        ArrayList<Purchase> consumableSkus = getConsumablePurchases();
        for (Purchase purchase : consumableSkus) {
            inventory.addPurchase(purchase);
        }

        return inventory;
    }

    @Override
    public void consume(Purchase itemInfo) throws IabException {
        if (!itemInfo.getItemType().equals(IabHelper.ITEM_TYPE_INAPP)) {
            throw new IabException(IabHelper.IABHELPER_INVALID_CONSUMPTION,
                    "Items of type '" + itemInfo.getItemType() + "' can't be consumed.");
        }
        try {
            consumeSkuFromPreferences(itemInfo);
        } catch (JSONException e) {
            throw new IabException(IabHelper.IABHELPER_BAD_RESPONSE,
                    "JSONException while consuming " + itemInfo.getSku() + " " + e);
        }
    }

    @Override
    public void dispose() {
        //do nothing
    }


    private void addConsumableItem(Purchase purchase) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoStore.SHARED_PREFS_FORTUMO_CONSUMABLE_SKUS, Context.MODE_PRIVATE);
        String consumableSkusString = sharedPreferences.getString(FortumoStore.SHARED_PREFS_FORTUMO_CONSUMABLE_SKUS, "");
        try {
            JSONArray consumablePurchases;
            if (TextUtils.isEmpty(consumableSkusString)) {
                consumablePurchases = new JSONArray();
            } else {
                consumablePurchases = new JSONArray(consumableSkusString);

            }
            boolean alreadyContains = false;
            for (int i = 0; i < consumablePurchases.length(); i++) {
                JSONObject object = (JSONObject) consumablePurchases.get(i);
                if (object.getString("sku").equals(purchase.getSku()) &&
                        object.getString("order_id").equals(purchase.getOrderId())) {
                    alreadyContains = true;
                    break;
                }
            }
            if (!alreadyContains) {
                JSONObject purchasedObject = new JSONObject();
                purchasedObject.put("sku", purchase.getSku());
                purchasedObject.put("order_id", purchase.getOrderId());
                consumablePurchases.put(purchasedObject);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(FortumoStore.SHARED_PREFS_FORTUMO_CONSUMABLE_SKUS, consumablePurchases.toString());
                editor.commit();
            }

        } catch (JSONException e) {
            throw new IllegalStateException("Can't add purchase: " + purchase.getSku(), e);
        }
    }

    private void consumeSkuFromPreferences(Purchase purchase) throws JSONException {
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoStore.SHARED_PREFS_FORTUMO_CONSUMABLE_SKUS, Context.MODE_PRIVATE);
        String consumableSkuString = sharedPreferences.getString(FortumoStore.SHARED_PREFS_FORTUMO_CONSUMABLE_SKUS, "");
        if (!TextUtils.isEmpty(consumableSkuString)) {
            JSONArray jsonArray = new JSONArray(consumableSkuString);
            int indexToRemove = -1;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject object = (JSONObject) jsonArray.get(i);
                if (FortumoStore.getSkuName(purchase.getSku()).equals(object.getString("sku"))
                        && purchase.getOrderId().equals(object.getString("order_id"))) {
                    indexToRemove = i;
                    break;
                }
            }
            if (indexToRemove >= 0) {
                JSONUtils.removeElementByIndex(indexToRemove, jsonArray);
            }
        }
    }

    /**
     * Returns consumable purchases from shared preferences
     */
    private ArrayList<Purchase> getConsumablePurchases() {
        ArrayList<Purchase> purchases = new ArrayList<Purchase>();
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoStore.SHARED_PREFS_FORTUMO, Context.MODE_PRIVATE);
        String consumableSkuString = sharedPreferences.getString(FortumoStore.SHARED_PREFS_FORTUMO_CONSUMABLE_SKUS, "");
        try {
            JSONArray jsonArray;
            if (TextUtils.isEmpty(consumableSkuString)) {
                jsonArray = new JSONArray();
            } else {
                jsonArray = new JSONArray(consumableSkuString);
            }
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject object = (JSONObject) jsonArray.get(i);
                purchases.add(convertJsonToPurchase(context, object));
            }
        } catch (JSONException e) {
            SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.remove(FortumoStore.SHARED_PREFS_FORTUMO_CONSUMABLE_SKUS);
            edit.commit();
        }
        return purchases;
    }

    private static Purchase purchaseFromPaymentResponse(Context context, PaymentResponse paymentResponse) {
        Purchase purchase = new Purchase(OpenIabHelper.NAME_FORTUMO);
        purchase.setSku(paymentResponse.getProductName());
        purchase.setPackageName(context.getPackageName());
        String openFortumoSku = OpenIabHelper.getStoreSku(OpenIabHelper.NAME_FORTUMO, paymentResponse.getProductName());
        purchase.setItemType(FortumoStore.getSkuType(openFortumoSku));
        purchase.setOrderId(paymentResponse.getPaymentCode());
        Date date = paymentResponse.getDate();
        if (date != null) {
            purchase.setPurchaseTime(date.getTime());
        }
        return purchase;
    }

    private static Purchase convertJsonToPurchase(Context context, JSONObject object) throws JSONException {
        Purchase purchase = new Purchase(OpenIabHelper.NAME_FORTUMO);
        purchase.setSku(object.getString("sku"));
        purchase.setOrderId(object.getString("order_id"));
        purchase.setPackageName(context.getPackageName());
        String fortumoSku = OpenIabHelper.getStoreSku(OpenIabHelper.NAME_FORTUMO, object.getString("sku"));
        purchase.setItemType(FortumoStore.getSkuType(fortumoSku));
        return purchase;
    }


    private static boolean isItemConsumable(PaymentResponse paymentResponse) {
        boolean consumable = false;
        List<String> allStoreSkus = OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO);
        for (String fortumoSku : allStoreSkus) {
            if (FortumoStore.getSkuName(fortumoSku).equals(paymentResponse.getProductName())) {
                consumable = FortumoStore.isSkuConsumable(fortumoSku);
                break;
            }
        }
        return consumable;
    }

    private static class JSONUtils {

        public static List<JSONObject> jsonArrayAsList(final JSONArray jsonArray) {
            final int len = jsonArray.length();
            final ArrayList<JSONObject> result = new ArrayList<JSONObject>(len);
            for (int i = 0; i < len; i++) {
                final JSONObject obj = jsonArray.optJSONObject(i);
                if (obj != null) {
                    result.add(obj);
                }
            }
            return result;
        }

        public static JSONArray removeElementByIndex(final int idx, final JSONArray from) {
            final List<JSONObject> jsonObjects = jsonArrayAsList(from);
            jsonObjects.remove(idx);
            final JSONArray resultedArray = new JSONArray();
            for (final JSONObject obj : jsonObjects) {
                resultedArray.put(obj);
            }
            return resultedArray;
        }
    }

}
