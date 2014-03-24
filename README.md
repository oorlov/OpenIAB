OpenIAB - Open In-App Billing
=====

Uploading Android apps to all the existing Android appstores is a painful process and [AppDF](/onepf/AppDF)
project was designed to make it easier. But what is even more difficult for the developers is
supporting different in-purchase APIs of different appstores. There are five different In-App Purchase APIs
already and this number is increasing. We are going to create an open source library that will wrap
appstore in-app purchase APIs of all the stores and provide an easy way for the developers to develop
their apps/games in a way that one APK will work in all the stores and automatically use right in-app
purchase API under each store. Plus we are going to develop an open in-app billing API that stores
could implement to support all the built APK files using this library.

How To add OpenIAB into your Android app
=====
1. Clone the library ``` git clone https://github.com/onepf/OpenIAB.git``` and add /library as a Library Project.
Or download the latest released jar from https://github.com/onepf/OpenIAB/releases and attach it to the project.

2. Map Google Play SKU ids to Yandex/Amazon/etc SKUs like this:
https://github.com/onepf/OpenIAB/blob/master/samples/trivialdrive/src/org/onepf/trivialdrive/MainActivity.java#L109

3. Instantiate ``` new OpenIabHelper ```  and call ``` helper.startSetup() ```.
When setup is done call  ``` helper.queryInventory() ```
    ```java
      helper = new OpenIabHelper(this, storeKeys);
      helper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
          public void onIabSetupFinished(IabResult result) {
              if (!result.isSuccess()) {
                  complain("Problem setting up in-app billing: " + result);
                  return;
              }
              Log.d(TAG, "Setup successful. Querying inventory.");
                  helper.queryInventoryAsync(mGotInventoryListener);
              }
      });
    ```
https://github.com/onepf/OpenIAB/blob/master/samples/trivialdrive/src/org/onepf/trivialdrive/MainActivity.java#L184

4. Handle the results of ``` helper.queryInventory() ``` in an inventory listener and update UI to show what was purchased
https://github.com/onepf/OpenIAB/blob/master/samples/trivialdrive/src/org/onepf/trivialdrive/MainActivity.java#L210

5. When the user requests purchase of an item, call  ``` helper.launchPurchaseFlow() ```
https://github.com/onepf/OpenIAB/blob/master/samples/trivialdrive/src/org/onepf/trivialdrive/MainActivity.java#282
and handle the results with the listener
https://github.com/onepf/OpenIAB/blob/master/samples/trivialdrive/src/org/onepf/trivialdrive/MainActivity.java#L384

6. If the user has purchased a consumable item, call  ``` helper.consume() ```
to exclude it from the inventory. If the item is not consumed, a store supposes it as non-consumable item and doesn't allow to purchase it one more time. Also it will be returned by ``` helper.queryInventory() ``` next time
https://github.com/onepf/OpenIAB/blob/master/samples/trivialdrive/src/org/onepf/trivialdrive/MainActivity.java#L403

7. Specify keys for different stores like this:
https://github.com/onepf/OpenIAB/blob/master/samples/trivialdrive/src/org/onepf/trivialdrive/MainActivity.java#L164

8. Add the required permissions to the AndroidManifest.xml

    ```xml
    <!--all stores-->
    <uses-permission android:name="android.permission.INTERNET"/>
    <!--Google Play-->
    <uses-permission android:name="com.android.vending.BILLING" />
    <!--Amazon-->
    <uses-permission android:name="com.sec.android.iap.permission.BILLING" />
    <!--Samsung Apps-->
    <uses-permission android:name="com.sec.android.iap.permission.BILLING" />
    <!--Open Store-->
    <uses-permission android:name="org.onepf.openiab.permission.BILLING" />
    <!--T-Store and Fortumo-->
    <uses-permission android:name="android.permission.RECEIVE_SMS"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <!--T-Store-->
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="com.tmoney.vending.INBILLING" />
    <permission android:name="com.tmoney.vending.INBILLING" />
    <!--Fortumo-->
    <uses-permission android:name="android.permission.SEND_SMS" />
    ```

