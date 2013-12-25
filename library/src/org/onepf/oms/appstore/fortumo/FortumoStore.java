package org.onepf.oms.appstore.fortumo;

import android.content.Context;
import org.onepf.oms.Appstore;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.DefaultAppstore;
import org.onepf.oms.OpenIabHelper;

/**
 * Created by akarimova on 23.12.13.
 */


public class FortumoStore extends DefaultAppstore {
    private Context context;
    private FortumoBillingService billingService;
    private String sharedPrefName;

    public FortumoStore(Context context, String sharedPrefName) {
        this.context = context;
        this.sharedPrefName = sharedPrefName;
    }

    @Override
    public boolean isPackageInstaller(String packageName) {
        return false;
    }

    @Override
    public boolean isBillingAvailable(String packageName) {
        return OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO).size() > 0;
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
            billingService = new FortumoBillingService(context, sharedPrefName);
        }
        return billingService;
    }
}
