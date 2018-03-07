package com.example.js.jsbridgedemo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.tencent.smtt.sdk.WebSettings;

import wendu.webviewjavascriptbridge.WVJBWebView;

public class MainActivity extends AppCompatActivity {

    private WVJBWebView webview;
    private DefaultHandler defaultHandler;
    private GetContactsListener getContactsListener;

    public interface GetContactsListener {
        void onGetContacts(int requestCode, int resultCode, Intent data);
    }

    public void setGetContactsListener(GetContactsListener getContactsListener) {
        this.getContactsListener = getContactsListener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initWebView();
        loadUrl();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (getContactsListener != null) {
            getContactsListener.onGetContacts(requestCode, resultCode, data);
        }
    }

    private void initView() {
        webview = findViewById(R.id.webview);
    }

    private void initWebView() {
        WebSettings webSettings = webview.getSettings();
        webSettings.setDomStorageEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setSaveFormData(false);
        webSettings.setSavePassword(false);
        webSettings.setDatabaseEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        /*设置js——bridge 消息接收器*/
        webview.registerHandler("default", new WVJBWebView.WVJBHandler<String, String>() {
            @Override
            public void handler(String data, WVJBWebView.WVJBResponseCallback<String> callback) {
                if (defaultHandler == null) {
                    defaultHandler = new DefaultHandler(MainActivity.this);
                }
                defaultHandler.handler(data, callback);
            }
        });
    }

    private void loadUrl() {
        webview.loadUrl("file:///android_asset/index.html");
    }
}
