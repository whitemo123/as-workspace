package cn.mobai.webview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.webkit.WebView;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.alibaba.fastjson.JSONObject;

import java.net.URLEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("SetJavaScriptEnabled")
public class BridgeWebView extends WebView implements WebViewJavascriptBridge, BridgeWebViewClient.OnLoadJSListener {
  private final int URL_MAX_CHARACTER_NUM=2097152;
  private Map<String, OnBridgeCallback> mCallbacks = new ArrayMap<>();
  private List<Object> mMessages = new ArrayList<>();
  private BridgeWebViewClient mClient;
  private long mUniqueId = 0;
  private boolean mJSLoaded = false;

  public BridgeWebView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public BridgeWebView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  public BridgeWebView(Context context) {
    super(context);
    init();
  }

  private void init() {
    clearCache(true);
    getSettings().setUseWideViewPort(true);
//		webView.getSettings().setLoadWithOverviewMode(true);
    getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
    getSettings().setJavaScriptEnabled(true);
//        mContent.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      WebView.setWebContentsDebuggingEnabled(true);
    }
    mClient = new BridgeWebViewClient(this);
    super.setWebViewClient(mClient);
  }

  public boolean isJSLoaded() {
    return mJSLoaded;
  }

  public Map<String, OnBridgeCallback> getCallbacks() {
    return mCallbacks;
  }

  @Override
  public void setWebViewClient(WebViewClient client) {
    mClient.setWebViewClient(client);
  }

  @Override
  public void onLoadStart() {
    mJSLoaded = false;
  }

  @Override
  public void onLoadFinished() {
    mJSLoaded = true;
    if (mMessages != null) {
      for (Object message : mMessages) {
        dispatchMessage(message);
      }
      mMessages = null;
    }
  }

  @Override
  public void sendToWeb(String data) {
    sendToWeb(data, (OnBridgeCallback) null);
  }

  @Override
  public void sendToWeb(String data, OnBridgeCallback responseCallback) {
    doSend(null, data, responseCallback);
  }

  /**
   * call javascript registered handler
   * 调用javascript处理程序注册
   *
   * @param handlerName handlerName
   * @param data        data
   * @param callBack    OnBridgeCallback
   */
  public void callHandler(String handlerName, String data, OnBridgeCallback callBack) {
    doSend(handlerName, data, callBack);
  }


  @Override
  public void sendToWeb(String function, Object... values) {
    // 必须要找主线程才会将数据传递出去 --- 划重点
    if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
      String jsCommand = String.format(function, values);
      jsCommand = String.format(BridgeUtil.JAVASCRIPT_STR, jsCommand);
      loadUrl(jsCommand);
    }
  }

  /**
   * 保存message到消息队列
   *
   * @param handlerName      handlerName
   * @param data             data
   * @param responseCallback OnBridgeCallback
   */
  private void doSend(String handlerName, Object data, OnBridgeCallback responseCallback) {
    if (!(data instanceof String)){
      return;
    }
    JSRequest request = new JSRequest();
    if (data != null) {
      request.data = data instanceof String ? (String) data : JSONObject.toJSONString(data);
    }
    if (responseCallback != null) {
      String callbackId = String.format(BridgeUtil.CALLBACK_ID_FORMAT, (++mUniqueId) + (BridgeUtil.UNDERLINE_STR + SystemClock.currentThreadTimeMillis()));
      mCallbacks.put(callbackId, responseCallback);
      request.callbackId = callbackId;
    }
    if (!TextUtils.isEmpty(handlerName)) {
      request.handlerName = handlerName;
    }
    queueMessage(request);
  }

  /**
   * list<message> != null 添加到消息集合否则分发消息
   *
   * @param message Message
   */
  private void queueMessage(Object message) {
    if (mMessages != null) {
      mMessages.add(message);
    } else {
      dispatchMessage(message);
    }
  }

  /**
   * 分发message 必须在主线程才分发成功
   *
   * @param message Message
   */
  private void dispatchMessage(Object message) {
    String messageJson = JSONObject.toJSONString(message);
    //escape special characters for json string  为json字符串转义特殊字符

    // 系统原生 API 做 Json转义，没必要自己正则替换，而且替换不一定完整
    messageJson = org.json.JSONObject.quote(messageJson);
    String javascriptCommand = String.format(BridgeUtil.JS_HANDLE_MESSAGE_FROM_JAVA, messageJson);
    // 必须要找主线程才会将数据传递出去 --- 划重点
    if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT&&javascriptCommand.length()>=URL_MAX_CHARACTER_NUM) {
        this.evaluateJavascript(javascriptCommand,null);
      }else {
        this.loadUrl(javascriptCommand);
      }
    }
  }

  public void sendResponse(Object data, String callbackId) {
    if (!(data instanceof String)){
      return;
    }
    if (!TextUtils.isEmpty(callbackId)) {
      final JSResponse response = new JSResponse();
      response.responseId = callbackId;
      response.responseData = data instanceof String ? (String) data : JSONObject.toJSONString(data);
      if (Thread.currentThread() == Looper.getMainLooper().getThread()){
        dispatchMessage(response);
      }else {
        post(new Runnable() {
          @Override
          public void run() {
            dispatchMessage(response);
          }
        });
      }

    }
  }

  @Override
  public void destroy() {
    super.destroy();
    mCallbacks.clear();
  }

  public static abstract class BaseJavascriptInterface {

    private Map<String, OnBridgeCallback> mCallbacks;

    public BaseJavascriptInterface(Map<String, OnBridgeCallback> callbacks) {
      mCallbacks = callbacks;
    }

    @JavascriptInterface
    public String send(String data, String callbackId) {
      Log.d("BaseJavascriptInterface", data + ", callbackId: " + callbackId + " " + Thread.currentThread().getName());
      return send(data);
    }

    @JavascriptInterface
    public void response(String data, String responseId) {
      Log.d("BaseJavascriptInterface", data + ", responseId: " + responseId + " " + Thread.currentThread().getName());
      if (!TextUtils.isEmpty(responseId)) {
        OnBridgeCallback function = mCallbacks.remove(responseId);
        if (function != null) {
          function.onCallBack(data);
        }
      }
    }

    public abstract String send(String data);
  }

}
