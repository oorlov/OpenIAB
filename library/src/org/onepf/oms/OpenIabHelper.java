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

package org.onepf.oms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.onepf.oms.appstore.AmazonAppstore;
import org.onepf.oms.appstore.GooglePlay;
import org.onepf.oms.appstore.OpenAppstore;
import org.onepf.oms.appstore.SamsungApps;
import org.onepf.oms.appstore.TStore;
import org.onepf.oms.appstore.googleUtils.IabException;
import org.onepf.oms.appstore.googleUtils.IabHelper;
import org.onepf.oms.appstore.googleUtils.IabHelper.OnIabSetupFinishedListener;
import org.onepf.oms.appstore.googleUtils.IabResult;
import org.onepf.oms.appstore.googleUtils.Inventory;
import org.onepf.oms.appstore.googleUtils.Purchase;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * 
 * 
 * @author Boris Minaev, Oleg Orlov
 * @since 16.04.13
 */
public class OpenIabHelper {
    private static final String TAG = OpenIabHelper.class.getSimpleName();
    // Is debug logging enabled?
    private static final boolean mDebugLog = false;
    
    private static final String BIND_INTENT = "org.onepf.oms.openappstore.BIND";
    /** */
    private static final int DISCOVER_TIMEOUT_MS = 5000;
    /** */
    private static final int INVENTORY_CHECK_TIMEOUT_MS = 5000;
    
    private final Context context;
    
    /** selected appstore */
    private Appstore mAppstore;

    /** selected appstore billing service */
    private AppstoreInAppBillingService mAppstoreBillingService;
    
    /** Candidates for billing. If not provided by user than discovered OpenStores + extraStores
     * {@link #discoverOpenStores(Context, List, Map, OnInitListener)}
     */
    private List<Appstore> availableStores;
    private Map<String, String> storeKeys;
    
    /** Developer preferred store names */
    private String[] prefferedStoreNames = new String[] {};

    // Is setup done?
    private boolean mSetupDone = false;

    // Is an asynchronous operation in progress?
    // (only one at a time can be in progress)
    private boolean mAsyncInProgress = false;

    // (for logging/debugging)
    // if mAsyncInProgress == true, what asynchronous operation is in progress?
    private String mAsyncOperation = "";

    // The request code used to launch purchase flow
    int mRequestCode;

    // The item type of the current purchase flow
    String mPurchasingItemType;

    /** Check inventory before appstore election */
    private boolean checkInventory = false;
    
    // Item types
    public static final String ITEM_TYPE_INAPP = "inapp";
    public static final String ITEM_TYPE_SUBS = "subs";

    // Billing response codes
    public static final int BILLING_RESPONSE_RESULT_OK = 0;
    public static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
    public static final int BILLING_RESPONSE_RESULT_ERROR = 6;
        
    public static final String NAME_GOOGLE = "com.google.play";
    public static final String NAME_AMAZON = "com.amazon.apps";
    public static final String NAME_TSTORE = "com.tmobile.store";
    public static final String NAME_SAMSUNG = "com.samsung.apps";

    /** 
     * NOTE: used as sync object in related methods<br>
     * 
     * storeName -> [ ... {app_sku1 -> store_sku1}, ... ]
     */
    private static final Map <String, Map<String, String>> sku2storeSkuMappings = new HashMap<String, Map <String, String>>();

    /** 
     * storeName -> [ ... {store_sku1 -> app_sku1}, ... ]
     */
    private static final Map <String, Map<String, String>> storeSku2skuMappings = new HashMap<String, Map <String, String>>();
        
