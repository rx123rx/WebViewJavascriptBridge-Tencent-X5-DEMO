package com.example.js.jsbridgedemo;

import org.json.JSONException;
import org.json.JSONObject;

import wendu.webviewjavascriptbridge.WVJBWebView;

/**
 * Created by renxiao on 2018/1/29.
 */

public class JsMessageExecuteCenter {
    private final WVJBWebView.WVJBResponseCallback function;
    private String data;
    public final static String FUNCTION_NAME = "functionName";
    private MainActivity mainActivity;

    public JsMessageExecuteCenter(MainActivity mainActivity, String data, WVJBWebView.WVJBResponseCallback function) {
        this.mainActivity = mainActivity;
        this.data = data;
        this.function = function;
    }

    public void executeTask() {
        try {
            JSONObject dataJson = convertStringToJson(data);
            String functionName = dataJson.getString(FUNCTION_NAME);
            switch (functionName) {
                case "getKey":
                    mainActivity.runOnUiThread(new GetKeyTask(mainActivity, dataJson, function));
                    break;
                case "getContact":
                    mainActivity.runOnUiThread(new GetContactTask(mainActivity, dataJson, function));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private JSONObject convertStringToJson(String jsonString) throws JSONException {
        return new JSONObject(jsonString);
    }
}