9. Edit your proguard config file

    ```
    # T-Store
    -keep class com.skplanet.dodo.**{*;}
    -keep class com.skplanet.internal.dodo.**{*;}
    -keep class com.skplanet.internal.dodo.dev.**{*;}
    -keep class com.skplanet.internal.dodo.util.**{*;}
    -keep class com.skplanet.pmss.secure.**{*;}
    -keep public class android.net.http.SslError
    -keep public class android.webkit.WebViewClient
    -keep class com.tmoney.aidl.**{*;}
    -dontwarn android.webkit.WebView
    -dontwarn android.net.http.SslError
    -dontwarn android.webkit.WebViewClient
    -keepattributes Signature
    -dontshrink

    # AMAZON
    -dontwarn com.amazon.**
    -keep class com.amazon.** {*;}
    -keepattributes *Annotation*
    -dontoptimize

    # GOOGLE
    -keep class com.android.vending.billing.**

    # SAMSUNG
    -keep class com.sec.android.iap.**
    ```


Support instructions for the stores
===================================

Google Play
-----------
1. Add the corresponding billing permission

    ```xml
    <uses-permission android:name="com.android.vending.BILLING" />>
    ```

2. Provide a public key

    ```java
    Map<String, String> storeKeys = new HashMap<String, String>();
    storeKeys.put(OpenIabHelper.NAME_GOOGLE, googleBase64EncodedPublicKey);
    OpenIabHelper.Options options = new OpenIabHelper.Options();
    options.storeKeys = storeKeys;
    mHelper = new OpenIabHelper(this, options);
    //or
    mHelper = new OpenIabHelper(this, storeKeys);
    ```
    otherwise verify purchases on your server side.


3. In the proguard configuration file

    ```proguard
     # GOOGLE
     -keep class com.android.vending.billing.**
     ```


Amazon
------
1. In the AndroidManifest.xml add the corresponding billing permission

    ```xml
    <uses-permission android:name="com.sec.android.iap.permission.BILLING" />
    ```

    and declare the receiver

    ```xml
    <receiver android:name="com.amazon.inapp.purchasing.ResponseReceiver">
        <intent-filter>
            <action
                android:name="com.amazon.inapp.purchasing.NOTIFY"
                android:permission="com.amazon.inapp.purchasing.Permission.NOTIFY"
            />
        </intent-filter>
    </receiver>
    ```

2. Map the SKUs if required.
Remember, the SKUs must be unique across your Amazon developer account.

    ```java
    OpenIabHelper.mapSku(SKU_PREMIUM, OpenIabHelper.NAME_AMAZON, "org.onepf.trivialdrive.amazon.premium");
    OpenIabHelper.mapSku(SKU_GAS, OpenIabHelper.NAME_AMAZON, "org.onepf.trivialdrive.amazon.gas");
    OpenIabHelper.mapSku(SKU_INFINITE_GAS, OpenIabHelper.NAME_AMAZON, "org.onepf.trivialdrive.amazon.infinite_gas");
    ```

3. In the proguard configuration file add

    ```proguard
     # AMAZON
    -dontwarn com.amazon.**
    -keep class com.amazon.** {*;}
    -keepattributes *Annotation*
    -dontoptimize
    ```

Samsung Apps
------------
1. In the AndroidManifest.xml add the corresponding billing permission

    ```xml
     <uses-permission android:name="com.sec.android.iap.permission.BILLING" />
    ```

2. Map the SKUs if required.
   Remember, Samsung Apps describes an item it terms of Item Group ID and Item ID.

   ```java
   //format "group_id/item_id"
   OpenIabHelper.mapSku(SKU_PREMIUM, OpenIabHelper.NAME_SAMSUNG, "100000100696/000001003746");
   OpenIabHelper.mapSku(SKU_GAS, OpenIabHelper.NAME_SAMSUNG, "100000100696/000001003744");
   OpenIabHelper.mapSku(SKU_INFINITE_GAS, OpenIabHelper.NAME_SAMSUNG, "100000100696/000001003747");
   ```

