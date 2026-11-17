package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.LiveChannelItem;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * 网页频道播放容器（吸收 WebViewTvLive / TVBrowser 思路的最小实现）。
 * <p>
 * 频道地址为网页（非 .m3u8/.mp4 等流媒体后缀）或直播源里写了 click 字段时，
 * LivePlayActivity 会把播放交给本页面：
 * <ul>
 *     <li>视频元素自动全屏（onShowCustomView）；</li>
 *     <li>上/下键切换频道（回调 LivePlayActivity）；</li>
 *     <li>返回键退出（全屏视频先退全屏）；</li>
 *     <li>复用直播源的 ua / referer / click 字段。</li>
 * </ul>
 */
public class WebLiveActivity extends BaseActivity {

    /** 频道切换回调：direction = +1 下一个频道 / -1 上一个频道 */
    public interface SwitchListener {
        void onSwitchChannel(int direction, WebLiveActivity activity);
    }

    private static WeakReference<WebLiveActivity> sInstance = new WeakReference<>(null);
    private static LiveChannelItem sPendingChannel;
    private static SwitchListener sPendingListener;

    /** 由 LivePlayActivity 发起启动（静态传参，避免 Intent 序列化 LiveChannelItem） */
    public static void launch(Context context, LiveChannelItem channel, SwitchListener listener) {
        sPendingChannel = channel;
        sPendingListener = listener;
        Intent intent = new Intent(context, WebLiveActivity.class);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** 当前存活的实例（无 / 正在关闭 → null） */
    public static WebLiveActivity current() {
        WebLiveActivity act = sInstance.get();
        if (act == null || act.isFinishing() || act.isDestroyed()) return null;
        return act;
    }

    private FrameLayout mRoot;
    private WebView mWebView;
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private SwitchListener mListener;
    private String mUrl = "";
    private String mUa = "";
    private String mReferer = "";
    private String mClick = "";
    private boolean mFullscreen = false;
    private int mClickGen = 0;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_web_live;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void init() {
        sInstance = new WeakReference<>(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mListener = sPendingListener;
        LiveChannelItem channel = sPendingChannel;
        sPendingChannel = null;
        sPendingListener = null;
        if (channel == null) {
            finish();
            return;
        }
        mRoot = findViewById(R.id.web_root);
        initWebView();
        loadChannel(channel);
    }

    /** 切换到新频道（LivePlayActivity 在网页频道之间互切时直接复用本页面） */
    public void loadChannel(LiveChannelItem channel) {
        if (channel == null) return;
        mUrl = channel.getUrl();
        mUa = channel.getChannelUa();
        mReferer = channel.getChannelReferer();
        mClick = channel.getChannelClick();
        if (mCustomView != null) exitCustomView();
        if (mWebView == null) return;
        String ua = mUa.trim();
        if (!ua.isEmpty()) mWebView.getSettings().setUserAgentString(ua);
        Map<String, String> headers = new HashMap<>();
        if (!mReferer.trim().isEmpty()) headers.put("Referer", mReferer);
        mWebView.loadUrl(mUrl, headers);
        updateChannelToast(channel);
    }

    private void updateChannelToast(LiveChannelItem channel) {
        try {
            Toast.makeText(this, channel.getChannelName(), Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        mWebView = new WebView(this);
        mWebView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mWebView.setBackgroundColor(Color.BLACK);
        mWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = mWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) return false;
                // intent:// 等非网页协议交给系统处理
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // 直播站点证书链常不完整，与同类播放器一致放行
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                applyClick();
            }
        });
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (mCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                mCustomView = view;
                mCustomViewCallback = callback;
                mRoot.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                mWebView.setVisibility(View.GONE);
                mFullscreen = true;
            }

            @Override
            public void onHideCustomView() {
                exitCustomView();
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        request.grant(request.getResources());
                    }
                });
            }
        });
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(mWebView, true);
        mRoot.addView(mWebView);
    }

    /** 页面加载完成后注入 click（CSS 选择器 / javascript: / js: 前缀），失败自动重试两次 */
    private void applyClick() {
        final String click = mClick.trim();
        if (click.isEmpty()) return;
        final int gen = ++mClickGen;
        evaluateClick(click);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (gen == mClickGen) evaluateClick(click);
            }
        }, 3000);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (gen == mClickGen) evaluateClick(click);
            }
        }, 8000);
    }

    private void evaluateClick(String click) {
        if (mWebView == null) return;
        String js;
        String lower = click.toLowerCase();
        if (lower.startsWith("javascript:")) {
            js = click.substring("javascript:".length());
        } else if (lower.startsWith("js:")) {
            js = click.substring(3);
        } else {
            String safe = click.replace("\\", "\\\\").replace("'", "\\'");
            js = "(function(){try{var e=document.querySelector('" + safe + "');if(e){e.click();return 'ok';}return 'miss';}catch(err){return 'err';}})();";
        }
        mWebView.evaluateJavascript(js, null);
    }

    private void exitCustomView() {
        if (mCustomView == null) return;
        mRoot.removeView(mCustomView);
        mCustomView = null;
        mFullscreen = false;
        if (mCustomViewCallback != null) {
            WebChromeClient.CustomViewCallback cb = mCustomViewCallback;
            mCustomViewCallback = null;
            try {
                cb.onCustomViewHidden();
            } catch (Exception ignored) {
            }
        }
        mWebView.setVisibility(View.VISIBLE);
    }

    /** 切回流媒体频道时由 LivePlayActivity 调用：关掉网页层让视频露出来 */
    public void exitForVideo() {
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_CHANNEL_UP) {
                if (mListener != null) {
                    mListener.onSwitchChannel(-1, this);
                    return true;
                }
            } else if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                if (mListener != null) {
                    mListener.onSwitchChannel(1, this);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (mFullscreen) {
            exitCustomView();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWebView != null) mWebView.onResume();
    }

    @Override
    protected void onPause() {
        if (mWebView != null) mWebView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        exitCustomView();
        mHandler.removeCallbacksAndMessages(null);
        if (mWebView != null) {
            mRoot.removeView(mWebView);
            mWebView.removeAllViews();
            mWebView.destroy();
            mWebView = null;
        }
        if (sInstance.get() == this) sInstance.clear();
        sPendingChannel = null;
        super.onDestroy();
    }
}
