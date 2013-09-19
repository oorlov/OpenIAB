/*******************************************************************************
 * Copyright 2013 One Platform Foundation
 *
 *        Licensed under the Apache License, Version 2.0 (the "License");
 *        you may not use this file except in compliance with the License.
 *        You may obtain a copy of the License at
 *
 *            http://www.apache.org/licenses/LICENSE-2.0
 *
 *        Unless required by applicable law or agreed to in writing, software
 *        distributed under the License is distributed on an "AS IS" BASIS,
 *        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *        See the License for the specific language governing permissions and
 *        limitations under the License.
 ******************************************************************************/

package org.onepf.oms.appstore;

import java.text.SimpleDateFormat;
import java.util.*;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.googleUtils.*;
import org.onepf.oms.appstore.googleUtils.IabHelper.OnIabPurchaseFinishedListener;
import org.onepf.oms.appstore.googleUtils.IabHelper.OnIabSetupFinishedListener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.sec.android.iap.IAPConnector;

public class SamsungAppsBillingService implements AppstoreInAppBillingService {
    private static final String TAG = SamsungAppsBillingService.class.getSimpleName();

    private static final int HONEYCOMB_MR1 = 12;
    
    // IAP Modes are used for IAPConnector.init() 
    public static final int IAP_MODE_COMMERCIAL = 0;
    public static final int IAP_MODE_TEST_SUCCESS = 1;
    public static final int IAP_MODE_TEST_FAIL = -1;

    public static final String IAP_SERVICE_NAME = "com.sec.android.iap.service.iapService";
    public static final String ACCOUNT_ACTIVITY_NAME = "com.sec.android.iap.activity.AccountActivity";
    public static final String PAYMENT_ACTIVITY_NAME = "com.sec.android.iap.activity.PaymentMethodListActivity";
    // ========================================================================
    // BILLING RESPONSE CODE
    // ========================================================================
    public static final int IAP_RESPONSE_RESULT_OK = 0;
    public static final int IAP_RESPONSE_RESULT_UNAVAILABLE = 2;
    // ========================================================================
    public static final int FLAG_INCLUDE_STOPPED_PACKAGES = 32;
    // ========================================================================
    // BUNDLE KEY
    // ========================================================================
    public static final String KEY_NAME_THIRD_PARTY_NAME = "THIRD_PARTY_NAME";
    public static final String KEY_NAME_STATUS_CODE = "STATUS_CODE";
    public static final String KEY_NAME_ERROR_STRING = "ERROR_STRING";
    public static final String KEY_NAME_IAP_UPGRADE_URL = "IAP_UPGRADE_URL";
    public static final String KEY_NAME_ITEM_GROUP_ID = "ITEM_GROUP_ID";
    public static final String KEY_NAME_ITEM_ID = "ITEM_ID";
    public static final String KEY_NAME_RESULT_LIST = "RESULT_LIST";
    public static final String KEY_NAME_RESULT_OBJECT = "RESULT_OBJECT";
    // ========================================================================
    // ITEM JSON KEY
    // ========================================================================
    public static final String JSON_KEY_ITEM_ID = "mItemId";
    public static final String JSON_KEY_ITEM_NAME = "mItemName";
    public static final String JSON_KEY_ITEM_DESC = "mItemDesc";
    public static final String JSON_KEY_ITEM_PRICE = "mItemPrice";
    public static final String JSON_KEY_CURRENCY_UNIT = "mCurrencyUnit";
    public static final String JSON_KEY_ITEM_IMAGE_URL = "mItemImageUrl";
    public static final String JSON_KEY_ITEM_DOWNLOAD_URL = "mItemDownloadUrl";
    public static final String JSON_KEY_PURCHASE_DATE = "mPurchaseDate";
    public static final String JSON_KEY_PAYMENT_ID = "mPaymentId";
    public static final String JSON_KEY_TYPE = "mType";
    public static final String JSON_KEY_ITEM_PRICE_STRING = "mItemPriceString";
    // ========================================================================
    // ITEM TYPE
    // ========================================================================
    public static final String ITEM_TYPE_CONSUMABLE = "00";
    public static final String ITEM_TYPE_NON_CONSUMABLE = "01";
    public static final String ITEM_TYPE_SUBSCRIPTION = "02";
    public static final String ITEM_TYPE_ALL = "10";
    // ========================================================================
    // define status code passed to 3rd party application
    // ========================================================================
    final public static int IAP_ERROR_NONE = 0;
    final public static int IAP_PAYMENT_IS_CANCELED = 1;
    final public static int IAP_ERROR_INITIALIZATION = -1000;
    final public static int IAP_ERROR_NEED_APP_UPGRADE = -1001;
    final public static int IAP_ERROR_COMMON = -1002;
    final public static int IAP_ERROR_ALREADY_PURCHASED = -1003;
    final public static int IAP_ERROR_WHILE_RUNNING = -1004;
    final public static int IAP_ERROR_PRODUCT_DOES_NOT_EXIST = -1005;
    final public static int IAP_ERROR_CONFIRM_INBOX = -1006;
    // ========================================================================