3. Instantiate ``` new OpenIabHelper ``` using an Activity instance.
   Activity context is required to call  ``` startActivityForResult() ``` for SamsungAccount Activity.

4. In the proguard configuration add

    ```proguard
    # SAMSUNG
    -keep class com.sec.android.iap.**
    ```


Open Store
----------
1. Add the corresponding billing permission

    ```xml
    <uses-permission android:name="org.onepf.openiab.permission.BILLING" />
    ```

2. Provide a public key

    ```java
    Map<String, String> storeKeys = new HashMap<String, String>();
    storeKeys.put(OpenIabHelper.OPEN_STORE_NAME, openStorePublicKey);
    OpenIabHelper.Options options = new OpenIabHelper.Options();
    options.storeKeys = storeKeys;
    mHelper = new OpenIabHelper(this, options);
    //or
    mHelper = new OpenIabHelper(this, storeKeys);
    ```
otherwise verify purchases on your server side.

3. Map the SKUs if required

    ```java
    OpenIabHelper.mapSku(SKU_PREMIUM, OpenIabHelper.OPEN_STORE_NAME, "org.onepf.trivialdrive.openstorename.premium");
    OpenIabHelper.mapSku(SKU_GAS, OpenIabHelper.OPEN_STORE_NAME, "org.onepf.trivialdrive.openstorename.gas");
    OpenIabHelper.mapSku(SKU_INFINITE_GAS, OpenIabHelper.OPEN_STORE_NAME, "org.onepf.trivialdrive.openstorename.infinite_gas");
    ```


T-Store
-------
1. In the AndroidManifest.xml add the following permissions

    ```xml
    <uses-permission android:name="android.permission.RECEIVE_SMS"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.READ_PHONE_STATE"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="com.tmoney.vending.INBILLING" />
    <permission android:name="com.tmoney.vending.INBILLING" />
    ```

    and specify the API version

    ```xml
     <application
                android:icon="@drawable/ic_launcher"
                android:label="@string/app_name"
                android:theme="@style/AppTheme">
            <meta-data
                    android:name="iap:api_version"
                    android:value="13"/>
    ```

2. Map the SKUs, if required

    ```java
    OpenIabHelper.mapSku(SKU_PREMIUM, OpenIabHelper.NAME_TSTORE, "tstore_sku_premium");
    OpenIabHelper.mapSku(SKU_GAS, OpenIabHelper.NAME_TSTORE, "tstore_sku_gas");
    OpenIabHelper.mapSku(SKU_INFINITE_GAS, OpenIabHelper.NAME_TSTORE, "tstore_sku_infinite_gas");
    ```

3. In the proguard config add

    ```proguard
    # TStore
    -keep class com.skplanet.dodo.**{*;}
    -keep class com.skplanet.internal.dodo.**{*;}
    -keep class com.skplanet.internal.dodo.dev.**{*;}
    -keep class com.skplanet.internal.dodo.util.**{*;}
    -keep class com.skplanet.pmss.secure.**{*;}
    -keep public class android.net.http.SslError
    -keep public class android.webkit.WebViewClient
    -keep class com.tmoney.aidl.**{*;}
    -dontwarn android.webkit.WebView
    -dontwarn android.net.http.SslError
    -dontwarn android.webkit.WebViewClient
    -keepattributes Signature
    -dontshrink
    ```

Support instructions for Fortumo carrier billing
================================================

Before start to work with OpenIab library
-----------------------------------------
Create a Fortumo account and add a required number of services. One service corresponds to one price, e.g. for 3 inapps with different prices you should create 3 different services.
You can find all required instructions <a href="http://developers.fortumo.com/in-app-purchasing-on-android/service-setup/">here.</a>

OpenIab setup
-------------
1. Make sure that <a href="https://github.com/onepf/OpenIAB/blob/master/library/libs/FortumoInApp-android-9.1.2-o.jar">FortumoInApp-android-9.1.2-o.jar</a> is attached to the project.

