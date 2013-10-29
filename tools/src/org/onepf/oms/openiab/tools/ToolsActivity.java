package org.onepf.oms.openiab.tools;

import java.util.List;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.Menu;

public class ToolsActivity extends Activity {
    private static final String TAG = ToolsActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tool);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.tool, menu);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        printPackages(this);
        

        
    }
    
    public static void printPackages(Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> allPackages = packageManager.getInstalledPackages(0);
        for (PackageInfo packageInfo : allPackages) {
            String installer = packageManager.getInstallerPackageName(packageInfo.packageName);
            Log.w(TAG, "printPackages() package: " + packageInfo.packageName + ", installer: " + installer);
        }
    }

    
}