    public boolean mIsBind = false;
    private IAPConnector mIapConnector = null;
    private Context mContext;
    private ServiceConnection mServiceConnection;
    private String mItemGroupId;
    private String mPurchasingItemType;

    private OnIabSetupFinishedListener setupListener = null;
    // The listener registered on launchPurchaseFlow, which we have to call back when
    // the purchase finishes
    private OnIabPurchaseFinishedListener mPurchaseListener = null;
    private int mRequestCode;

    public SamsungAppsBillingService(Context context, String groupId) {
        mContext = context;
        mItemGroupId = groupId;
    }

    @Override
    public void startSetup(final OnIabSetupFinishedListener listener) {
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mIapConnector = IAPConnector.Stub.asInterface(service);
                try {
                    Bundle result = mIapConnector.init(IAP_MODE_COMMERCIAL);
                    if (result != null) {
                        int statusCode = result.getInt(KEY_NAME_STATUS_CODE);
                        Log.d(TAG, "status code: " + statusCode);
                        String errorString = result.getString(KEY_NAME_ERROR_STRING);
                        if (statusCode == IAP_ERROR_NONE) {
                            listener.onIabSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_OK, "Setup successful"));
                        } else if (statusCode == IAP_ERROR_NEED_APP_UPGRADE) {
                            listener.onIabSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE, "Samsung IAP package must be upgraded"));
                        } else {
                            listener.onIabSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, "Error: " + errorString));
                        }
                    } else {
                        listener.onIabSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, "Problem while initiate service"));
                    }
                } catch (RemoteException e) {
                    listener.onIabSetupFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, "Can't init service"));
                }
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                mIapConnector = null;
                mServiceConnection = null;
            }
        };
        Intent serviceIntent = new Intent(IAP_SERVICE_NAME);
        mContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
