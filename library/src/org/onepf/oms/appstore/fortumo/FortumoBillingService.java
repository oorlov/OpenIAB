package org.onepf.oms.appstore.fortumo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import mp.MpUtils;
import mp.PaymentRequest;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.appstore.googleUtils.*;

import java.util.List;

/**
 * Created by akarimova on 23.12.13.
 */
public class FortumoBillingService implements AppstoreInAppBillingService {
   private IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener;

    @Override
    public void startSetup(IabHelper.OnIabSetupFinishedListener listener) {
        //do nothing
    }

    @Override
    public void launchPurchaseFlow(Activity act, String sku, String itemType, int requestCode, IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {
        String[] split = sku.split(",");
        if (split.length < 2) {
            throw new IllegalStateException("service id and service key must be non-empty!");
        }
        this.purchaseFinishedListener = listener;
        FortumoPaymentActivity.startPaymentActivityForResult(act, requestCode, split[0], split[1]);
    }

    @Override
    public boolean handleActivityResult(int requestCode, int resultCode, Intent intent) {
        FortumoPaymentReceiver.processPayment(intent);
//        purchaseFinishedListener.onIabPurchaseFinished(new IabResult(errorCode, errorMsg), purchase);
        return true;
    }

    @Override
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus) throws IabException {
        return null;
    }

    @Override
    public void consume(Purchase itemInfo) throws IabException {

    }

    @Override
    public void dispose() {
        //do nothing
    }
}
