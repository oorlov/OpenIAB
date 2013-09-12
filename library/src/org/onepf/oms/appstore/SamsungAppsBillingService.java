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

import java.util.List;

import android.util.Log;
import org.onepf.oms.AppstoreInAppBillingService;
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
    public static final String ACCOUNT_ACTIVITY_NAME = "com.sec.android.iap.AccountActivity";
    // BILLING RESPONSE CODE
    // ========================================================================
    public static final int IAP_RESPONSE_RESULT_OK = 0;
    public static final int IAP_RESPONSE_RESULT_UNAVAILABLE = 2;
    // ========================================================================

    public static final int FLAG_INCLUDE_STOPPED_PACKAGES = 32;

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

    // ITEM TYPE
    // ========================================================================
    public static final String ITEM_TYPE_CONSUMABLE = "00";
    public static final String ITEM_TYPE_NON_CONSUMABLE = "01";
    public static final String ITEM_TYPE_SUBSCRIPTION = "02";
    public static final String ITEM_TYPE_ALL = "10";
    // ========================================================================

    // IAP 호출시 onActivityResult 에서 받기 위한 요청 코드
    // define request code for IAPService.
    // ========================================================================
    public static final int REQUEST_CODE_IS_IAP_PAYMENT = 1;
    public static final int REQUEST_CODE_IS_ACCOUNT_CERTIFICATION = 2;
    // ========================================================================

    // 3rd party 에 전달되는 코드 정의
    // define status code passed to 3rd party application 
    // ========================================================================
    /** 처리결과가 성공 */
    final public static int IAP_ERROR_NONE = 0;

    /** 결제 취소일 경우 */
    final public static int IAP_PAYMENT_IS_CANCELED = 1;

    /** initialization 과정중 에러 발생 */
    final public static int IAP_ERROR_INITIALIZATION = -1000;

    /** IAP 업그레이드가 필요함 */
    final public static int IAP_ERROR_NEED_APP_UPGRADE = -1001;

    /** 공통 에러코드 */
    final public static int IAP_ERROR_COMMON = -1002;

    /** NON CONSUMABLE 재구매일 경우 */
    final public static int IAP_ERROR_ALREADY_PURCHASED = -1003;

    /** 결제상세 호출시 Bundle 값 없을 경우 */
    final public static int IAP_ERROR_WHILE_RUNNING = -1004;

    /** 요청한 상품 목록이 없는 경우 */
    final public static int IAP_ERROR_PRODUCT_DOES_NOT_EXIST = -1005;

    /** 결제 결과가 성공은 아니지만 구매되었을 수 있기 때문에
     *  구매한 상품 목록 확인이 필요할 경우 */
    final public static int IAP_ERROR_CONFIRM_INBOX = -1006;
    // ========================================================================

    public boolean mIsBind = false;
    private IAPConnector mIapConnector = null;
    private Context mContext;
    private ServiceConnection mServiceConnection;


    private OnIabSetupFinishedListener setupListener = null;

    public SamsungAppsBillingService(Context context) {
        mContext = context;
    }

    @Override
    public void startSetup(final OnIabSetupFinishedListener listener) {
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mIapConnector = IAPConnector.Stub.asInterface(service);
                boolean success = true;
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
        return null;
    }

    @Override
    public void launchPurchaseFlow(Activity act, String sku, String itemType, int requestCode, OnIabPurchaseFinishedListener listener, String extraData) {
        Bundle bundle = new Bundle();
        bundle.putString("THIRD_PARTY_NAME", act.getPackageName());
        bundle.putString("ITEM_GROUP_ID", "_itemGroupId");
        bundle.putString("ITEN_ID", "_itemId");
        
        ComponentName cmpName = new ComponentName("com.set.android.iap", "com.sec.android.iap.activity.PaymentMethodListActivity");
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(cmpName);
        intent.putExtras(bundle);
        act.startActivityForResult(intent, requestCode);
    }

    @Override
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }


    @Override
    public void consume(Purchase itemInfo) throws IabException {
    }

    @Override
    public void dispose() {
        mContext.unbindService(mServiceConnection);
    }


}