2. In the AndroidManifest.xml add the following permissions

    ```xml
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    ```

   and declare the Fortumo SDK objects

     ```xml
     <!-- Declare these objects, this is part of Fortumo SDK,
         and should not be called directly -->
      <receiver android:name="mp.MpSMSReceiver">
            <intent-filter>
                <action android:name="android.provider.Telephony.SMS_RECEIVED"/>
            </intent-filter>
        </receiver>
        <service android:name="mp.MpService"/>
        <service android:name="mp.StatusUpdateService"/>
        <activity android:name="mp.MpActivity"
                  android:theme="@android:style/Theme.Translucent.NoTitleBar"
                  android:configChanges="orientation|keyboardHidden|screenSize"/>
     ```

4. In the code setup an Options object

    ```java
    OpenIabHelper.Options options = new OpenIabHelper.Options();
    //set supportFortumo flag to true
    options.supportFortumo = true;
    //or
    List<Appstore> storeList = new ArrayList<Appstore>();
    storeList.add(new FortumoStore(this));
    //by the way, you can add other stores object to the list
    options.availableStores = storeList;
    mHelper = new OpenIabHelper(this, options);
    ```

5. Add <a href="https://github.com/onepf/AppDF/blob/xsd-for-inapps/specification/inapp-description.xsd">inapps_products.xml</a> (in-app products description in terms similar to Google Play) and
<a href="https://github.com/onepf/AppDF/blob/xsd-for-inapps/specification/fortumo-products-description.xsd">fortumo_inapps_details.xml</a> (data about your Fortumo services) files to the assets folder.
You can find a sample <a href="https://github.com/onepf/OpenIAB/tree/master/samples/trivialdrive/assets">here.</a>

Support instructions for Fortumo in-app purchasing on NOOK
==========================================================
Quite the same as Support instructions for Fortumo carrier billing.
But in the AndroidManifest.xml add only 2 following permissions

    ```xml
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    ```

Unity Plugin
=====
There is also Unity engine [plugin](unity_plugin) that will simplify integration for C#/JavaScript developers. No need to write any java code.

How OpenIAB Works
=====
1. An Android app developer integrates OpenIAB library in his/her Android code
2. An Android app developer implements in-app purchases using OpenIAB API (which is very close to Google Play IAB API, just few changes in source code will be needed)
3. OpenIAB Lib detects which appstore installed the app
4. OpenIAB Lib redirects in-app purchase calls to the corresponding appstore IAB API (OpenIAB Lib wrapps IAB APIs of severall apstores)
5. All In-App Billing logic is handled by the corresponding appstore, OpenIAB has no code to process in-app purchases and has no UI, it just wrapps In-App Billing APIs of different stores in one library

<img src="http://www.onepf.org/img/openiabdiagram1.png">

<img src="http://www.onepf.org/img/openiabdiagram2.png">

