package org.onepf.oms.appstore.fortumo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import mp.PaymentResponse;

/**
 * Created by akarimova on 23.12.13.
 */
public class FortumoPaymentReceiver extends BroadcastReceiver {
    public static final String SHARED_PREFS_NAME = "shared_prefs_name";
    public static final String SP_PAYMENT_RESPONSE_TO_PROCEED = "sp_payment_response_to_proceed";

    @Override
    public void onReceive(Context context, Intent intent) {
        processPayment(context, intent);
    }

    public static void processPayment(Context context, PaymentResponse paymentResponse) {
        if (paymentResponse != null) {
            SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong(SP_PAYMENT_RESPONSE_TO_PROCEED, paymentResponse.getMessageId());
            editor.commit();
        }
    }

    public static void processPayment(Context context, Intent intent) {
        processPayment(context, new PaymentResponse(intent));
    }
}
