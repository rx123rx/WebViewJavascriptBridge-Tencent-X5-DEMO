package com.example.js.jsbridgedemo;

import android.util.Log;

import org.json.JSONObject;

import wendu.webviewjavascriptbridge.WVJBWebView;

/**
 * Created by renxiao on 2018/1/29.
 */

public class GetKeyTask implements Runnable {
    private final WVJBWebView.WVJBResponseCallback function;
    private JSONObject dataJson;
    private MainActivity mainActivity;

    public GetKeyTask(MainActivity mainActivity, JSONObject dataJson, WVJBWebView.WVJBResponseCallback function) {
        this.function = function;
        this.dataJson = dataJson;
        this.mainActivity = mainActivity;
    }


    @Override
    public void run() {
        try {
            Log.e("GetKeyTask", dataJson.toString());
            String packageName = mainActivity.getPackageName();
            JSONObject response = new JSONObject();
            response.put("packageName", packageName);
            function.onResult(response.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
