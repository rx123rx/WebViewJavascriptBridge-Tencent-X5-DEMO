package wendu.webviewjavascriptbridge;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Keep;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.tencent.smtt.export.external.interfaces.ConsoleMessage;
import com.tencent.smtt.export.external.interfaces.IX5WebChromeClient;
import com.tencent.smtt.export.external.interfaces.JsPromptResult;
import com.tencent.smtt.export.external.interfaces.JsResult;
import com.tencent.smtt.export.external.interfaces.WebResourceResponse;
import com.tencent.smtt.sdk.ValueCallback;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebStorage;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by du on 16/12/29.
 */

public class WVJBWebView extends WebView {
    private static final String BRIDGE_NAME = "WVJBInterface";
    private String APP_CACHE_DIRNAME;
    private static final int EXEC_SCRIPT = 1;
    private static final int LOAD_URL = 2;
    private static final int LOAD_URL_WITH_HEADERS = 3;
    private static final int HANDLE_MESSAGE = 4;
    MyHandler mainThreadHandler = null;
    private JavascriptCloseWindowListener javascriptCloseWindowListener = null;


    class MyHandler extends Handler {
        //  Using WeakReference to avoid memory leak
        WeakReference<Context> mContextReference;

        MyHandler(Context context) {
            mContextReference = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(Message msg) {
            final Context context = mContextReference.get();
            if (context != null) {
                switch (msg.what) {
                    case EXEC_SCRIPT:
                        _evaluateJavascript((String) msg.obj);
                        break;
                    case LOAD_URL:
                        WVJBWebView.super.loadUrl((String) msg.obj);
                        break;
                    case LOAD_URL_WITH_HEADERS: {
                        RequestInfo info = (RequestInfo) msg.obj;
                        WVJBWebView.super.loadUrl(info.url, info.headers);
                    }
                    break;
                    case HANDLE_MESSAGE:
                        WVJBWebView.this.handleMessage((String) msg.obj);
                        break;
                }
            }
        }
    }

    private class RequestInfo {
        String url;
        Map<String, String> headers;

        RequestInfo(String url, Map<String, String> additionalHttpHeaders) {
            this.url = url;
            this.headers = additionalHttpHeaders;
        }
    }

    private class WVJBMessage {
        Object data = null;
        String callbackId = null;
        String handlerName = null;
        String responseId = null;
        Object responseData = null;
    }


    public WVJBWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WVJBWebView(Context context) {
        super(context);
        init();
    }

    private ArrayList<WVJBMessage> startupMessageQueue = null;
    private Map<String, WVJBResponseCallback> responseCallbacks = null;
    private Map<String, WVJBHandler> messageHandlers = null;
    private long uniqueId = 0;
    private boolean alertboxBlock = true;

    public interface WVJBResponseCallback<T> {
        void onResult(T data);
    }

    public interface WVJBMethodExistCallback {
        void onResult(boolean exist);
    }


    public interface JavascriptCloseWindowListener {
        /**
         * @return If true, close the current activity, otherwise, do nothing.
         */
        boolean onClose();
    }


    public interface WVJBHandler<T, R> {
        void handler(T data, WVJBResponseCallback<R> callback);
    }

    public void disableJavascriptAlertBoxSafetyTimeout(boolean disable) {
        alertboxBlock = !disable;
    }

    public void callHandler(String handlerName) {
        callHandler(handlerName, null, null);
    }

    public void callHandler(String handlerName, Object data) {
        callHandler(handlerName, data, null);
    }

    public <T> void callHandler(String handlerName, Object data,
                                WVJBResponseCallback<T> responseCallback) {
        sendData(data, responseCallback, handlerName);
    }

    /**
     * Test whether the handler exist in javascript
     *
     * @param handlerName
     * @param callback
     */
    public void hasJavascriptMethod(String handlerName, final WVJBMethodExistCallback callback) {
        callHandler("_hasJavascriptMethod", handlerName, new WVJBResponseCallback() {
            @Override
            public void onResult(Object data) {
                callback.onResult((boolean) data);
            }
        });
    }

    /**
     * set a listener for javascript closing the current activity.
     */
    public void setJavascriptCloseWindowListener(JavascriptCloseWindowListener listener) {
        javascriptCloseWindowListener = listener;
    }

