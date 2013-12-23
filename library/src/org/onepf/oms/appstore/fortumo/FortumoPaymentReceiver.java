package org.onepf.oms.appstore.fortumo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import mp.MpUtils;
import mp.PaymentResponse;

/**
 * Created by akarimova on 23.12.13.
 */
public class FortumoPaymentReceiver extends BroadcastReceiver {
    private static final String TAG = FortumoPaymentReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
    }

    public static boolean processPayment(Intent intent) {
        boolean result = false; //todo bad decision
        PaymentResponse paymentResponse = new PaymentResponse(intent);
        int billingStatus = paymentResponse.getBillingStatus();
        if (billingStatus == MpUtils.MESSAGE_STATUS_BILLED) {
            result = true;
        } else if (billingStatus == MpUtils.MESSAGE_STATUS_PENDING) {
            //todo ?
        } else {
            result = false;
        }
        Bundle extras = intent.getExtras();
        Log.d(TAG, "- billing_status:  " + extras.getInt("billing_status"));
        Log.d(TAG, "- credit_amount:   " + extras.getString("credit_amount"));
        Log.d(TAG, "- credit_name:     " + extras.getString("credit_name"));
        Log.d(TAG, "- message_id:      " + extras.getString("message_id"));
        Log.d(TAG, "- payment_code:    " + extras.getString("payment_code"));
        Log.d(TAG, "- price_amount:    " + extras.getString("price_amount"));
        Log.d(TAG, "- price_currency:  " + extras.getString("price_currency"));
        Log.d(TAG, "- product_name:    " + extras.getString("product_name"));
        Log.d(TAG, "- service_id:      " + extras.getString("service_id"));
        Log.d(TAG, "- user_id:         " + extras.getString("user_id"));

//        int billingStatus = extras.getInt("billing_status");
//        if (billingStatus == MpUtils.MESSAGE_STATUS_BILLED) {
//            int coins = Integer.parseInt(intent.getStringExtra("credit_amount"));
//        }
        return result;
    }
}
