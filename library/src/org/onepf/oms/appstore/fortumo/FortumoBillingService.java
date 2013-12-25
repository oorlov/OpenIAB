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
        final String[] split = sku.split(",");
        if (split.length != 4) {
            throw new IllegalStateException("service id, service key, sku type and sku name must be specified!");
        }
        this.purchaseFinishedListener = listener;
        boolean isConsumable = Boolean.parseBoolean(split[2]);
        if (!isConsumable) {
            int nonConsumablePaymentStatus = MpUtils.getNonConsumablePaymentStatus(act, split[0], split[1], split[3]);
            if (nonConsumablePaymentStatus == MpUtils.MESSAGE_STATUS_BILLED) {
                listener.onIabPurchaseFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED, "Already owned!"), null);//todo
                return;
            }
        }
        PaymentRequest paymentRequest = new PaymentRequest.PaymentRequestBuilder().setService(split[0], split[1]).
                setConsumable(isConsumable).
                setProductName(split[3]).
                build();
        FortumoUtils.startPaymentActivityForResult(act, requestCode, paymentRequest);
    }

    @Override
    public boolean handleActivityResult(int requestCode, int resultCode, Intent intent) {
        if (intent != null && resultCode == Activity.RESULT_OK) {
            PaymentResponse paymentResponse = new PaymentResponse(intent);
            FortumoPaymentReceiver.processPayment(context, intent);
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
                    String[] splittedSku = fortumoSku.split(",");
                    if (splittedSku[3].equals(purchase.getSku())) {
                        boolean consumable = Boolean.parseBoolean(splittedSku[2]);
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
        //add non-consumable skus
        List<String> allStoreSkus = OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO);
        for (String sku : allStoreSkus) {
            String[] skuDetatils = sku.split(",");
            boolean consumable = Boolean.parseBoolean(skuDetatils[2]);
            if (!consumable) {
                int nonConsumablePaymentStatus = MpUtils.getNonConsumablePaymentStatus(context, skuDetatils[0], skuDetatils[1], skuDetatils[3]);//todo change details order
                if (nonConsumablePaymentStatus == MpUtils.MESSAGE_STATUS_BILLED) {
                    Purchase purchase = new Purchase(OpenIabHelper.NAME_FORTUMO);
                    purchase.setPurchaseState(0);//todo?
                    purchase.setPackageName(context.getPackageName());
                    purchase.setItemType(IabHelper.ITEM_TYPE_INAPP);
                    purchase.setSku(skuDetatils[3]);
                    inventory.addPurchase(purchase);
                }
            }
        }
        //add consumable skus
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
            boolean wasRemoved = list.remove(openSkuName.split(",")[3]);
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
