package org.onepf.oms.appstore;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import mp.*;
import org.onepf.oms.Appstore;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.DefaultAppstore;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.googleUtils.IabHelper;

import java.lang.reflect.Field;

/**
 * Created by akarimova on 23.12.13.
 */

/**
 * Fortumo, an international mobile payment provider, is not actually an app store.
 * This class was made to provide in-app purchasing compatibility with other, "real", stores.
 */
public class FortumoStore extends DefaultAppstore {

    private static final int SKU_DESC_ARRAY_LENGTH = 5;
    private static final int INDEX_SKU_NAME = 0;
    private static final int INDEX_SKU_TYPE = 1;
    private static final int INDEX_SKU_CONSUMABLE = 2;
    private static final int INDEX_SKU_SERVICE_ID = 3;
    private static final int INDEX_SKU_APP_SECRET = 4;

    static final String SHARED_PREFS_FORTUMO = "shared_prefs_fortumo";
    static final String SHARED_PREFS_FORTUMO_CONSUMABLE_SKUS = "shared_prefs_fortumo_consumable_skus";
    static final String SHARED_PREFS_PAYMENT_TO_HANDLE = "shared_prefs_payment_to_handle";

    private Context context;
    private FortumoBillingService billingService;

    public FortumoStore(Context context) {
        this.context = context;
    }

    @Override
    public boolean isPackageInstaller(String packageName) {
        //Fortumo is not an app. It can't be an installer.
        return false;
    }

