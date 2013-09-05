package org.onepf.oms.appstore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.appstore.googleUtils.IabException;
import org.onepf.oms.appstore.googleUtils.IabHelper.OnIabPurchaseFinishedListener;
import org.onepf.oms.appstore.googleUtils.IabHelper.OnIabSetupFinishedListener;
import org.onepf.oms.appstore.googleUtils.IabHelper;
import org.onepf.oms.appstore.googleUtils.IabResult;
import org.onepf.oms.appstore.googleUtils.Inventory;
import org.onepf.oms.appstore.googleUtils.Purchase;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.sec.android.iap.IAPConnector;

public class SamsungAppsBillingService3 implements AppstoreInAppBillingService {
    private static final String TAG = SamsungAppsBillingService3.class.getSimpleName();

    private static final int HONEYCOMB_MR1 = 12;
    
    // IAP Modes are used for IAPConnector.init() 
    public static final int IAP_MODE_COMMERCIAL = 0;
    public static final int IAP_MODE_TEST_SUCCESS = 1;
    public static final int IAP_MODE_TEST_FAIL = -1;
    
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

    private IAPConnector iapConnector = null;

    Context mContext;
    public boolean mIsBind = false;
    private OnIabSetupFinishedListener setupListener = null;
    
    @Override
    public void startSetup(OnIabSetupFinishedListener listener) {
        
        new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                iapConnector = IAPConnector.Stub.asInterface(service);
                try {
                    Bundle result = iapConnector.init(IAP_MODE_COMMERCIAL);
                } catch (RemoteException e) {
                    
                }
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        
    }
    
    private void onSetupFinished(IabResult result) {
        
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
    }


}