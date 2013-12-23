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
    private boolean isBillingAvailable = true; //todo get rid of it
    private FortumoBillingService mBillingService;

    public FortumoStore(Context context) {
        this.context = context;
    }

    @Override
    public boolean isPackageInstaller(String packageName) {
        return false;
    }

    @Override
    public boolean isBillingAvailable(String packageName) {
//        return OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_FORTUMO).size() > 0; //wtf?!
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
        if (mBillingService == null) {
            mBillingService = new FortumoBillingService();
        }
        return mBillingService;
    }
}
