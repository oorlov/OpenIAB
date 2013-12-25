package org.onepf.oms.appstore.fortumo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import mp.*;

import java.lang.reflect.Field;

/**
 * Created by akarimova on 24.12.13.
 */
public class FortumoUtils {
    public static final String SP_FORTUMO_DEFAULT_NAME = "SP_FORTUMO_DEFAULT_NAME";
    public static final String SP_FORTUMO_CONSUMABLE_SKUS = "SP_FORTUMO_CONSUMABLE_SKUS";
    public static final String SP_PAYMENT_MESSAGE_ID_PROCEED = "SP_PAYMENT_MESSAGE_ID_PROCEED";
    public static final String SP_PAYMENT_NAME_TO_PROCEED = "SP_PAYMENT_MESSAGE_ID_PROCEED";

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
        activity.startActivityForResult(localIntent, requestCode);
    }

    private static void checkPermission(Context context, String paramString) {
        if (context.checkCallingOrSelfPermission(paramString) != PackageManager.PERMISSION_GRANTED)
            throw new IllegalStateException(String.format("Required permission \"%s\" is NOT granted.", paramString));
    }


    public static String openSkuDescription(String serviceId, String appSecret, boolean consumable, String skuName) {
        return String.format("%s,%s,%s,%s", serviceId, appSecret, consumable, skuName);
    }


    public static void checkFortumoSettings(Context context) {
        checkPermission(context, "android.permission.INTERNET");
        checkPermission(context, "android.permission.ACCESS_NETWORK_STATE");
        checkPermission(context, "android.permission.READ_PHONE_STATE");
        checkPermission(context, "android.permission.RECEIVE_SMS");
        checkPermission(context, "android.permission.SEND_SMS");

        String appDeclaredPermission;
        try {
            Class permissionClass = Class.forName(context.getPackageName() + ".Manifest$permission");
            Field paymentBroadcastPermission = permissionClass.getField("PAYMENT_BROADCAST_PERMISSION");
            appDeclaredPermission = (String) paymentBroadcastPermission.get(null);
        } catch (Exception e) {
            throw new IllegalStateException("PAYMENT_BROADCAST_PERMISSION must be declared!\n" + e);
        }
        if (TextUtils.isEmpty(appDeclaredPermission)) {
            throw new IllegalStateException("PAYMENT_BROADCAST_PERMISSION must be declared!");
        }

        Intent paymentActivityIntent = new Intent();
        paymentActivityIntent.setClassName(context.getPackageName(), MpActivity.class.getName());
        if (context.getPackageManager().resolveActivity(paymentActivityIntent, 0) == null) {//todo flag
            throw new IllegalStateException("mp.MpActivity must be declared!");
        }

        Intent mpServerIntent = new Intent();
        mpServerIntent.setClassName(context.getPackageName(), MpService.class.getName());
        if (context.getPackageManager().resolveService(mpServerIntent, 0) == null) {//todo flag
            throw new IllegalStateException("mp.MpService must be declared!");
        }

        Intent statusUpdateServiceIntent = new Intent();
        statusUpdateServiceIntent.setClassName(context.getPackageName(), StatusUpdateService.class.getName());
        if (context.getPackageManager().resolveService(statusUpdateServiceIntent, 0) == null) {
            throw new IllegalStateException("mp.StatusUpdateService must be declared!");
        }

    }
}
