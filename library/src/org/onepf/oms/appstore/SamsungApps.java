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

import android.app.Activity;
import android.util.Log;
import org.onepf.oms.Appstore;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.DefaultAppstore;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.OpenIabHelper.Options;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import org.onepf.oms.appstore.googleUtils.IabException;
import org.onepf.oms.appstore.googleUtils.IabHelper;
import org.onepf.oms.appstore.googleUtils.IabResult;
import org.onepf.oms.appstore.googleUtils.Inventory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * <p>
 * {@link #isPackageInstaller(String)} - there is no known reliable way to understand
 * SamsungApps is installer of Application
 * If you want SamsungApps to be used for purhases specify it in preffered stores by
 * {@link OpenIabHelper#OpenIabHelper(Context, java.util.Map, String[])} </p>
 * <p/>
 * Supported purchase details
 * <pre>
 * PurchaseInfo(type:inapp): {
 *     "orderId"            :TPMTID20131011RUI0515895,    // Samsung's payment id
 *     "packageName"        :org.onepf.trivialdrive,
 *     "productId"          :sku_gas,
 *     "purchaseTime"       :1381508784209,               // time in millis
 *     "purchaseState"      :0,                           // will be always zero
 *     "developerPayload"   :,                            // available only in Purchase which return in OnIabPurchaseFinishedListener and
 *                                                        // in OnConsumeFinishedListener. In other places it's equal empty string
 *     "token"              :3218a5f30dd56ca459b16155a207e8af7b2cfe80a54f2aed846b2bbbd547c400
 * }
 * </pre>
 *
 * @author Ruslan Sayfutdinov
 * @since 10.10.2013
 */
public class SamsungApps extends DefaultAppstore {
    private static final String TAG = SamsungApps.class.getSimpleName();
    private static final long TIMEOUT_BILLING_SUPPORTED = 5;
    private static final int IAP_SIGNATURE_HASHCODE = 0x7a7eaf4b;
    public static final String IAP_PACKAGE_NAME = "com.sec.android.iap";
    public static final String IAP_SERVICE_NAME = "com.sec.android.iap.service.iapService";

    private AppstoreInAppBillingService mBillingService;
    private Activity mActivity;
    private Options mOptions;
    //isSamsungTestMode = true -> always returns Samsung Apps is installer and billing is available
    public static boolean isSamsungTestMode;
    private boolean mDebugMode;
    private Boolean mBillingAvailable;

    public SamsungApps(Activity activity, Options options) {
        this.mActivity = activity;
        this.mOptions = options;
    }

    @Override
    public boolean isPackageInstaller(String packageName) {
        return isSamsungTestMode; // currently there is no reliable way to understand it
    }

    /**
     * @return true if Samsung Apps is installed in the system and inventory contains info about the skus
     */
    @Override
    public boolean isBillingAvailable(final String packageName) {
        if (mBillingAvailable== null) {
            if (isSamsungTestMode) {
                if (mDebugMode) Log.d(TAG, "isBillingAvailable() billing is supported in test mode.");
                return true;
            }
            boolean iapInstalled = true;
            try {
                PackageManager packageManager = mActivity.getPackageManager();
                if (packageManager != null) {
                    packageManager.getApplicationInfo(IAP_PACKAGE_NAME, PackageManager.GET_META_DATA);
                }
            } catch (PackageManager.NameNotFoundException e) {
                iapInstalled = false;
            }
            if (iapInstalled) {
                try {
                    Signature[] signatures = mActivity.getPackageManager().getPackageInfo(IAP_PACKAGE_NAME, PackageManager.GET_SIGNATURES).signatures;
                    if (signatures != null) {
                        if (signatures[0].hashCode() != IAP_SIGNATURE_HASHCODE) {
                            iapInstalled = false;
                        }
                    }
                } catch (Exception e) {
                    iapInstalled = false;
                }
            }
            if (iapInstalled) {
                final Boolean billingAvailable[] = {null};
                final CountDownLatch mainLatch = new CountDownLatch(1);
                final IabResult[] resultFromMainThread = {null};
                getInAppBillingService().startSetup(new IabHelper.OnIabSetupFinishedListener() {
                    @Override
                    public void onIabSetupFinished(final IabResult result) {
                        resultFromMainThread[0] = result;
                        mainLatch.countDown();
                    }
                });
                try {
                    if (mOptions.samsungCertificationEnabled) {
                        mainLatch.await();
                    } else {
                        mainLatch.await(TIMEOUT_BILLING_SUPPORTED, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "isBillingAvailable failed", e);
                }
                if (resultFromMainThread[0] != null && resultFromMainThread[0].isSuccess()) {
                    final CountDownLatch inventoryLatch = new CountDownLatch(1);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (resultFromMainThread[0].isSuccess()) {
                                try {
                                    Inventory inventory = getInAppBillingService().queryInventory(true, OpenIabHelper.getAllStoreSkus(OpenIabHelper.NAME_SAMSUNG), null);
                                    if (inventory.mSkuMap != null && inventory.mSkuMap.size() > 0) {
                                        billingAvailable[0] = true;
                                    }
                                } catch (IabException e) {
                                    Log.e(TAG, "isBillingAvailable() failed", e);
                                } finally {
                                    getInAppBillingService().dispose();
                                    mBillingService = null;
                                }
                            }
                            inventoryLatch.countDown();
                        }
                    }).start();
                    try {
                        if (mOptions.samsungCertificationEnabled) {
                            inventoryLatch.await();
                        } else {
                            inventoryLatch.await(TIMEOUT_BILLING_SUPPORTED, TimeUnit.SECONDS);
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "isBillingAvailable() failed", e);
                    }
                } else {
                    getInAppBillingService().dispose();
                    mBillingService = null;
                }
                return mBillingAvailable = billingAvailable[0] != null;
            } else {
                return mBillingAvailable = false;
            }
        } else {
            return mBillingAvailable;
        }
    }

    @Override
    public int getPackageVersion(String packageName) {
        return Appstore.PACKAGE_VERSION_UNDEFINED;
    }

    @Override
    public AppstoreInAppBillingService getInAppBillingService() {
        if (mBillingService == null) {
            mBillingService = new SamsungAppsBillingService(mActivity, mOptions);
        }
        return mBillingService;
    }

    @Override
    public String getAppstoreName() {
        return OpenIabHelper.NAME_SAMSUNG;
    }
}
