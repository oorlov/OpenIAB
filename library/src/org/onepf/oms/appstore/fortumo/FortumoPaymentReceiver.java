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

    @Override
    public void onReceive(Context context, Intent intent) {
        PaymentResponse paymentResponse = new PaymentResponse(intent);
        SharedPreferences sharedPreferences = context.getSharedPreferences(FortumoStore.FortumoUtils.SP_FORTUMO_DEFAULT_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(FortumoStore.FortumoUtils.SP_PAYMENT_NAME_TO_PROCEED, paymentResponse.getProductName());
        editor.putString(FortumoStore.FortumoUtils.SP_PAYMENT_MESSAGE_ID_PROCEED, paymentResponse.getPaymentCode());
        editor.commit();
    }

}
