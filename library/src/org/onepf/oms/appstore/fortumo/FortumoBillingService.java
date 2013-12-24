package org.onepf.oms.appstore.fortumo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import mp.MpUtils;
import mp.PaymentRequest;
import mp.PaymentResponse;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.googleUtils.*;

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
        //do nothing
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
                listener.onIabPurchaseFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED, "Already owned!"), null);
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