Current Status
=====
We have just started. We are creating a [sample game](/onepf/OpenIAB/tree/master/samples/trivialdrive) that supports in-app billing of all existing appstores that support in-app purchasing. In the same time, we are designing
[Open In-App Billing API](http://www.github.com/onepf/OpenIAB/blob/master/specification/openms_spec_1_0.md) that appstores can use to easily integrate in-app billing functionality.

Basic Principles
=====
* **As close to Google Play In-app Billing API as possible** - we optimize the OpenIAB library by the following parameter "lines on code you need to change in an app that already works in Google Play to make it working in all the appstores"
* **No middle man**
* **Modular architecture** - adding new appstore should be just adding one more class imeplementing a fixed interface
* **One APK file to work in all appstores** - but OpenIAB should have an option to build separate APK files for appstores for developers who want to create APK without any overhead
* **No additional functionality** - OpenIAB does not make in-app billing easier to use, we just make it working in all the appstores with single code

No Middle Man
=====
OpenIAB is an open source library that wraps the already existing IAB APIs as well as an open API that
appstores could implement. It is important to understand that all payments are processes directly by
each store and there is no a middle man staying between the app developers and the appstores.
OpenIAB will not do payments for the appstores. It is just an API how the apps communicate with
appstores to request in-app billing. There is a common open API all the stores can use instead of
each new store implement their own API and developers have to integrate all these different APIs in their apps.

AppStores
=====
The following Android application stores support in-app billing today:
 * [Google Play](https://play.google.com/apps/publish/)
 * [Amazon AppStore](https://developer.amazon.com/welcome.html)
 * [Samsung Apps](http://seller.samsungapps.com/)
 * [SK-Telecom T-Store](http://dev.tstore.co.kr/devpoc/main/main.omp)
 * [NOOK](https://nookdeveloper.barnesandnoble.com/) (via [Fortumo](http://smsfortumo.ru/))

If you know about other Android appstores that support in-app purchasing
please [let us know](http://groups.google.com/group/opf_openiab).

We are working on integrating their IAB APIs in one OpenIAB library. Here is information about
Appstore IAB feature support:
<table>
  <tr>
    <th></th>
    <th><a href="https://play.google.com/apps/publish/">Google Play</a></th>
    <th><a href="https://developer.amazon.com/welcome.html">Amazon AppStore</a></th>
    <th><a href="http://seller.samsungapps.com/">Samsung Apps</a></th>
    <th><a href="http://dev.tstore.co.kr/devpoc/main/main.omp">SK-Telecom T-Store</a></th>
  </tr>
  <tr>
    <td>Link to IAB API description</td>
    <td><a href="http://developer.android.com/google/play/billing/index.html">Google IAB API</a></td>
    <td><a href="https://developer.amazon.com/sdk/in-app-purchasing.html">Amazon IAB API</a></td>
    <td><a href="http://developer.samsung.com/android/tools-sdks/In-App-Purchase-Library">Samsung IAB API</a></td>
    <td><a href="http://dev.tstore.co.kr/devpoc/guide/guideProd.omp#a1_5">T-Store IAB API</a></td>
  </tr>
  <tr>
    <td>Processing code</td>
    <td>Appstore</td>
    <td>Appstore</td>
    <td>Lib</td>
    <td>Lib</td>
  </tr>
  <tr>
    <td>Subscription</td>
    <td>Yes</td>
    <td>Yes</td>
    <td>No</td>
    <td>Yes</td>
  </tr>
  <tr>
    <td>Consumable goods</td>
    <td>Yes</td>
    <td>Yes</td>
    <td>Yes</td>
    <td>Yes</td>
  </tr>
  <tr>
    <td>Non-consumable goods</td>
    <td>Yes</td>
    <td>Yes</td>
    <td>No</td>
    <td>Yes</td>
  </tr>
</table>

How Can I Help?
=====
* If you are an Android app developer check <a href="https://github.com/onepf/OpenIAB/issues?labels=open+tasks&state=open">the list of open tasks</a>, check if any of these tasks is interesting for you, send a message to <a href="http://groups.google.com/group/opf_openiab">OpenIAB mailing list</a> how you want to help. <a href="https://github.com/onepf/OpenIAB">Fork OpenIAB</a> on GitHub.
* If you are an appstore and already support In-App Billing then most probably we are already working on supporting your API in OpenIAB library, and your help is very welcome since you know your API better than anyone else! Just contact us by <a href="http://groups.google.com/group/opf_openiab">joining OpenIAB mailing list</a>.
* If you are an appstore and do not support In-App Billing yet but plan to support it then we will be glad working with your on creating a common OpenIAB API and API. <a href="http://groups.google.com/group/opf_openiab">Join OpenIAB mailing list</a> to be involved in OpenIAB API development.

License
=====
Source code of the OpenIAB library and the samples is available under the terms of the Apache License, Version 2.0:
http://www.apache.org/licenses/LICENSE-2.0

The OpenIAB API specification and the related texts are available under the terms of the Creative Commons Attribution 2.5 license:
http://creativecommons.org/licenses/by/2.5/