    /**
     * Map sku and storeSku for particular store.
     * 
     * TODO: returns smth with nice API like:
     * <pre>
     * mapSku(sku).store(GOOGLE_PLAY).to(SKU_MY1_GOOGLE).and(AMAZON_APPS).to(SKU_MY1_AMAZON)
     * or
     * mapStore(AMAZON_APPS).sku(SKU_MY2).to(SKU_MY2_AMAZON).sku(SKU_MY3).to(SKU_MY3_AMAZON)
     * </pre>
     * 
     * @param sku - and
     * @param storeSku - shouldn't duplicate already mapped values
     * @param storeName - @see {@link IOpenAppstore#getAppstoreName()} or {@link #NAME_AMAZON} {@link #NAME_GOOGLE} {@link #NAME_TSTORE}
     */
    public static void mapSku(String sku, String storeName, String storeSku) {
        synchronized (sku2storeSkuMappings) {
            Map<String, String> skuMap = sku2storeSkuMappings.get(storeName);
            if (skuMap == null) {
                skuMap = new HashMap<String, String>();
                sku2storeSkuMappings.put(storeName, skuMap);
            }
            if (skuMap.get(sku) != null) {
                throw new IllegalArgumentException("Already specified sku: " + sku + ", storeSku: " + skuMap.get(sku));
            }
            ;
            Map<String, String> storeSkuMap = storeSku2skuMappings.get(storeName);
            if (storeSkuMap == null) {
                storeSkuMap = new HashMap<String, String>();
                storeSku2skuMappings.put(storeName, storeSkuMap);
            }
            if (storeSkuMap.get(storeSku) != null) {
                throw new IllegalArgumentException("Already specified storeSku: " + storeSku + ", sku: " + storeSkuMap.get(storeSku));
            }
            skuMap.put(sku, storeSku);
            storeSkuMap.put(storeSku, sku);
        }
    }
    
    public static String getStoreSku(final String appstoreName, String sku) {
        synchronized (sku2storeSkuMappings) {
            String currentStoreSku = sku;
            Map<String, String> skuMap = sku2storeSkuMappings.get(appstoreName);
            if (skuMap != null && skuMap.get(sku) != null) {
                currentStoreSku = skuMap.get(sku);
                Log.d(TAG, "getStoreSku() using mapping for sku: " + sku + " -> " + currentStoreSku);
            }
            return currentStoreSku;
        }
    }
    
    public static String getSku(final String appstoreName, String storeSku) {
        synchronized (sku2storeSkuMappings) {
            String sku = storeSku;
            Map<String, String> skuMap = storeSku2skuMappings.get(appstoreName);
            if (skuMap != null && skuMap.get(sku) != null) {
                sku = skuMap.get(sku);
                Log.d(TAG, "getSku() restore sku from storeSku: " + storeSku + " -> " + sku);
            }
            return sku;
        }
    }

    public OpenIabHelper(Context context, Map<String, String> storeKeys) {
        this(context, storeKeys, null);
    }
    
    /**
     * @param storeKeys - map [ storeName -> publicKey ]
     * @param prefferedStoreNames - will be used if package installer cannot be found
     */
    public OpenIabHelper(Context context, Map<String, String> storeKeys, String[] prefferedStores) {
        this(context, storeKeys, prefferedStores, null);
    }
    /**
     * @param storeKeys - map [ storeName -> publicKey ]
     * @param prefferedStoreNames - will be used if package installer cannot be found
     * @param availableStores - exact list of stores to participate in store election
     */
    public OpenIabHelper(Context context, Map<String, String> storeKeys, String[] prefferedStores, Appstore[] availableStores) {
        this.context = context;
        this.storeKeys = storeKeys;
        this.prefferedStoreNames = prefferedStores != null ? prefferedStores : this.prefferedStoreNames;
        
        this.availableStores = availableStores != null ? new ArrayList<Appstore>(Arrays.asList(availableStores)) : null;
    }

