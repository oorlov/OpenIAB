package org.onepf.oms.appstore.fortumo;

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
        listener.onIabSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_OK, "Setup successful"));
    }

    @Override
    public void launchPurchaseFlow(final Activity act, String sku, String itemType, int requestCode, IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {
        this.purchaseFinishedListener = listener;
        if (FortumoStore.FortumoUtils.getSkuType(sku).equals(OpenIabHelper.ITEM_TYPE_SUBS)) {
            listener.onIabPurchaseFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE, "Fortumo doesn't support subscriptions."), null);
        } else {
            PaymentRequest paymentRequest = new PaymentRequest.PaymentRequestBuilder().setService(FortumoStore.FortumoUtils.getSkuServiceId(sku), FortumoStore.FortumoUtils.getSkuAppSecret(sku)).
                    setConsumable(FortumoStore.FortumoUtils.isSkuConsumable(sku)).
                    setProductName(FortumoStore.FortumoUtils.getSkuName(sku)).
                    build();
            FortumoStore.FortumoUtils.startPaymentActivityForResult(act, requestCode, paymentRequest);

        }
    }

    @Override
    public boolean handleActivityResult(int requestCode, int resultCode, Intent intent) {
        if (intent == null) return false; //no data to handle
        PaymentResponse paymentResponse = new PaymentResponse(intent);
        boolean consumable = false;
        List<String> allStoreSkus = OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO);
        for (String fortumoSku : allStoreSkus) {
            if (FortumoStore.FortumoUtils.getSkuName(fortumoSku).equals(paymentResponse.getProductName())) {
                consumable = FortumoStore.FortumoUtils.isSkuConsumable(fortumoSku);
                break;
            }
        }
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
        purchaseFinishedListener.onIabPurchaseFinished(new IabResult(errorCode, errorMsg), purchase);
        return true;
    }

    @Override
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus) throws IabException {
        Inventory inventory = new Inventory();
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoStore.FortumoUtils.SP_FORTUMO_DEFAULT_NAME, Context.MODE_PRIVATE);
        long deferredMessageId = sharedPreferences.getLong(FortumoStore.FortumoUtils.SP_PAYMENT_MESSAGE_ID_PROCEED, -1);
        if (deferredMessageId != -1) {
            String deferredPaymentName = sharedPreferences.getString(FortumoStore.FortumoUtils.SP_PAYMENT_NAME_TO_PROCEED, "");
            if (!TextUtils.isEmpty(deferredPaymentName)) {
                PaymentResponse paymentResponse = MpUtils.getPaymentResponse(context, deferredMessageId);
                if (paymentResponse.getBillingStatus() == MpUtils.MESSAGE_STATUS_BILLED) {
                    List<String> allStoreSkus = OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO);
                    for (String sku : allStoreSkus) {
                        if (FortumoStore.FortumoUtils.getSkuName(sku).equals(deferredPaymentName)) {
                            Purchase purchase = new Purchase(OpenIabHelper.NAME_FORTUMO);
                            purchase.setItemType(FortumoStore.FortumoUtils.getSkuType(sku));
                            purchase.setPackageName(context.getPackageName());
                            purchase.setOrderId(paymentResponse.getPaymentCode());
                            boolean skuConsumable = FortumoStore.FortumoUtils.isSkuConsumable(sku);
                            if (skuConsumable) {
                                addConsumableItem(purchase);
                            }
                            inventory.addPurchase(purchase);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.remove(FortumoStore.FortumoUtils.SP_PAYMENT_MESSAGE_ID_PROCEED);
                            editor.remove(FortumoStore.FortumoUtils.SP_PAYMENT_NAME_TO_PROCEED);
                            editor.commit();
                            break;
                        }
                    }
                }
            }
        }
        //Getting non-consumable purchased items from Fortumo.
        List<String> allFortumoSkus = OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO);
        for (String sku : allFortumoSkus) {
            boolean consumable = FortumoStore.FortumoUtils.isSkuConsumable(sku);
            String skuName = FortumoStore.FortumoUtils.getSkuName(sku);
            if (!consumable) {
                //if order id is not required then use MpUtils.getNonConsumablePaymentStatus()
                List purchaseHistory = MpUtils.getPurchaseHistory(context, FortumoStore.FortumoUtils.getSkuServiceId(sku), FortumoStore.FortumoUtils.getSkuAppSecret(sku), 5000);
                for (int i = 0; i < purchaseHistory.size(); i++) {
                    PaymentResponse paymentResponse = (PaymentResponse) purchaseHistory.get(i);
                    if (paymentResponse.getProductName().equals(skuName)) {
                        inventory.addPurchase(purchaseFromPaymentResponse(context, paymentResponse));
                        break;
                    }
                }
            }
        }

        //Getting consumable purchased items from OpenIAB shared prefs.
        ArrayList<Purchase> consumableSkus = getConsumableSkus();
        for (Purchase purchase : consumableSkus) {
            inventory.addPurchase(purchase);
        }

        return inventory;
    }

    @Override
    public void consume(Purchase itemInfo) throws IabException {
        //check for subs
        try {
            consumeSkuFromPreferences(itemInfo);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void dispose() {
        //do nothing
    }


    private void addConsumableItem(Purchase purchase) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoStore.FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, Context.MODE_PRIVATE);
        String consumableSkusString = sharedPreferences.getString(FortumoStore.FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, "");
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
                editor.putString(FortumoStore.FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, consumablePurchases.toString());
                editor.commit();
            }

        } catch (JSONException e) {
            throw new IllegalStateException("Can't add purchase: " + purchase.getSku(), e);
        }
    }

    private void consumeSkuFromPreferences(Purchase purchase) throws JSONException {
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoStore.FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, Context.MODE_PRIVATE);
        String consumableSkuString = sharedPreferences.getString(FortumoStore.FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, "");
        if (!TextUtils.isEmpty(consumableSkuString)) {
            JSONArray jsonArray = new JSONArray(consumableSkuString);
            int indexToRemove = -1;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject object = (JSONObject) jsonArray.get(i);
                if (FortumoStore.FortumoUtils.getSkuName(purchase.getSku()).equals(object.getString("sku"))
                        && purchase.getOrderId().equals(object.getString("order_id"))) {
                    indexToRemove = i;
                    break;
                }
            }
            if (indexToRemove >= 0) {
                JSONUtils.remove(indexToRemove, jsonArray);
            }
        }
    }

    private ArrayList<Purchase> getConsumableSkus() {
        ArrayList<Purchase> purchases = new ArrayList<Purchase>();
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoStore.FortumoUtils.SP_FORTUMO_DEFAULT_NAME, Context.MODE_PRIVATE);
        String consumableSkuString = sharedPreferences.getString(FortumoStore.FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, "");
        try {
            JSONArray jsonArray;
            if (TextUtils.isEmpty(consumableSkuString)) {
                jsonArray = new JSONArray();
            } else {
                jsonArray = new JSONArray(consumableSkuString);
            }
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject object = (JSONObject) jsonArray.get(i);
                purchases.add(convertJsonToPurchase(object));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return purchases;
    }

    private static Purchase purchaseFromPaymentResponse(Context context, PaymentResponse paymentResponse) {
        Purchase purchase = new Purchase(OpenIabHelper.NAME_FORTUMO);
        purchase.setSku(paymentResponse.getProductName());
        purchase.setPackageName(context.getPackageName());
        String openFortumoSku = OpenIabHelper.getStoreSku(OpenIabHelper.NAME_FORTUMO, paymentResponse.getProductName());
        purchase.setItemType(FortumoStore.FortumoUtils.getSkuType(openFortumoSku));
        purchase.setOrderId(paymentResponse.getPaymentCode());
        purchase.setPurchaseState(0);
        Date date = paymentResponse.getDate();
        if (date != null) {
            purchase.setPurchaseTime(date.getTime());
        }
        return purchase;
    }

    private Purchase convertJsonToPurchase(JSONObject object) throws JSONException {
        Purchase purchase = new Purchase(OpenIabHelper.NAME_FORTUMO);
        purchase.setSku(object.getString("sku"));
        purchase.setOrderId(object.getString("order_id"));
        purchase.setPurchaseState(0);//todo?
        purchase.setPackageName(context.getPackageName());
        purchase.setItemType(IabHelper.ITEM_TYPE_INAPP); //todo set type from skus

        return purchase;
    }

    private static class JSONUtils {

        public static List<JSONObject> asList(final JSONArray jsonArray) {
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

        public static JSONArray remove(final int idx, final JSONArray from) {
            final List<JSONObject> objs = asList(from);
            objs.remove(idx);

            final JSONArray ja = new JSONArray();
            for (final JSONObject obj : objs) {
                ja.put(obj);
            }
            return ja;
        }
    }

}