    public <T, R> void registerHandler(String handlerName, WVJBHandler<T, R> handler) {
        if (handlerName == null || handlerName.length() == 0 || handler == null) {
            return;
        }
        messageHandlers.put(handlerName, handler);
    }

    // send the onResult message to javascript
    private void sendData(Object data, WVJBResponseCallback responseCallback,
                          String handlerName) {
        if (data == null && (handlerName == null || handlerName.length() == 0)) {
            return;
        }
        WVJBMessage message = new WVJBMessage();
        if (data != null) {
            message.data = data;
        }
        if (responseCallback != null) {
            String callbackId = "java_cb_" + (++uniqueId);
            responseCallbacks.put(callbackId, responseCallback);
            message.callbackId = callbackId;
        }
        if (handlerName != null) {
            message.handlerName = handlerName;
        }
        queueMessage(message);
    }

    private synchronized void queueMessage(WVJBMessage message) {

        if (startupMessageQueue != null) {
            startupMessageQueue.add(message);
        } else {
            dispatchMessage(message);
        }
    }

    private void dispatchMessage(WVJBMessage message) {
        String messageJSON = message2JSONObject(message).toString();
        evaluateJavascript(String.format("WebViewJavascriptBridge._handleMessageFromJava(%s)", messageJSON));
    }