//        ComponentName com = new ComponentName(SamsungApps.IAP_PACKAGE_NAME, ACCOUNT_ACTIVITY_NAME);
//        Intent intent = new Intent();
//        intent.setComponent(com);
//        ((Activity)mContext).startActivityForResult(intent, 1002);
    }
    
    @Override
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus) throws IabException {
        Inventory inventory = new Inventory();
        Date date = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String today = simpleDateFormat.format(date);
        Bundle itemInbox = null;
        try {
            // TODO: change endNum to request all items
            itemInbox = mIapConnector.getItemsInbox(mContext.getPackageName(), mItemGroupId, 1, 100, "20000101", today);
        } catch (RemoteException e) {
            Log.e(TAG, "Samsung getItemsInbox: " + e.getMessage());
        }
        if (itemInbox != null && itemInbox.getInt(KEY_NAME_STATUS_CODE) == IAP_ERROR_NONE) {
            ArrayList<String> items = itemInbox.getStringArrayList(KEY_NAME_RESULT_LIST);
            for (String itemString : items) {
                try {
                    JSONObject item = new JSONObject(itemString);
                    String itemId = item.getString(JSON_KEY_ITEM_ID);
                    String rawType = item.getString(JSON_KEY_TYPE);
                    String itemType = rawType.equals(ITEM_TYPE_SUBSCRIPTION) ? IabHelper.ITEM_TYPE_SUBS : IabHelper.ITEM_TYPE_INAPP;
                    Purchase purchase = new Purchase(OpenIabHelper.NAME_SAMSUNG);
                    purchase.setItemType(itemType);
                    purchase.setSku(OpenIabHelper.getSku(OpenIabHelper.NAME_SAMSUNG, itemId));
                    inventory.addPurchase(purchase);
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parse error: " + e.getMessage());
                }
            }
        }
        if (querySkuDetails) {
            Set<String> queryIds = new HashSet<String>();
            if (moreItemSkus != null) {
                queryIds.addAll(moreItemSkus);
            }
            if (moreSubsSkus != null) {
                queryIds.addAll(moreSubsSkus);
            }
            if (queryIds.size() > 0) {
                Bundle itemList = null;
                try {
                    itemList = mIapConnector.getItemList(IAP_MODE_COMMERCIAL, mContext.getPackageName(), mItemGroupId, 1, 100, ITEM_TYPE_ALL);
                } catch (RemoteException e) {
                    Log.e(TAG, "Samsung getItemList: " + e.getMessage());
                }
                if (itemList != null && itemList.getInt(KEY_NAME_STATUS_CODE) == 0) {
                    ArrayList<String> items = itemList.getStringArrayList(KEY_NAME_RESULT_LIST);
                    for (String itemString : items) {
                        try {
                            JSONObject item = new JSONObject(itemString);
                            String itemId = item.getString(JSON_KEY_ITEM_ID);
                            if (queryIds.contains(itemId)) {
                                Purchase purchase = new Purchase(OpenIabHelper.NAME_SAMSUNG);
                                String rawType = item.getString(JSON_KEY_TYPE);
                                String itemType = rawType.equals(ITEM_TYPE_SUBSCRIPTION) ? IabHelper.ITEM_TYPE_SUBS : IabHelper.ITEM_TYPE_INAPP;
                                purchase.setItemType(itemType);
                                purchase.setSku(itemId);
                                inventory.addPurchase(purchase);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "JSON parse error: " + e.getMessage());
                        }
                    }
                }
            }
        }
        return inventory;
    }

    @Override
    public void launchPurchaseFlow(Activity activity, String sku, String itemType, int requestCode, OnIabPurchaseFinishedListener listener, String extraData) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_NAME_THIRD_PARTY_NAME, activity.getPackageName());
        bundle.putString(KEY_NAME_ITEM_GROUP_ID, mItemGroupId);
        bundle.putString(KEY_NAME_ITEM_ID, sku);
        
        ComponentName cmpName = new ComponentName(SamsungApps.IAP_PACKAGE_NAME, PAYMENT_ACTIVITY_NAME);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(cmpName);
        intent.putExtras(bundle);
        mRequestCode = requestCode;
        mPurchaseListener = listener;
        mPurchasingItemType = itemType;
        Log.d(TAG, "Request code: " + requestCode);
        activity.startActivityForResult(intent, requestCode);
    }

    @Override
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != mRequestCode) {
            return false;
        }
        IabResult result = new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, "Unknown error");
        Purchase purchase = new Purchase(OpenIabHelper.NAME_SAMSUNG);
        if (data != null) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                int statusCode = extras.getInt(KEY_NAME_STATUS_CODE);
                String errorMsg = extras.getString(KEY_NAME_ERROR_STRING);
                String itemId = extras.getString(KEY_NAME_ITEM_ID);
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        if (statusCode == IAP_RESPONSE_RESULT_OK) {
                            result = new IabResult(IabHelper.BILLING_RESPONSE_RESULT_OK, errorMsg);
                        } else {
                            result = new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, errorMsg);
                        }
                        break;
                    case Activity.RESULT_CANCELED:
                        result = new IabResult(IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED, errorMsg);
                        break;
                    default:
                        result = new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, errorMsg);
                        break;
                }
                purchase.setItemType(mPurchasingItemType);
                purchase.setSku(itemId);

            } else {
                result = new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, "Payment was not processed successfully");
            }
        }
        Log.d(TAG, "Samsung result code: " + result.getResponse() + ", msg: " + result.getMessage());
        mPurchaseListener.onIabPurchaseFinished(result, purchase);
        return true;
    }


    @Override
    public void consume(Purchase itemInfo) throws IabException {
        // Nothing to do here
    }

    @Override
    public void dispose() {
        mContext.unbindService(mServiceConnection);
        mServiceConnection = null;
        mIapConnector = null;
    }


}