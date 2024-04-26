package cn.mobai.workspace;

import android.app.Activity;
import android.os.Bundle;

import cn.mobai.webview.BridgeWebView;

public class MainActivity extends Activity {

  private BridgeWebView bridgeWebView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    bridgeWebView = findViewById(R.id.bridgeWebView);

    bridgeWebView.loadUrl("https://www.baidu.com");
  }
}