    // handle the onResult message from javascript
    private void handleMessage(String info) {
        try {
            JSONObject jo = new JSONObject(info);
            WVJBMessage message = JSONObject2WVJBMessage(jo);
            if (message.responseId != null) {
                WVJBResponseCallback responseCallback = responseCallbacks
                        .remove(message.responseId);
                if (responseCallback != null) {
                    responseCallback.onResult(message.responseData);
                }
            } else {
                WVJBResponseCallback responseCallback = null;
                if (message.callbackId != null) {
                    final String callbackId = message.callbackId;
                    responseCallback = new WVJBResponseCallback() {
                        @Override
                        public void onResult(Object data) {
                            WVJBMessage msg = new WVJBMessage();
                            msg.responseId = callbackId;
                            msg.responseData = data;
                            dispatchMessage(msg);
                        }
                    };
                }

                WVJBHandler handler;
                handler = messageHandlers.get(message.handlerName);
                if (handler != null) {
                    handler.handler(message.data, responseCallback);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private JSONObject message2JSONObject(WVJBMessage message) {
        JSONObject jo = new JSONObject();
        try {
            if (message.callbackId != null) {
                jo.put("callbackId", message.callbackId);
            }
            if (message.data != null) {
                jo.put("data", message.data);
            }
            if (message.handlerName != null) {
                jo.put("handlerName", message.handlerName);
            }
            if (message.responseId != null) {
                jo.put("responseId", message.responseId);
            }
            if (message.responseData != null) {
                jo.put("responseData", message.responseData);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jo;
    }

    private WVJBMessage JSONObject2WVJBMessage(JSONObject jo) {
        WVJBMessage message = new WVJBMessage();
        try {
            if (jo.has("callbackId")) {
                message.callbackId = jo.getString("callbackId");
            }
            if (jo.has("data")) {
                message.data = jo.get("data");
            }
            if (jo.has("handlerName")) {
                message.handlerName = jo.getString("handlerName");
            }
            if (jo.has("responseId")) {
                message.responseId = jo.getString("responseId");
            }
            if (jo.has("responseData")) {
                message.responseData = jo.get("responseData");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return message;
    }


    @Keep
    void init() {
        mainThreadHandler = new MyHandler(getContext());
        APP_CACHE_DIRNAME = getContext().getFilesDir().getAbsolutePath() + "/webcache";
        this.responseCallbacks = new HashMap<>();
        this.messageHandlers = new HashMap<>();
        this.startupMessageQueue = new ArrayList<>();
//        WebSettings settings = getSettings();
//        settings.setDomStorageEnabled(true);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
//            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
//        }
//        settings.setAllowFileAccess(false);
//        settings.setAppCacheEnabled(false);
//        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
//        settings.setJavaScriptEnabled(true);
//        settings.setLoadWithOverviewMode(true);
//        settings.setSupportMultipleWindows(true);
//        settings.setAppCachePath(APP_CACHE_DIRNAME);
//        settings.setUseWideViewPort(true);
        super.setWebChromeClient(mWebChromeClient);
        super.setWebViewClient(mWebViewClient);

        registerHandler("_hasNativeMethod", new WVJBHandler() {
            @Override
            public void handler(Object data, WVJBResponseCallback callback) {
                callback.onResult(messageHandlers.get(data) != null);
            }
        });
        registerHandler("_closePage", new WVJBHandler() {
            @Override
            public void handler(Object data, WVJBResponseCallback callback) {
                if (javascriptCloseWindowListener == null
                        || javascriptCloseWindowListener.onClose()) {
                    ((Activity) getContext()).onBackPressed();
                }
            }
        });
        registerHandler("_disableJavascriptAlertBoxSafetyTimeout", new WVJBHandler() {
            @Override
            public void handler(Object data, WVJBResponseCallback callback) {
                disableJavascriptAlertBoxSafetyTimeout((boolean) data);
            }
        });
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            super.addJavascriptInterface(new Object() {
                @Keep
                @JavascriptInterface
                public void notice(String info) {
                    Message msg = new Message();
                    msg.what = HANDLE_MESSAGE;
                    msg.obj = info;
                    mainThreadHandler.sendMessage(msg);
                }

            }, BRIDGE_NAME);
        }

    }

    private void _evaluateJavascript(String script) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WVJBWebView.super.evaluateJavascript(script, null);
        } else {
            loadUrl("javascript:" + script);
        }
    }

    /**
     * This method can be called in any thread, and if it is not called in the main thread,
     * it will be automatically distributed to the main thread.
     *
     * @param script
     */
    public void evaluateJavascript(final String script) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            _evaluateJavascript(script);
        } else {
            Message msg = new Message();
            msg.what = EXEC_SCRIPT;
            msg.obj = script;
            mainThreadHandler.sendMessage(msg);
        }
    }


    /**
     * This method can be called in any thread, and if it is not called in the main thread,
     * it will be automatically distributed to the main thread.
     *
     * @param url
     */
    @Override
    public void loadUrl(String url) {
        Message msg = new Message();
        msg.what = LOAD_URL;
        msg.obj = url;
        mainThreadHandler.sendMessage(msg);
    }


    /**
     * This method can be called in any thread, and if it is not called in the main thread,
     * it will be automatically distributed to the main thread.
     *
     * @param url
     * @param additionalHttpHeaders
     */
    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        Message msg = new Message();
        msg.what = LOAD_URL_WITH_HEADERS;
        msg.obj = new RequestInfo(url, additionalHttpHeaders);
        mainThreadHandler.sendMessage(msg);
    }


    // proxy client
    WebChromeClient webChromeClient;
    WebViewClient webViewClient;

    @Override
    public void setWebChromeClient(WebChromeClient client) {
        webChromeClient = client;
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        webViewClient = client;
    }

    private WebChromeClient mWebChromeClient = new WebChromeClient() {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (webChromeClient != null) {
                webChromeClient.onProgressChanged(view, newProgress);
            } else {
                super.onProgressChanged(view, newProgress);
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (webChromeClient != null) {
                webChromeClient.onReceivedTitle(view, title);
            } else {
                super.onReceivedTitle(view, title);
            }
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            if (webChromeClient != null) {
                webChromeClient.onReceivedIcon(view, icon);
            } else {
                super.onReceivedIcon(view, icon);
            }
        }

        @Override
        public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed) {
            if (webChromeClient != null) {
                webChromeClient.onReceivedTouchIconUrl(view, url, precomposed);
            } else {
                super.onReceivedTouchIconUrl(view, url, precomposed);
            }
        }

        @Override
        public void onShowCustomView(View view, IX5WebChromeClient.CustomViewCallback callback) {
            if (webChromeClient != null) {
                webChromeClient.onShowCustomView(view, callback);
            } else {
                super.onShowCustomView(view, callback);
            }
        }


        @Override
        public void onShowCustomView(View view, int requestedOrientation,
                                     IX5WebChromeClient.CustomViewCallback callback) {
            if (webChromeClient != null) {
                webChromeClient.onShowCustomView(view, requestedOrientation, callback);
            } else {
                super.onShowCustomView(view, requestedOrientation, callback);
            }
        }

        @Override
        public void onHideCustomView() {
            if (webChromeClient != null) {
                webChromeClient.onHideCustomView();
            } else {
                super.onHideCustomView();
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog,
                                      boolean isUserGesture, Message resultMsg) {
            if (webChromeClient != null) {
                return webChromeClient.onCreateWindow(view, isDialog,
                        isUserGesture, resultMsg);
            }
            return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
        }

        @Override
        public void onRequestFocus(WebView view) {
            if (webChromeClient != null) {
                webChromeClient.onRequestFocus(view);
            } else {
                super.onRequestFocus(view);
            }
        }

        @Override
        public void onCloseWindow(WebView window) {
            if (webChromeClient != null) {
                webChromeClient.onCloseWindow(window);
            } else {
                super.onCloseWindow(window);
            }
        }

        @Override
        public boolean onJsAlert(WebView view, String url, final String message, final JsResult result) {
            if (!alertboxBlock) {
                result.confirm();
            }
            if (webChromeClient != null) {
                if (webChromeClient.onJsAlert(view, url, message, result)) {
                    return true;
                }
            }
            Dialog alertDialog = new AlertDialog.Builder(getContext()).
                    setMessage(message).
                    setCancelable(false).
                    setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            if (alertboxBlock) {
                                result.confirm();
                            }
                        }
                    })
                    .create();
            alertDialog.show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message,
                                   final JsResult result) {
            if (!alertboxBlock) {
                result.confirm();
            }
            if (webChromeClient != null && webChromeClient.onJsConfirm(view, url, message, result)) {
                return true;
            } else {
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (alertboxBlock) {
                            if (which == Dialog.BUTTON_POSITIVE) {
                                result.confirm();
                            } else {
                                result.cancel();
                            }
                        }
                    }
                };
                new AlertDialog.Builder(getContext())
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, listener).show();
                return true;

            }

        }

        @Override
        public boolean onJsPrompt(WebView view, String url, final String message,
                                  String defaultValue, final JsPromptResult result) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
                String prefix = "_wvjbxx";
                if (message.equals(prefix)) {
                    Message msg = new Message();
                    msg.what = HANDLE_MESSAGE;
                    msg.obj = defaultValue;
                    mainThreadHandler.sendMessage(msg);
                }
                return true;
            }
            if (!alertboxBlock) {
                result.confirm();
            }
            if (webChromeClient != null && webChromeClient.onJsPrompt(view, url, message, defaultValue, result)) {
                return true;
            } else {
                final EditText editText = new EditText(getContext());
                editText.setText(defaultValue);
                if (defaultValue != null) {
                    editText.setSelection(defaultValue.length());
                }
                float dpi = getContext().getResources().getDisplayMetrics().density;
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (alertboxBlock) {
                            if (which == Dialog.BUTTON_POSITIVE) {
                                result.confirm(editText.getText().toString());
                            } else {
                                result.cancel();
                            }
                        }
                    }
                };
                new AlertDialog.Builder(getContext())
                        .setTitle(message)
                        .setView(editText)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, listener)
                        .show();
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                int t = (int) (dpi * 16);
                layoutParams.setMargins(t, 0, t, 0);
                layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
                editText.setLayoutParams(layoutParams);
                int padding = (int) (15 * dpi);
                editText.setPadding(padding - (int) (5 * dpi), padding, padding, padding);
                return true;
            }

        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
            if (webChromeClient != null) {
                return webChromeClient.onJsBeforeUnload(view, url, message, result);
            }
            return super.onJsBeforeUnload(view, url, message, result);
        }

        @Override
        public void onExceededDatabaseQuota(String url, String databaseIdentifier, long quota,
                                            long estimatedDatabaseSize,
                                            long totalQuota,
                                            WebStorage.QuotaUpdater quotaUpdater) {
            if (webChromeClient != null) {
                webChromeClient.onExceededDatabaseQuota(url, databaseIdentifier, quota,
                        estimatedDatabaseSize, totalQuota, quotaUpdater);
            } else {
                super.onExceededDatabaseQuota(url, databaseIdentifier, quota,
                        estimatedDatabaseSize, totalQuota, quotaUpdater);
            }
        }

        @Override
        public void onReachedMaxAppCacheSize(long requiredStorage, long quota, WebStorage.QuotaUpdater quotaUpdater) {
            if (webChromeClient != null) {
                webChromeClient.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
            }
            super.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
        }

        @Override
        public void onGeolocationPermissionsHidePrompt() {
            if (webChromeClient != null) {
                webChromeClient.onGeolocationPermissionsHidePrompt();
            } else {
                super.onGeolocationPermissionsHidePrompt();
            }
        }


        @Override
        public boolean onJsTimeout() {
            if (webChromeClient != null) {
                return webChromeClient.onJsTimeout();
            }
            return super.onJsTimeout();
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            if (webChromeClient != null) {
                return webChromeClient.onConsoleMessage(consoleMessage);
            }
            return super.onConsoleMessage(consoleMessage);
        }

        @Override
        public Bitmap getDefaultVideoPoster() {

            if (webChromeClient != null) {
                return webChromeClient.getDefaultVideoPoster();
            }
            return super.getDefaultVideoPoster();
        }

        @Override
        public View getVideoLoadingProgressView() {
            if (webChromeClient != null) {
                return webChromeClient.getVideoLoadingProgressView();
            }
            return super.getVideoLoadingProgressView();
        }

        @Override
        public void getVisitedHistory(ValueCallback<String[]> callback) {
            if (webChromeClient != null) {
                webChromeClient.getVisitedHistory(callback);
            } else {
                super.getVisitedHistory(callback);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (webChromeClient != null) {
                return webChromeClient.onShowFileChooser(webView, filePathCallback, fileChooserParams);
            }
            return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
        }
    };

    private WebViewClient mWebViewClient = new WebViewClient() {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (webViewClient != null) {
                return webViewClient.shouldOverrideUrlLoading(view, url);
            } else {
                return super.shouldOverrideUrlLoading(view, url);
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (webViewClient != null) {
                webViewClient.onPageStarted(view, url, favicon);
            } else {
                super.onPageStarted(view, url, favicon);
            }

        }

        @Override
        public void onPageFinished(WebView view, String url) {
            // inject code
            try {
                Log.e("onPageFinished",url);
                InputStream is = view.getContext().getAssets()
                        .open("WebViewJavascriptBridge.js");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                String js = new String(buffer);
                evaluateJavascript(js);
            } catch (IOException e) {
                e.printStackTrace();
            }

            synchronized (WVJBWebView.this) {
                if (startupMessageQueue != null) {
                    for (int i = 0; i < startupMessageQueue.size(); i++) {
                        dispatchMessage(startupMessageQueue.get(i));
                    }
                    startupMessageQueue = null;
                }
            }

            if (webViewClient != null) {
                webViewClient.onPageFinished(view, url);
            } else {
                super.onPageFinished(view, url);
            }

        }

        @Override
        public void onLoadResource(WebView view, String url) {
            if (webViewClient != null) {
                webViewClient.onLoadResource(view, url);
            } else {
                super.onLoadResource(view, url);
            }
        }

        @Override
        @Deprecated
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (webViewClient != null) {
                return webViewClient.shouldInterceptRequest(view, url);
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @Override
        @Deprecated
        public void onTooManyRedirects(WebView view, Message cancelMsg, Message continueMsg) {
            if (webViewClient != null) {
                webViewClient.onTooManyRedirects(view, cancelMsg, continueMsg);
            } else {
                super.onTooManyRedirects(view, cancelMsg, continueMsg);
            }
        }

        @Override
        @Deprecated
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (webViewClient != null) {
                webViewClient.onReceivedError(view, errorCode, description, failingUrl);
            } else {
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        }

        @Override
        public void onFormResubmission(WebView view, Message dontResend, Message resend) {
            if (webViewClient != null) {
                webViewClient.onFormResubmission(view, dontResend, resend);
            } else {
                super.onFormResubmission(view, dontResend, resend);
            }
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (webViewClient != null) {
                webViewClient.doUpdateVisitedHistory(view, url, isReload);
            } else {
                super.doUpdateVisitedHistory(view, url, isReload);
            }

        }

        @Override
        public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
            if (webViewClient != null) {
                return webViewClient.shouldOverrideKeyEvent(view, event);
            } else {
                return super.shouldOverrideKeyEvent(view, event);
            }
        }

        @Override
        @Deprecated
        public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
            if (webViewClient != null) {
                webViewClient.onUnhandledKeyEvent(view, event);
            } else {
                super.onUnhandledKeyEvent(view, event);
            }

        }

        @Override
        public void onScaleChanged(WebView view, float oldScale, float newScale) {
            if (webViewClient != null) {
                webViewClient.onScaleChanged(view, oldScale, newScale);
            } else {
                super.onScaleChanged(view, oldScale, newScale);
            }

        }

    };

}