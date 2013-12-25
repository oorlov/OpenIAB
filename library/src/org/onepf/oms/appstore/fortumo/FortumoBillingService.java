package org.onepf.oms.appstore.fortumo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import mp.MpUtils;
import mp.PaymentRequest;
import mp.PaymentResponse;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.googleUtils.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
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
                listener.onIabSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, "PAYMENT_BROADCAST_PERMISSION is not declared."));
            }
        }
        listener.onIabSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_OK, "Setup successful"));
    }

    @Override
    public void launchPurchaseFlow(final Activity act, String sku, String itemType, int requestCode, IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {
        this.purchaseFinishedListener = listener;
        if (FortumoUtils.getSkuType(sku).equals(OpenIabHelper.ITEM_TYPE_SUBS)) {
            listener.onIabPurchaseFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE, "Subscriptions are not currently supported!"), null);//todo
        } else {
            boolean isConsumable = FortumoUtils.getSkuConsumable(sku);
            if (!isConsumable) {
                int nonConsumablePaymentStatus = MpUtils.getNonConsumablePaymentStatus(act, FortumoUtils.getSkuName(sku), FortumoUtils.getSkuServiceId(sku), FortumoUtils.getSkuAppSecret(sku));
                if (nonConsumablePaymentStatus == MpUtils.MESSAGE_STATUS_BILLED) {
                    listener.onIabPurchaseFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED, "Already owned!"), null);//todo
                    return;
                }
            }
            PaymentRequest paymentRequest = new PaymentRequest.PaymentRequestBuilder().setService(FortumoUtils.getSkuServiceId(sku), FortumoUtils.getSkuAppSecret(sku)).
                    setConsumable(isConsumable).
                    setProductName(FortumoUtils.getSkuName(sku)).
                    build();
            FortumoUtils.startPaymentActivityForResult(act, requestCode, paymentRequest);

        }
    }

    @Override
    public boolean handleActivityResult(int requestCode, int resultCode, Intent intent) {
        if (intent != null && resultCode == Activity.RESULT_OK) {
            PaymentResponse paymentResponse = new PaymentResponse(intent);
            int errorCode = IabHelper.BILLING_RESPONSE_RESULT_OK;
            String errorMsg = "";
            Purchase purchase;
            if (paymentResponse.getBillingStatus() == MpUtils.MESSAGE_STATUS_BILLED) {
                purchase = new Purchase(OpenIabHelper.NAME_FORTUMO);
                purchase.setItemType(IabHelper.ITEM_TYPE_INAPP);
                Date date = paymentResponse.getDate();
                if (date != null) {
                    purchase.setPurchaseTime(date.getTime());
                }
                purchase.setPackageName(context.getPackageName());
//            purchase.setPurchaseState(); //todo
//            purchase.setSku(paymentResponse.getSku()); //todo
                purchase.setSku(paymentResponse.getProductName()); //todo
                purchase.setOrderId(String.valueOf(paymentResponse.getMessageId()));
                //consumable or not
                List<String> allStoreSkus = OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO);
                for (String fortumoSku : allStoreSkus) {
                    if (FortumoUtils.getSkuName(fortumoSku).equals(purchase.getSku())) {
                        boolean consumable = FortumoUtils.getSkuConsumable(fortumoSku);
                        if (consumable) {
                            addConsumableItem(purchase.getSku());
                        }
                        break;
                    }
                }

            } else {
                errorCode = IabHelper.BILLING_RESPONSE_RESULT_ERROR;
                purchase = null;
            }
            purchaseFinishedListener.onIabPurchaseFinished(new IabResult(errorCode, errorMsg), purchase);
            return true;
        }
        return false;
    }

    @Override
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus) throws IabException {
        Inventory inventory = new Inventory();
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoUtils.SP_FORTUMO_DEFAULT_NAME, Context.MODE_PRIVATE);
        long deferredMessageId = sharedPreferences.getLong(FortumoUtils.SP_PAYMENT_MESSAGE_ID_PROCEED, -1);
        if (deferredMessageId != -1) {
            String deferredPaymentName = sharedPreferences.getString(FortumoUtils.SP_PAYMENT_NAME_TO_PROCEED, "");
            if (!TextUtils.isEmpty(deferredPaymentName)) {
                PaymentResponse paymentResponse = MpUtils.getPaymentResponse(context, deferredMessageId);
                if (paymentResponse.getBillingStatus() == MpUtils.MESSAGE_STATUS_BILLED) {
                    List<String> allStoreSkus = OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO);
                    for (String sku : allStoreSkus) {
                        if (FortumoUtils.getSkuName(sku).equals(deferredPaymentName)) {
                            Purchase purchase = new Purchase(OpenIabHelper.NAME_FORTUMO);
                            purchase.setItemType(FortumoUtils.getSkuType(sku));
                            purchase.setPackageName(context.getPackageName());
                            purchase.setOrderId(String.valueOf(deferredMessageId));
                            boolean skuConsumable = FortumoUtils.getSkuConsumable(sku);
                            if (skuConsumable) {
                                addConsumableItem(deferredPaymentName);
                            }
                            inventory.addPurchase(purchase);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.remove(FortumoUtils.SP_PAYMENT_MESSAGE_ID_PROCEED);
                            editor.remove(FortumoUtils.SP_PAYMENT_NAME_TO_PROCEED);
                            editor.commit();
                            break;
                        }
                    }
                }
            }
        }

        //non-consumable skus
        List<String> allStoreSkus = OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO);
        for (String sku : allStoreSkus) {
            boolean consumable = FortumoUtils.getSkuConsumable(sku);
            if (!consumable) {
                int nonConsumablePaymentStatus = MpUtils.getNonConsumablePaymentStatus(context, FortumoUtils.getSkuServiceId(sku), FortumoUtils.getSkuAppSecret(sku),
                        FortumoUtils.getSkuName(sku));
                if (nonConsumablePaymentStatus == MpUtils.MESSAGE_STATUS_BILLED) {
                    Purchase purchase = new Purchase(OpenIabHelper.NAME_FORTUMO);
                    purchase.setPurchaseState(0);//todo?
                    purchase.setPackageName(context.getPackageName());
                    purchase.setItemType(IabHelper.ITEM_TYPE_INAPP);
                    purchase.setSku(FortumoUtils.getSkuName(sku));
                    inventory.addPurchase(purchase);
                }
            }
        }
        //consumable skus
        String[] consumableSkus = getConsumableSkus();
        for (String consumableSku : consumableSkus) {
            Purchase purchase = new Purchase(OpenIabHelper.NAME_FORTUMO);
            purchase.setPurchaseState(0);//todo?
            purchase.setPackageName(context.getPackageName());
            purchase.setItemType(IabHelper.ITEM_TYPE_INAPP);
            purchase.setSku(consumableSku);
            inventory.addPurchase(purchase);
        }

        return inventory;
    }

    @Override
    public void consume(Purchase itemInfo) throws IabException {
        consumeSkuFromPreferences(itemInfo.getSku());
    }

    @Override
    public void dispose() {
        //do nothing
    }


    //todo message id
    private void addConsumableItem(String skuName) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, Context.MODE_PRIVATE);
        String consumableSkusString = sharedPreferences.getString(FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, "");
        if (consumableSkusString.length() > 0) {
            consumableSkusString += ",";
        }
        consumableSkusString += skuName;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, consumableSkusString);
        editor.commit();
    }

    private boolean consumeSkuFromPreferences(String openSkuName) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, Context.MODE_PRIVATE);
        String consumableSkuString = sharedPreferences.getString(FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, "");
        if (!TextUtils.isEmpty(consumableSkuString)) {
            String[] skuArray = consumableSkuString.split(",");
            List<String> list = new ArrayList<String>(Arrays.asList(skuArray));
            boolean wasRemoved = list.remove(FortumoUtils.getSkuName(openSkuName));
            if (wasRemoved) {
                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    stringBuilder.append(list.get(i));
                    if (i != list.size() - 1) {
                        stringBuilder.append(",");
                    }
                }
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, stringBuilder.toString());
                editor.commit();
                return true;
            }

        }
        return false;
    }

    private String[] getConsumableSkus() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoUtils.SP_FORTUMO_DEFAULT_NAME, Context.MODE_PRIVATE);
        String consumableSkuString = sharedPreferences.getString(FortumoUtils.SP_FORTUMO_CONSUMABLE_SKUS, "");
        return consumableSkuString.split(",");
    }
}
