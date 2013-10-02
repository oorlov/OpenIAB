/*******************************************************************************
 * Copyright 2013 One Platform Foundation
 *
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *       You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 ******************************************************************************/

package org.onepf.oms.appstore;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import org.onepf.oms.Appstore;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.DefaultAppstore;
import org.onepf.oms.OpenIabHelper;

import android.content.Context;

/**
 * User: Boris Minaev
 * Date: 22.04.13
 * Time: 12:28
 */
public class SamsungApps extends DefaultAppstore {
    private static final String TAG = SamsungApps.class.getSimpleName();

    private static final int IAP_SIGNATURE_HASHCODE = 0x7a7eaf4b;
    public static final String IAP_PACKAGE_NAME = "com.sec.android.iap";
    public static final String IAP_SERVICE_NAME = "com.sec.android.iap.service.iapService";

    private AppstoreInAppBillingService mBillingService;
    private Context mContext;

    // isDebugMode = true -> always returns Samsung Apps is installer
    private final boolean isDebugMode = false;

    public SamsungApps(Context context) {
        mContext = context;
    }

    @Override
    public boolean isPackageInstaller(String packageName) {
        return isDebugMode || isBillingAvailable(packageName);
    }

    /**
     * @return true if Samsung Apps is installed in the system
     */
    @Override
    public boolean isBillingAvailable(String packageName) {
        Intent serviceIntent = new Intent(IAP_SERVICE_NAME);
        boolean iapInstalled = !mContext.getPackageManager().queryIntentServices(serviceIntent, 0).isEmpty();
        if (iapInstalled) {
            try {
                Signature[] signatures = mContext.getPackageManager().getPackageInfo(IAP_PACKAGE_NAME, PackageManager.GET_SIGNATURES).signatures;
                if (signatures[0].hashCode() != IAP_SIGNATURE_HASHCODE) {
                    iapInstalled = false;
                }
            } catch (Exception e) {
                iapInstalled = false;
            }
        }
//        if (!iapInstalled) {
//            Intent intent = new Intent();
//            intent.setData(Uri.parse("samsungapps://ProductDetail/com.sec.android.iap"));
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
//            mContext.startActivity(intent);
//        }
        return isDebugMode || iapInstalled;
    }
    
    @Override
    public int getPackageVersion(String packageName) {
        return Appstore.PACKAGE_VERSION_UNDEFINED;
    }
    
    @Override
    public AppstoreInAppBillingService getInAppBillingService() {
        if (mBillingService == null) {
            mBillingService = new SamsungAppsBillingService(mContext);
        }
        return mBillingService;
    }

    @Override
    public String getAppstoreName() {
        return OpenIabHelper.NAME_SAMSUNG;
    }


}
