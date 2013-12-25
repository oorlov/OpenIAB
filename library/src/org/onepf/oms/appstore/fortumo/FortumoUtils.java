package org.onepf.oms.appstore.fortumo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import mp.MpUtils;
import mp.PaymentRequest;

import java.lang.reflect.Field;

/**
 * Created by akarimova on 24.12.13.
 */
public class FortumoUtils {
    public static final String SP_FORTUMO_DEFAULT_NAME = "SP_FORTUMO_DEFAULT_NAME";
    public static final String SP_FORTUMO_CONSUMABLE_SKUS = "SP_FORTUMO_CONSUMABLE_SKUS";

    public static void startPaymentActivityForResult(Activity activity, int requestCode, PaymentRequest paymentRequest) {
        if (!MpUtils.isPaymentBroadcastEnabled(activity)) {
            try {
                Class permissionClass = Class.forName(activity.getPackageName() + ".Manifest$permission");
                Field paymentBroadcastPermission = permissionClass.getField("PAYMENT_BROADCAST_PERMISSION");
                String permissionString = (String) paymentBroadcastPermission.get(null);
                MpUtils.enablePaymentBroadcast(activity, permissionString);
            } catch (Exception e) {
                throw new IllegalStateException("PAYMENT_BROADCAST_PERMISSION must be declared!\n" + e);
            }
        }

        Intent localIntent = paymentRequest.toIntent(activity);
        checkPermission(activity, "android.permission.INTERNET");
        checkPermission(activity, "android.permission.ACCESS_NETWORK_STATE");
        checkPermission(activity, "android.permission.READ_PHONE_STATE");
        checkPermission(activity, "android.permission.RECEIVE_SMS");
        checkPermission(activity, "android.permission.SEND_SMS");
        activity.startActivityForResult(localIntent, requestCode);
    }

    private static void checkPermission(Context context, String paramString) {
        if (context.checkCallingOrSelfPermission(paramString) != PackageManager.PERMISSION_GRANTED)
            throw new IllegalStateException(String.format("Required permission \"%s\" is NOT granted.", paramString));
    }

    public static String openSkuDescription(String serviceId, String appSecret, boolean consumable, String skuName) {
        return String.format("%s,%s,%s,%s", serviceId, appSecret, consumable, skuName);
    }

}
