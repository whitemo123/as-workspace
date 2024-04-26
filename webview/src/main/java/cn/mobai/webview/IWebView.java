package cn.mobai.webview;

import android.content.Context;

public interface IWebView {

  Context getContext();

  void loadUrl(String url);
}
