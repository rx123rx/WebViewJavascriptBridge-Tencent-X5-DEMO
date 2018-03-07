package com.example.js.jsbridgedemo;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import wendu.webviewjavascriptbridge.WVJBWebView;

import static android.app.Activity.RESULT_OK;

/**
 * Created by renxiao on 2018/1/29.
 */

public class GetContactTask implements Runnable, MainActivity.GetContactsListener {
    private WVJBWebView.WVJBResponseCallback function;
    private JSONObject dataJson;
    private MainActivity mainActivity;
    private static final int OPEN_CONTACTS_CODE = 25;

    public GetContactTask(MainActivity mainActivity, JSONObject dataJson, WVJBWebView.WVJBResponseCallback function) {
        this.function = function;
        this.dataJson = dataJson;
        this.mainActivity = mainActivity;
    }


    @Override
    public void run() {
        try {
            mainActivity.setGetContactsListener(this);
            getContacts(mainActivity, OPEN_CONTACTS_CODE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 拉起通讯录
     *
     * @param openContactsCode 请求码
     */
    public static void getContacts(Activity activity, int openContactsCode) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.PICK");
        intent.setType("vnd.android.cursor.dir/phone_v2");
        activity.startActivityForResult(intent, openContactsCode);
    }

    @Override
    public void onGetContacts(int requestCode, int resultCode, Intent data) {
        if (requestCode == OPEN_CONTACTS_CODE && resultCode == RESULT_OK) {
            receiveContactResult(data);
        }
    }

    /**
     * 返回通讯录数据
     *
     * @param data 获取到的通讯录数据
     */
    private void receiveContactResult(Intent data) {
        try {
            if (data != null) {
                Uri uri = data.getData();
                String num = null;
                // 创建内容解析者
                ContentResolver contentResolver = mainActivity.getContentResolver();
                Cursor cursor = contentResolver.query(uri, null, null, null, null);
                assert cursor != null;
                while (cursor.moveToNext()) {
                    num = cursor.getString(cursor.getColumnIndex("data1"));
                }
                cursor.close();
                if (!TextUtils.isEmpty(num)) {
                    function.onResult(num);
                }
            }
        } catch (Exception e) {
        }
    }
}