    /**
     *  Discover available stores and select the best billing service. Calls listener when service is found
     *  TODO: run in separate thread. If there is no openstores discovered it proceed in the same thread (i.e. main-thread)   
     */
    public void startSetup(final IabHelper.OnIabSetupFinishedListener listener) {
        new Thread(new Runnable() {
            public void run() {
                List<Appstore> candidates = new ArrayList<Appstore>(); 
                // if appstores are not specified by user - look up for all available stores
                if (availableStores == null) {
                    candidates.addAll(discoverOpenStores(context, null, storeKeys));
                    candidates.addAll(Arrays.asList(new Appstore[] {
                            new GooglePlay(context, storeKeys.get(OpenIabHelper.NAME_GOOGLE))
                            ,   new AmazonAppstore(context)
                            ,   new SamsungApps(context, storeKeys.get(OpenIabHelper.NAME_SAMSUNG))
                            ,   new TStore(context, storeKeys.get(OpenIabHelper.NAME_TSTORE))
                    }));
                } else {
                    candidates.addAll(availableStores);
                }
                checkAndSelectBillingService(candidates, checkInventory, listener);
            }
        }, "openiab-setup").start();
    }

    /**
     * Discover all OpenStore services, checks them and build {@link #availableStores} list<br>.
     * Time is limited by 5 seconds  
     * 
     * @param appstores - discovered OpenStores will be added here. Must be not null
     * @param listener - called back when all OpenStores collected and analyzed
     */
    public static List<Appstore> discoverOpenStores(final Context context, final List<Appstore> dest, final Map<String, String> storeKeys) {
        PackageManager packageManager = context.getPackageManager();
        final Intent intentAppstoreServices = new Intent(BIND_INTENT);
        List<ResolveInfo> infoList = packageManager.queryIntentServices(intentAppstoreServices, 0);
        final List<Appstore> result = dest != null ? dest : new ArrayList<Appstore>(infoList.size());

        final CountDownLatch storesToCheck = new CountDownLatch(infoList.size());
        for (ResolveInfo info : infoList) {
            String packageName = info.serviceInfo.packageName;
            String name = info.serviceInfo.name;
            Intent intentAppstore = new Intent(intentAppstoreServices);
            intentAppstore.setClassName(packageName, name);
            context.bindService(intentAppstore, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.d(TAG, "discoverOpenStores() appstoresService connected for component: " + name.flattenToShortString());
                    IOpenAppstore openAppstoreService = IOpenAppstore.Stub.asInterface(service);

                    try {
                        String appstoreName = openAppstoreService.getAppstoreName();
                        Intent billingIntent = openAppstoreService.getBillingServiceIntent();
                        if (appstoreName == null) { // no name - no service
                            Log.e(TAG, "discoverOpenStores() Appstore doesn't have name. Skipped. ComponentName: " + name);
                        } else if (billingIntent == null) { // don't handle stores without billing support
                            Log.d(TAG, "discoverOpenStores(): billing is not supported by store: " + name);
                        } else {
                            String publicKey = storeKeys.get(appstoreName);
                            final OpenAppstore openAppstore = new OpenAppstore(context, openAppstoreService, billingIntent, publicKey);
                            Log.d(TAG, "discoverOpenStores() add new open store by type: " + openAppstore.getAppstoreName());
                            synchronized (result) {
                                if (result.contains(openAppstore) == false) {
                                    result.add(openAppstore);
                                }
                            }
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "discoverOpenStores() ComponentName: " + name, e);
                    }
                    storesToCheck.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.d(TAG, "onServiceDisconnected() appstoresService disconnected for component: " + name.flattenToShortString());
                    //Nothing to do here
                }
            }, Context.BIND_AUTO_CREATE);
        }
        try {
            storesToCheck.await(DISCOVER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted: discovering OpenStores. ", e);
        }
        return result;
    }

    
    /**
     * Checks appstores for non-empty inventory   
     * @param listener
     */
    protected void checkAndSelectBillingService(final List<Appstore> availableStores, boolean checkInventory, final IabHelper.OnIabSetupFinishedListener listener) {
        if (checkInventory) {
            String packageName = context.getPackageName();
            // candidates:
            Map<String, Appstore> candidates = new HashMap<String, Appstore>();
            final ArrayList<Appstore> equippedStores = new ArrayList<Appstore>();
            for (Appstore appstore : availableStores) {
                if (appstore.isBillingAvailable(packageName)) {
                    candidates.put(appstore.getAppstoreName(), appstore);
                }
            }
            final CountDownLatch inventoryRemains = new CountDownLatch(candidates.size()); 
            for (Map.Entry<String, Appstore> entry : candidates.entrySet()) {
                final Appstore appstore = entry.getValue();
                final AppstoreInAppBillingService billingService = entry.getValue().getInAppBillingService();
                billingService.startSetup(new OnIabSetupFinishedListener() {
                    public void onIabSetupFinished(IabResult result) {
                        queryInventoryAsyncInner(false, null, new IabHelper.QueryInventoryFinishedListener() {
                            @Override
                            public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                                if (!result.isSuccess()) {
                                    Log.e(TAG, "inventoryCheck failed ");
                                } else if (inv.getAllPurchases().size() > 0) {
                                    synchronized (equippedStores) {
                                        equippedStores.add(appstore);
                                    }
                                }
                                inventoryRemains.countDown();
                            }
                        });
                    }
                });
            }
            try {
                inventoryRemains.await(INVENTORY_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "selectBillingService()  inventory check is failed. candidates: " + candidates.size() 
                        + ", inventory remains: " + inventoryRemains.getCount() , e);
            }
            if (equippedStores.size() > 0) {
                equippedStores.get(new Random().nextInt(equippedStores.size())).getInAppBillingService();
                listener.onIabSetupFinished(new IabResult(BILLING_RESPONSE_RESULT_OK, "Successful with existing inventory"));
                return;
            }
        }
        // otherwise use old approach
        mAppstore = selectBillingService(availableStores);
        if (mAppstore == null) {
            IabResult iabResult = new IabResult(BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE, "Billing isn't supported");
            listener.onIabSetupFinished(iabResult);
            return;
        }
        
        mAppstoreBillingService = mAppstore.getInAppBillingService(); 
        mAppstoreBillingService.startSetup(new OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                mSetupDone = true;
                listener.onIabSetupFinished(result);
            }
        });
    }
    
    /**
     * Lookup for requested service in store based on isPackageInstaller() & isBillingAvailable()
     * <p>
     * Scenario:
     * <li>
     * - look for installer: if exists and supports billing service - we done <li>  
     * - rest of stores who support billing considered as candidates<p><li>
     * 
     * - find candidate according to [prefferedStoreNames]. if found - we done<p><li>
     * 
     * - select candidate randomly from 3 groups based on published package version<li> 
     *   - published version == app.versionCode<li>
     *   - published version  > app.versionCode<li>
     *   - published version < app.versionCode
     * 
     */
    protected Appstore selectBillingService(final List<Appstore> availableStores) {
        String packageName = context.getPackageName();
        // candidates:
        Map<String, Appstore> candidates = new HashMap<String, Appstore>();
        //
        for (Appstore appstore : availableStores) {
            if (appstore.isBillingAvailable(packageName)) {
                candidates.put(appstore.getAppstoreName(), appstore);
            } else {
                continue; // for billing we cannot select store without billing
            }
            if (appstore.isPackageInstaller(packageName)) {
                return appstore;
            }
        }
        if (candidates.size() == 0) return null;
        
        // lookup for developer preffered stores
        for (int i = 0; i < prefferedStoreNames.length; i++) {
            Appstore candidate = candidates.get(prefferedStoreNames[i]);
            if (candidate != null) {
                return candidate;
            }
        }
        // nothing found. select something that matches package version
        int versionCode = Appstore.PACKAGE_VERSION_UNDEFINED; 
        try {
            versionCode = context.getPackageManager().getPackageInfo(packageName, 0).versionCode;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Are we installed?", e);
        }
        List<Appstore> sameVersion = new ArrayList<Appstore>();
        List<Appstore> higherVersion = new ArrayList<Appstore>();
        for (Appstore candidate : candidates.values()) {
            final int storeVersion = candidate.getPackageVersion(packageName);
            if (storeVersion == versionCode) {
                 sameVersion.add(candidate);
            } else if (storeVersion > versionCode) {
                higherVersion.add(candidate);
            }
        }
        // use random if found stores with same version of package  
        if (sameVersion.size() > 0) {
            return sameVersion.get(new Random().nextInt(sameVersion.size()));
        } else if (higherVersion.size() > 0) {  // or one of higher version
            return higherVersion.get(new Random().nextInt(higherVersion.size()));
        } else {                                // ok, return no matter what
            return new ArrayList<Appstore>(candidates.values()).get(new Random().nextInt(candidates.size())); 
        }
    }
    
    public void dispose() {
        logDebug("Disposing.");
        checkSetupDone("dispose");
        mAppstoreBillingService.dispose();
    }

    public boolean subscriptionsSupported() {
        // TODO: implement this
        return true;
    }

    public void launchPurchaseFlow(Activity act, String sku, int requestCode, IabHelper.OnIabPurchaseFinishedListener listener) {
        launchPurchaseFlow(act, sku, requestCode, listener, "");
    }

    public void launchPurchaseFlow(Activity act, String sku, int requestCode,
                                   IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {
        launchPurchaseFlow(act, sku, ITEM_TYPE_INAPP, requestCode, listener, extraData);
    }

    public void launchSubscriptionPurchaseFlow(Activity act, String sku, int requestCode,
                                               IabHelper.OnIabPurchaseFinishedListener listener) {
        launchSubscriptionPurchaseFlow(act, sku, requestCode, listener, "");
    }

    public void launchSubscriptionPurchaseFlow(Activity act, String sku, int requestCode,
                                               IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {
        launchPurchaseFlow(act, sku, ITEM_TYPE_SUBS, requestCode, listener, extraData);
    }

    public void launchPurchaseFlow(Activity act, String sku, String itemType, int requestCode,
                                   IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {
        checkSetupDone("launchPurchaseFlow");
        String storeSku = getStoreSku(mAppstore.getAppstoreName(), sku);
        mAppstoreBillingService.launchPurchaseFlow(act, storeSku, itemType, requestCode, listener, extraData);
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        return mAppstoreBillingService.handleActivityResult(requestCode, resultCode, data);
    }

    public Inventory queryInventory(boolean querySkuDetails, List<String> moreSkus) throws IabException {
        return queryInventory(querySkuDetails, moreSkus, null);
    }

    /**
     * 
     * 
     * @param querySkuDetails
     * @param moreItemSkus
     * @param moreSubsSkus - list of subscription skus to query regardless of ownership
     */
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus) throws IabException {
        checkSetupDone("queryInventory");

        List<String> moreItemStoreSkus = null;
        if (moreItemSkus != null) {
            moreItemStoreSkus = new ArrayList<String>();
            for (String sku : moreItemSkus) {
                String storeSku = getStoreSku(mAppstore.getAppstoreName(), sku);
                moreItemStoreSkus.add(storeSku);
            }
        }
        List<String> moreSubsStoreSkus = null;
        if (moreSubsSkus != null) {
            moreSubsStoreSkus = new ArrayList<String>();
            for (String sku : moreSubsSkus) {
                String storeSku = getStoreSku(mAppstore.getAppstoreName(), sku);
                moreSubsStoreSkus.add(storeSku);
            }
        }
        return mAppstoreBillingService.queryInventory(querySkuDetails, moreItemStoreSkus, moreSubsStoreSkus);
    }

    public void queryInventoryAsync(final boolean querySkuDetails, final List<String> moreSkus, final IabHelper.QueryInventoryFinishedListener listener) {
        checkSetupDone("queryInventory");
        flagStartAsync("refresh inventory");
        queryInventoryAsyncInner(querySkuDetails, moreSkus, listener);
    }

    private void queryInventoryAsyncInner(final boolean querySkuDetails, final List<String> moreSkus, final IabHelper.QueryInventoryFinishedListener listener) {
        final Handler handler = new Handler();
        (new Thread(new Runnable() {
            public void run() {
                IabResult result = new IabResult(BILLING_RESPONSE_RESULT_OK, "Inventory refresh successful.");
                Inventory inv = null;
                try {
                    inv = queryInventory(querySkuDetails, moreSkus);
                } catch (IabException ex) {
                    result = ex.getResult();
                }

                flagEndAsync();

                final IabResult result_f = result;
                final Inventory inv_f = inv;
                handler.post(new Runnable() {
                    public void run() {
                        listener.onQueryInventoryFinished(result_f, inv_f);
                    }
                });
            }
        })).start();
    }

    public void queryInventoryAsync(IabHelper.QueryInventoryFinishedListener listener) {
        queryInventoryAsync(true, null, listener);
    }

    public void queryInventoryAsync(boolean querySkuDetails, IabHelper.QueryInventoryFinishedListener listener) {
        queryInventoryAsync(querySkuDetails, null, listener);
    }

    public void consume(Purchase itemInfo) throws IabException {
        checkSetupDone("consume");
        Purchase purchaseStoreSku = (Purchase) itemInfo.clone(); // TODO: use Purchase.getStoreSku()
        purchaseStoreSku.setSku(getStoreSku(mAppstore.getAppstoreName(), itemInfo.getSku()));
        mAppstoreBillingService.consume(purchaseStoreSku);
    }

    public void consumeAsync(Purchase purchase, IabHelper.OnConsumeFinishedListener listener) {
        List<Purchase> purchases = new ArrayList<Purchase>();
        purchases.add(purchase);
        consumeAsyncInternal(purchases, listener, null);
    }

    public void consumeAsync(List<Purchase> purchases, IabHelper.OnConsumeMultiFinishedListener listener) {
        consumeAsyncInternal(purchases, null, listener);
    }

    void consumeAsyncInternal(final List<Purchase> purchases,
                              final IabHelper.OnConsumeFinishedListener singleListener,
                              final IabHelper.OnConsumeMultiFinishedListener multiListener) {
        final Handler handler = new Handler();
        flagStartAsync("consume");
        (new Thread(new Runnable() {
            public void run() {
                final List<IabResult> results = new ArrayList<IabResult>();
                for (Purchase purchase : purchases) {
                    try {
                        consume(purchase);
                        results.add(new IabResult(BILLING_RESPONSE_RESULT_OK, "Successful consume of sku " + purchase.getSku()));
                    } catch (IabException ex) {
                        results.add(ex.getResult());
                    }
                }

                flagEndAsync();
                if (singleListener != null) {
                    handler.post(new Runnable() {
                        public void run() {
                            singleListener.onConsumeFinished(purchases.get(0), results.get(0));
                        }
                    });
                }
                if (multiListener != null) {
                    handler.post(new Runnable() {
                        public void run() {
                            multiListener.onConsumeMultiFinished(purchases, results);
                        }
                    });
                }
            }
        })).start();
    }

    // Checks that setup was done; if not, throws an exception.
    void checkSetupDone(String operation) {
        if (!mSetupDone) {
            logError("Illegal state for operation (" + operation + "): IAB helper is not set up.");
            throw new IllegalStateException("IAB helper is not set up. Can't perform operation: " + operation);
        }
    }

    void flagStartAsync(String operation) {
        // TODO: why can't be called consume and queryInventory at the same time?
//        if (mAsyncInProgress) {
//            throw new IllegalStateException("Can't start async operation (" +
//                    operation + ") because another async operation(" + mAsyncOperation + ") is in progress.");
//        }
        mAsyncOperation = operation;
        mAsyncInProgress = true;
        logDebug("Starting async operation: " + operation);
    }

    void flagEndAsync() {
        logDebug("Ending async operation: " + mAsyncOperation);
        mAsyncOperation = "";
        mAsyncInProgress = false;
    }

    void logDebug(String msg) {
        if (mDebugLog) Log.d(TAG, msg);
    }

    void logError(String msg) {
        Log.e(TAG, "In-app billing error: " + msg);
    }

    void logWarn(String msg) {
        Log.w(TAG, "In-app billing warning: " + msg);
    }
    
    public interface OnInitListener {
        public void onInitFinished();
    }

    public interface OnOpenIabHelperInitFinished {
        public void onOpenIabHelperInitFinished();
    }

}