    @Override
    public boolean isBillingAvailable(String packageName) {
        //SMS support is required to make payments
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return false;
        //check whether any Fortumo-specific skus are declared
        if (OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO).isEmpty()) return false;
        return true;
    }

    @Override
    public int getPackageVersion(String packageName) {
        return Appstore.PACKAGE_VERSION_UNDEFINED;
    }

    @Override
    public String getAppstoreName() {
        return OpenIabHelper.NAME_FORTUMO;
    }

    @Override
    public AppstoreInAppBillingService getInAppBillingService() {
        if (billingService == null) {
            billingService = new FortumoBillingService(context);
        }
        return billingService;
    }


    static void startPaymentActivityForResult(Activity activity, int requestCode, PaymentRequest paymentRequest) {
        if (!MpUtils.isPaymentBroadcastEnabled(activity)) {
            try {
                Class permissionClass = Class.forName(activity.getPackageName() + ".Manifest$permission");
                Field paymentBroadcastPermission = permissionClass.getField("PAYMENT_BROADCAST_PERMISSION");
                String permissionString = (String) paymentBroadcastPermission.get(null);
                MpUtils.enablePaymentBroadcast(activity, permissionString);
            } catch (Exception e) {
                throwFortumoNotConfiguredException("PAYMENT_BROADCAST_PERMISSION is NOT declared.");
            }
        }

        Intent localIntent = paymentRequest.toIntent(activity);
        activity.startActivityForResult(localIntent, requestCode);
    }

    private static void checkPermission(Context context, String paramString) {
        if (context.checkCallingOrSelfPermission(paramString) != PackageManager.PERMISSION_GRANTED)
            throwFortumoNotConfiguredException(String.format("Required permission \"%s\" is NOT granted.", paramString));
    }


    public static String sku(String skuName, String skuType, boolean consumable, String serviceId, String appSecret) {
        if (skuName.trim().indexOf(',') != -1) {
            throw new IllegalStateException("Can't create a Fortumo SKU: SKU name contains \',\' .");
        }
        if (!skuType.trim().equals(IabHelper.ITEM_TYPE_INAPP) && !skuType.trim().equals(IabHelper.ITEM_TYPE_SUBS)) {
            throw new IllegalStateException("Can't create a Fortumo SKU: unknown SKU type: " + skuType);
        }
        if (serviceId.trim().length() % 4 != 0) {
            throw new IllegalStateException("Can't create a Fortumo SKU: service id is not base64 encoded string.");
        }
        if (appSecret.trim().length() % 4 != 0) {
            throw new IllegalStateException("Can't create a Fortumo SKU: app secret is not base64 encoded string.");
        }
        return String.format("%s,%s,%s,%s,%s", skuName.trim(), skuType.trim(), consumable, serviceId.trim(), appSecret.trim());
    }

    private static String[] splitOpenSkuDescription(String openSkuDescription) {
        String[] splitArray = openSkuDescription.split(",");
        if (splitArray.length != SKU_DESC_ARRAY_LENGTH) {
            throw new IllegalStateException("Fortumo-specific SKU must contain the following elements:\nSKU name: string,\nSKU type: subs or inapp,\n" +
                    "Consumable: true or false,\nService ID: base64 string,\nIn-application secret: base64 string");
        }
        return splitArray;
    }

    public static String getSkuName(String openSkuDescription) {
        return splitOpenSkuDescription(openSkuDescription)[INDEX_SKU_NAME];
    }

    public static String getSkuType(String openSkuDescription) {
        return splitOpenSkuDescription(openSkuDescription)[INDEX_SKU_TYPE];
    }

    public static boolean isSkuConsumable(String openSkuDescription) {
        return Boolean.parseBoolean(splitOpenSkuDescription(openSkuDescription)[INDEX_SKU_CONSUMABLE]);
    }

    public static String getSkuServiceId(String openSkuDescription) {
        return splitOpenSkuDescription(openSkuDescription)[INDEX_SKU_SERVICE_ID];
    }

    public static String getSkuAppSecret(String openSkuDescription) {
        return splitOpenSkuDescription(openSkuDescription)[INDEX_SKU_APP_SECRET];
    }


    /**
     * Checks for the presence of permissions and components' declarations that are required to support Fortumo billing.<br>
     * Full Example of AndroidManifest.xml:<br>
     * <pre>
     * {@code
     * <!-- Permissions -->
     * <uses-permission android:name="android.permission.INTERNET"/>
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>"
     * <uses-permission android:name="android.permission.READ_PHONE_STATE"/>"
     * <uses-permission android:name="android.permission.RECEIVE_SMS"/>"
     * <uses-permission android:name="android.permission.SEND_SMS"/>"
     *   <!-- Define your own permission to protect payment broadcast -->
     *   <permission android:name="com.your.domain.PAYMENT_BROADCAST_PERMISSION"
     *                android:label="Read payment status"
     *                android:protectionLevel="signature"/>
     *   <!-- "signature" permission granted automatically by system, without notifying user. -->
     *   <uses-permission android:name="com.your.domain.PAYMENT_BROADCAST_PERMISSION"/>
     *   <application android:icon="@drawable/ic_launcher" android:label="@string/app_name">
     * <!-- Declare these objects, this is part of Fortumo SDK,
     * and should not be called directly -->
     * <receiver android:name="mp.MpSMSReceiver">
     * <intent-filter>
     * <action android:name="android.provider.Telephony.SMS_RECEIVED" />
     * </intent-filter>
     * </receiver>
     * <service android:name="mp.MpService" />
     * <service android:name="mp.StatusUpdateService" />
     * <activity android:name="mp.MpActivity"
     * android:theme="@android:style/Theme.Translucent.NoTitleBar"
     * android:configChanges="orientation|keyboardHidden|screenSize" />
     *
     * <!-- Declare OpenIAB BroadcastReceiver to track payment status,
     * should be protected by "signature" permission -->
     * <receiver android:name="org.onepf.oms.appstore.FortumoPaymentReceiver"
     * android:permission="com.your.domain.PAYMENT_BROADCAST_PERMISSION">
     * <intent-filter>
     * <action android:name="mp.info.PAYMENT_STATUS_CHANGED" />
     * </intent-filter>
     * </receiver>
     *
     * <!-- Other application objects -->
     * <activity android:label="@string/app_name" android:name=".YourActivity">
     * <intent-filter>
     * <action android:name="android.intent.action.MAIN" />
     * <category android:name="android.intent.category.LAUNCHER" />
     * </intent-filter>
     * </activity>
     * ...
     * }
     * </pre>
     */
    public static void checkManifest(Context context) {
        checkPermission(context, "android.permission.INTERNET");
        checkPermission(context, "android.permission.ACCESS_NETWORK_STATE");
        checkPermission(context, "android.permission.READ_PHONE_STATE");
        checkPermission(context, "android.permission.RECEIVE_SMS");
        checkPermission(context, "android.permission.SEND_SMS");

        String appDeclaredPermission = null;
        try {
            Class permissionClass = Class.forName(context.getPackageName() + ".Manifest$permission");
            Field paymentBroadcastPermission = permissionClass.getField("PAYMENT_BROADCAST_PERMISSION");
            appDeclaredPermission = (String) paymentBroadcastPermission.get(null);
        } catch (Exception ignored) {
        }
        if (TextUtils.isEmpty(appDeclaredPermission)) {
            throwFortumoNotConfiguredException("PAYMENT_BROADCAST_PERMISSION is NOT declared.");
        }

        Intent paymentActivityIntent = new Intent();
        paymentActivityIntent.setClassName(context.getPackageName(), MpActivity.class.getName());
        if (context.getPackageManager().resolveActivity(paymentActivityIntent, 0) == null) {
            throwFortumoNotConfiguredException("mp.MpActivity is NOT declared.");
        }

        Intent mpServerIntent = new Intent();
        mpServerIntent.setClassName(context.getPackageName(), MpService.class.getName());
        if (context.getPackageManager().resolveService(mpServerIntent, 0) == null) {
            throwFortumoNotConfiguredException("mp.MpService is NOT declared.");
        }

        Intent statusUpdateServiceIntent = new Intent();
        statusUpdateServiceIntent.setClassName(context.getPackageName(), StatusUpdateService.class.getName());
        if (context.getPackageManager().resolveService(statusUpdateServiceIntent, 0) == null) {
            throwFortumoNotConfiguredException("mp.StatusUpdateService is NOT declared.");
        }

        try {
            context.getPackageManager().getReceiverInfo(new ComponentName(context.getPackageName(), FortumoPaymentReceiver.class.getName()), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throwFortumoNotConfiguredException("org.onepf.oms.appstore.FortumoPaymentReceiver is NOT declared.");
        }
    }

    private static void throwFortumoNotConfiguredException(String itemDescription) {
        throw new IllegalStateException(itemDescription + "\nTo support Fortumo your AndroidManifest.xml must contain:\n" +
                " <!-- Permissions -->\n" +
                "  <uses-permission android:name=\"android.permission.INTERNET\" />\n" +
                "  <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n" +
                "  <uses-permission android:name=\"android.permission.READ_PHONE_STATE\" />\n" +
                "  <uses-permission android:name=\"android.permission.RECEIVE_SMS\" />\n" +
                "  <uses-permission android:name=\"android.permission.SEND_SMS\" />\n" +
                "\n" +
                "  <!-- Define your own permission to protect payment broadcast -->\n" +
                "  <permission android:name=\"com.your.domain.PAYMENT_BROADCAST_PERMISSION\" \n" +
                "               android:label=\"Read payment status\" \n" +
                "               android:protectionLevel=\"signature\" />\n" +
                "  <!-- \"signature\" permission granted automatically by system, without notifying user. -->\n" +
                "  <uses-permission android:name=\"com.your.domain.PAYMENT_BROADCAST_PERMISSION\" />\n" +
                "\n" +
                "  <application android:icon=\"@drawable/ic_launcher\" android:label=\"@string/app_name\">\n" +
                "    <!-- Declare these objects, this is part of Fortumo SDK,\n" +
                "    and should not be called directly -->\n" +
                "    <receiver android:name=\"mp.MpSMSReceiver\">\n" +
                "      <intent-filter>\n" +
                "      <action android:name=\"android.provider.Telephony.SMS_RECEIVED\" />\n" +
                "    </intent-filter>\n" +
                "  </receiver>\n" +
                "  <service android:name=\"mp.MpService\" />\n" +
                "  <service android:name=\"mp.StatusUpdateService\" />\n" +
                "  <activity android:name=\"mp.MpActivity\" \n" +
                "             android:theme=\"@android:style/Theme.Translucent.NoTitleBar\"\n" +
                "             android:configChanges=\"orientation|keyboardHidden|screenSize\" />\n" +
                "\n" +
                "    <!-- add OpenIAB BroadcastReceiver to track payment status,\n" +
                "    should be protected by \"signature\" permission -->\n" +
                "  <receiver android:name=\"org.onepf.oms.appstore.FortumoPaymentReceiver\" \n" +
                "            android:permission=\"com.your.domain.PAYMENT_BROADCAST_PERMISSION\">\n" +
                "    <intent-filter>\n" +
                "      <action android:name=\"mp.info.PAYMENT_STATUS_CHANGED\" />\n" +
                "    </intent-filter>\n" +
                "  </receiver>\n" +
                "\n" +
                "  <!-- Other application objects -->\n" +
                "  <activity android:label=\"@string/app_name\" android:name=\".YourActivity\">\n" +
                "    <intent-filter>\n" +
                "      <action android:name=\"android.intent.action.MAIN\" />\n" +
                "      <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "    </intent-filter>\n" +
                "  </activity>\n" +
                "  ...");
    }
}
