package com.example.js.jsbridgedemo;

import wendu.webviewjavascriptbridge.WVJBWebView;

/**
 * Created by renxiao on 2018/1/29.
 */

public class DefaultHandler {
    private final MainActivity mainActivity;

    public DefaultHandler(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void handler(String data, WVJBWebView.WVJBResponseCallback function) {
        new JsMessageExecuteCenter(mainActivity, data, function).executeTask();
    }
}
