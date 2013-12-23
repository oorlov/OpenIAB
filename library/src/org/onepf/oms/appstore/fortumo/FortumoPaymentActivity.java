package org.onepf.oms.appstore.fortumo;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import mp.MpUtils;
import mp.PaymentActivity;
import mp.PaymentRequest;

import java.lang.reflect.Field;

/**
 * Created by akarimova on 23.12.13.
 */
public final class FortumoPaymentActivity extends PaymentActivity {
    //todo make private
    //todo add protection?
    public static final String EXTRA_PAYMENT_SERVICE_ID = "EXTRA_PAYMENT_SERVICE_ID";
    public static final String EXTRA_PAYMENT_SERVICE_SECRET = "EXTRA_PAYMENT_SERVICE_SECRET";
    private BroadcastReceiver mReceiver;
    private String mPermissionString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        //todo add comment
        if (!MpUtils.isPaymentBroadcastEnabled(this)) {
            try {
                Class permissionClass = Class.forName(getPackageName() + ".Manifest$permission");
                Field paymentBroadcastPermission = permissionClass.getField("PAYMENT_BROADCAST_PERMISSION");
                mPermissionString = (String) paymentBroadcastPermission.get(null);
                MpUtils.enablePaymentBroadcast(this, mPermissionString);
            } catch (Exception e) {
                throw new IllegalStateException("PAYMENT_BROADCAST_PERMISSION must be declared!\n" + e);
            }
        }
        //todo add comment
        String serviceId = getIntent().getStringExtra(EXTRA_PAYMENT_SERVICE_ID);
        String serviceSecret = getIntent().getStringExtra(EXTRA_PAYMENT_SERVICE_SECRET);
        if (TextUtils.isEmpty(serviceId) || TextUtils.isEmpty(serviceSecret)) {
            throw new IllegalStateException("service id and service secret must be non-empty!");
        }
        //todo add comment
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                FortumoPaymentReceiver.processPayment(intent);
                FortumoPaymentActivity.this.setResult(RESULT_OK, intent);
                FortumoPaymentActivity.this.finish();
            }
        };

        enableBroadcastReceiver(false);
        registerReceiver(mReceiver, new IntentFilter("mp.info.PAYMENT_STATUS_CHANGED"), mPermissionString, null);
        PaymentRequest.PaymentRequestBuilder paymentRequestBuilder = new PaymentRequest.PaymentRequestBuilder()
                .setService(serviceId, serviceSecret);
        PaymentRequest paymentRequest = paymentRequestBuilder.build();
        makePayment(paymentRequest);
    }

    private void enableBroadcastReceiver(boolean enabled) {
        int flag = (enabled ?
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        ComponentName component = new ComponentName(this, FortumoPaymentReceiver.class);
        getPackageManager()
                .setComponentEnabledSetting(component, flag,
                        PackageManager.DONT_KILL_APP);
    }

    @Override
    protected void onResume() {
        super.onResume();
//        enableBroadcastReceiver(false);
//        registerReceiver(mReceiver, new IntentFilter("mp.info.PAYMENT_STATUS_CHANGED"), mPermissionString, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
//        enableBroadcastReceiver(true);
//        unregisterReceiver(mReceiver);
    }

    protected static void startPaymentActivityForResult(Activity activityContext, int requestCode, String serviceId, String serviceSecret) {
        Intent intent = new Intent(activityContext, FortumoPaymentActivity.class);
        intent.putExtra(EXTRA_PAYMENT_SERVICE_ID, serviceId);
        intent.putExtra(EXTRA_PAYMENT_SERVICE_SECRET, serviceSecret);
        activityContext.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onStop() {
        super.onStop();
        enableBroadcastReceiver(true);
    }
}
