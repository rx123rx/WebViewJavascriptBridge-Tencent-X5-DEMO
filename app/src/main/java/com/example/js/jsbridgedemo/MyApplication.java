package com.example.js.jsbridgedemo;

import android.app.Application;

import com.tencent.smtt.sdk.QbSdk;

/**
 * Created by renxiao on 2018/1/30.
 */

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        /*初始化X5环境，使用X5 webView内核*/
        QbSdk.initX5Environment(this, null);
    }
}
