package org.onepf.oms.appstore.fortumo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import org.onepf.oms.Appstore;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.DefaultAppstore;
import org.onepf.oms.OpenIabHelper;

/**
 * Fortumo, an international mobile payment provider, is not actually an app store.
 * This class was made to provide in-app purchasing compatibility with other, "real", stores.
 *
 * @author akarimova@onepf.org
 * @since 23.12.13
 */
public class FortumoStore extends DefaultAppstore {
    private static final String TAG = FortumoStore.class.getSimpleName();

    /**
     * Contains information about all in-app products
     */
    public static final String IN_APP_PRODUCTS_FILE_NAME = "inapps_products.xml";

    /**
     * Contains additional information about Fortumo services
     */
    public static final String FORTUMO_DETATILS_FILE_NAME = "fortumo_inapps_details.xml";

    private static boolean isDebugLog() {
        return OpenIabHelper.isDebugLog();
    }

    private Context context;
    private FortumoBillingService billingService;

    public FortumoStore(Context context) {
        this.context = context.getApplicationContext();
    }


    @Override
    public boolean isPackageInstaller(String packageName) {
        //Fortumo is not an app. It can't be an installer.
        return false;
    }

    @Override
    public boolean isBillingAvailable(String packageName) {
        //SMS are required to make payments
        final boolean hasTelephonyFeature = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        if (isDebugLog()) {
            Log.d(TAG, "isBillingAvailable: has FEATURE_TELEPHONY " + hasTelephonyFeature);
        }
        return hasTelephonyFeature;
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
}