package org.onepf.oms.appstore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.appstore.googleUtils.IabException;
import org.onepf.oms.appstore.googleUtils.IabHelper.OnIabPurchaseFinishedListener;
import org.onepf.oms.appstore.googleUtils.IabHelper.OnIabSetupFinishedListener;
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

public class SamsungAppsBillingService2 implements AppstoreInAppBillingService {
    private static final String TAG = SamsungAppsBillingService2.class.getSimpleName();

    // IAP Signature Hashcode
    // ========================================================================
    public static final int IAP_SIGNATURE_HASHCODE = 0x7a7eaf4b;
    // ========================================================================

    private static final int HONEYCOMB_MR1 = 12;
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

    // IAP NAME
    // ========================================================================
    public static final String IAP_PACKAGE_NAME = "com.sec.android.iap";
    public static final String IAP_SERVICE_NAME = "com.sec.android.iap.service.iapService";
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

    private IAPConnector mIapConnector = null;
    private ServiceConnection mServiceConn = null;

    Context mContext;
    public boolean mIsBind = false;

    // 0 : COMMERCIAL MODE
    // 1 : TEST MODE
    // ========================================================================
    int mMode = 1;

    // ========================================================================

    
    @Override
    public void startSetup(OnIabSetupFinishedListener listener) {
    }

    @Override
    public void launchPurchaseFlow(Activity act, String sku, String itemType, int requestCode, OnIabPurchaseFinishedListener listener, String extraData) {
    }

    @Override
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }

    @Override
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus, List<String> moreSubsSkus) throws IabException {
        return null;
    }

    @Override
    public void consume(Purchase itemInfo) throws IabException {
    }

    /**
     * SamsungIapHelper 생성자 Application의 Context를 사용한다.
     * constructor
     * @param _context
     */
    public SamsungAppsBillingService2(Context _context) {
        mContext = _context.getApplicationContext();
    }

    /**
     * IAP를 운영/개발 모드로 사용할지 결정 TEST MODE : 1, COMMERCIAL MODE : 0 (Default : 1)
     * 
     * @param _mode
     */
    public void setMode(int _mode) {
        mMode = _mode;
    }

    /**
     * SamsungAccount 인증
     * SamsungAccount Authentication
     * @param _activity
     */
    public void startAccountActivity(final Activity _activity) {
        ComponentName com = new ComponentName("com.sec.android.iap", "com.sec.android.iap.activity.AccountActivity");

        Intent intent = new Intent();
        intent.setComponent(com);

        _activity.startActivityForResult(intent, REQUEST_CODE_IS_ACCOUNT_CERTIFICATION);
    }

    /**
     * IAP 설치화면으로 이동
     * go to page of SamsungApps in order to install IAP
     */
    public void installIapPackage(final Activity _activity) {
        Runnable OkBtnRunnable = new Runnable() {
            @Override
            public void run() {
                // SamsungApps의 IAP 주소
                // Link of SamsungApps for IAP install
                // ============================================================
                Uri iapDeepLink = Uri.parse("samsungapps://ProductDetail/com.sec.android.iap");
                // ============================================================

                Intent intent = new Intent();
                intent.setData(iapDeepLink);

                // 허니콤 MR1 이상이면 FLAG_INCLUDE_STOPPED_PACKAGES를 추가한다.
                // If android OS version is more HoneyComb MR1,
                // add flag FLAG_INCLUDE_STOPPED_PACKAGES
                // ============================================================
                if (Build.VERSION.SDK_INT >= HONEYCOMB_MR1) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | FLAG_INCLUDE_STOPPED_PACKAGES);
                }
                // ============================================================
                else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                }

                mContext.startActivity(intent);
            }
        };

        showIapDialog(_activity, _activity.getString(R.string.in_app_purchase), _activity.getString(R.string.msg_iap_is_not_installed), true, OkBtnRunnable);
    }

    public boolean isInstalledIapPackage(Context _context) {
        PackageManager pm = _context.getPackageManager();

        try {
            pm.getApplicationInfo(IAP_PACKAGE_NAME, PackageManager.GET_META_DATA);
            return true;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isValidIapPackage(Context _context) {
        boolean result = true;

        try {
            Signature[] sigs = _context.getPackageManager().getPackageInfo(IAP_PACKAGE_NAME, PackageManager.GET_SIGNATURES).signatures;

            if (sigs[0].hashCode() != SamsungAppsBillingService2.IAP_SIGNATURE_HASHCODE) {
                result = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = false;
        }

        return result;
    }

    /**
     * IAP Interface를 사용하기 위하여 Service Bind 처리를 한다. 
     * 처리결과를 Listener에 반환한다.
     * bind to IAPService
     *  
     * @param _listener The listener that receives notifications
     * when bindIapService method is finished.
     */
    public void bindIapService(final OnIapBindListener _listener) {
        // 이미 바인드된 경우
        // exit If already bound 
        // ====================================================================
        if (true == mIsBind) { return; }
        // ====================================================================

        // Connection to IAP service
        // ====================================================================
        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName _name) {
                Log.d(TAG, "IAP Service Disconnected...");

                mIapConnector = null;
            }

            @Override
            public void onServiceConnected(ComponentName _name, IBinder _service) {
                mIapConnector = IAPConnector.Stub.asInterface(_service);

                if (mIapConnector != null && _listener != null) {
                    mIsBind = true;

                    _listener.onBindIapFinished(IAP_RESPONSE_RESULT_OK);
                } else {
                    mIsBind = false;

                    _listener.onBindIapFinished(IAP_RESPONSE_RESULT_UNAVAILABLE);
                }
            }
        };
        // ====================================================================

        Intent serviceIntent = new Intent(IAP_SERVICE_NAME);

        // bind to IAPService
        // ====================================================================
        mContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
        // ====================================================================
    }

    /**
     * IAP의 init Interface를 호출하여 IAP 초기화 작업을 진행한다.
     * process IAP initialization by calling init() interface in IAPConnector
     * @return ErrorVO
     */
    public ErrorVO init() {
        ErrorVO errorVO = new ErrorVO();

        try {
            Bundle bundle = mIapConnector.init(mMode);

            if (null != bundle) {
                errorVO.setErrorCode(bundle.getInt(SamsungAppsBillingService2.KEY_NAME_STATUS_CODE));

                errorVO.setErrorString(bundle.getString(SamsungAppsBillingService2.KEY_NAME_ERROR_STRING));

                errorVO.setExtraString(bundle.getString(SamsungAppsBillingService2.KEY_NAME_IAP_UPGRADE_URL));
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return errorVO;
    }

    public Bundle getItemList(String _itemGroupId, String _itemType) {
        return getItemList(_itemGroupId, 1, 15, _itemType);
    }

    /**
     * IAP 상품 목록 Interface 를 호출하고 결과를 반환
     * load list of item by calling getItemList() method in IAPConnector
     * 
     * @param _itemGroupId
     * @param _startNum
     * @param _endNum
     * @param _itemType
     * @return Bundle
     */
    public Bundle getItemList(String _itemGroupId, int _startNum, int _endNum, String _itemType) {
        Bundle itemList = null;

        try {
            itemList = mIapConnector.getItemList(mMode, mContext.getPackageName(), _itemGroupId, _startNum, _endNum, _itemType);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return itemList;
    }

    public Bundle getItemsInbox(String _itemGroupId) {
        Date d = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

        return getItemsInbox(_itemGroupId, 1, 15, "20130101", sdf.format(d));
    }

    /**
     * IAP 구매한 상품 목록  Interface 를 호출하고 결과를 반환
     * call getItemInboxList() method in IAPConnector
     * to load List of purchased item
     * 
     * @param _itemGroupId
     * @param _startNum
     * @param _endNum
     * @param _startDate
     * @param _endDate
     * @return Bundle
     */
    public Bundle getItemsInbox(String _itemGroupId, int _startNum, int _endNum, String _startDate, String _endDate) {
        Bundle purchaseItemList = null;

        try {
            purchaseItemList = mIapConnector.getItemsInbox(mContext.getPackageName(), _itemGroupId, _startNum, _endNum, _startDate, _endDate);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return purchaseItemList;
    }

    /**
     * IAP의 결제 Activity 를 호출한다.
     * call PaymentMethodListActivity in IAP in order to process payment
     * @param _activity
     * @param _requestCode
     * @param _itemGroupId
     * @param _itemId
     */
    public void startPurchase(Activity _activity, int _requestCode, String _itemGroupId, String _itemId) {
        try {
            Bundle bundle = new Bundle();
            bundle.putString(SamsungAppsBillingService2.KEY_NAME_THIRD_PARTY_NAME, mContext.getPackageName());

            bundle.putString(SamsungAppsBillingService2.KEY_NAME_ITEM_GROUP_ID, _itemGroupId);

            bundle.putString(SamsungAppsBillingService2.KEY_NAME_ITEM_ID, _itemId);

            ComponentName com = new ComponentName("com.sec.android.iap", "com.sec.android.iap.activity.PaymentMethodListActivity");

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(com);

            intent.putExtras(bundle);

            _activity.startActivityForResult(intent, _requestCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Dialog 를 보여준다.
     * show dialog
     * @param _title
     * @param _message
     */
    public void showIapDialog(final Activity _activity, String _title, String _message, final boolean _finishActivity, final Runnable _onClickRunable) {
        AlertDialog.Builder alert = new AlertDialog.Builder(_activity);

        alert.setTitle(_title);
        alert.setMessage(_message);

        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (null != _onClickRunable) {
                    _onClickRunable.run();
                }

                dialog.dismiss();

                if (true == _finishActivity) {
                    _activity.finish();
                }
            }
        });

        if (true == _finishActivity) {
            alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    _activity.finish();
                }
            });
        }

        alert.show();
    }

    /**
     * IAP 종료시 사용중인 Connecter 종료와 Service를 unbind 시킨다.
     * unbind from IAPService when you are done with activity. 
     */
    public void dispose() {
        if (mServiceConn != null) {
            if (mContext != null) {
                mContext.unbindService(mServiceConn);
            }

            mServiceConn = null;
            mIapConnector = null;
        }
    }

    /**
     * bindIapService 메소스가 완료되었을 때 호출되는 인터페이스 정의
     * Interface definition for a callback to be invoked
     * when bindIapService method has been finished.
     */
    public interface OnIapBindListener {
        /**
         * bindIapService 메소드가 완료되었을 때 호출되는 콜백 메소드
         * Callback method to be invoked
         * when bindIapService() method has been finished.
         * @param result
         */
        public void onBindIapFinished(int result);
    }
}