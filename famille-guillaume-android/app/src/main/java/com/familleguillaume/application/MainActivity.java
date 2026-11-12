package com.familleguillaume.application;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
  public static final String SITE="https://script.google.com/macros/s/AKfycby4lvl5sQPHuik14KFc3GNWv6zx6qu5exzndgyybti8E1ju5In1vqTC478HlxN0oh8rzw/exec";
  private WebView web;
  private FrameLayout root;
  private SharedPreferences prefs;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean slowShown = false;
  private final Runnable slowCheck = () -> showSlowSite();
  private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    try{
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
      getWindow().getDecorView().setSystemUiVisibility(5894);
      prefs=getSharedPreferences("famille_guillaume",MODE_PRIVATE);
      startApp();
    }catch(Throwable e){
      TextView msg=new TextView(this);
      msg.setText("Notre Famille Guillaume : erreur au démarrage.\n"+e.toString());
      msg.setTextSize(16);msg.setTextColor(Color.WHITE);
      msg.setBackgroundColor(Color.rgb(10,60,45));msg.setPadding(dp(20),dp(55),dp(20),dp(20));
      setContentView(msg);
    }
  }
  private void startApp(){
    root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(243,206,112));
    web=new WebView(this);
    web.setBackgroundColor(Color.rgb(12,61,47));
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);s.setJavaScriptCanOpenWindowsAutomatically(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    try{
      CookieManager cm=CookieManager.getInstance();
      cm.setAcceptCookie(true);cm.setAcceptThirdPartyCookies(web,true);
    }catch(Throwable ignored){}
    web.setWebChromeClient(new WebChromeClient());
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
        Uri u=request.getUrl();String scheme=u.getScheme();
        if("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)){
          if(u.toString().startsWith(SITE)){
            prefs.edit().putBoolean("intro_seen_v5",true).apply();
            watchSite();
          }
          return false;
        }
        try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Throwable ignored){}
        return true;
      }
      @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError err){
        if(req.isForMainFrame()&&!String.valueOf(req.getUrl()).startsWith("file:"))showError();
      }
    });
    FrameLayout.LayoutParams wp=new FrameLayout.LayoutParams(-1,-1);
    wp.setMargins(dp(2),dp(2),dp(2),dp(2));
    root.addView(web,wp);
    TextView refresh=new TextView(this);refresh.setText("⟳");
    refresh.setTextSize(29);refresh.setTextColor(Color.WHITE);
    refresh.setGravity(Gravity.CENTER);
    GradientDrawable bg=new GradientDrawable();
    bg.setColor(Color.rgb(12,78,61));bg.setCornerRadius(dp(18));
    bg.setStroke(dp(2),Color.rgb(248,211,121));
    refresh.setBackground(bg);refresh.setElevation(dp(6));
    FrameLayout.LayoutParams btn=new FrameLayout.LayoutParams(dp(53),dp(53),Gravity.TOP|Gravity.LEFT);
    btn.setMargins(dp(13),dp(14),0,0);
    root.addView(refresh,btn);
    refresh.setContentDescription("Actualiser");
    refresh.setOnClickListener(v->{watchSite();web.reload();});
    refresh.setOnLongClickListener(v->{web.loadUrl("file:///android_asset/intro.html");return true;});
    TextView browser=new TextView(this);
    browser.setText("↗");
    browser.setTextSize(26);
    browser.setTextColor(Color.WHITE);
    browser.setGravity(Gravity.CENTER);
    browser.setContentDescription("Ouvrir dans Chrome");
    GradientDrawable bgBrowser=new GradientDrawable();
    bgBrowser.setColor(Color.rgb(12,78,61));
    bgBrowser.setCornerRadius(dp(18));
    bgBrowser.setStroke(dp(2),Color.rgb(248,211,121));
    browser.setBackground(bgBrowser);
    browser.setElevation(dp(6));
    FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(53),dp(53),Gravity.TOP|Gravity.RIGHT);
    bp.setMargins(0,dp(14),dp(13),0);
    root.addView(browser,bp);
    browser.setOnClickListener(v->openInBrowser());
    setContentView(root);
    boolean introSeen=prefs.getBoolean("intro_seen_v5",false);
    if(introSeen)watchSite();
    web.loadUrl(introSeen?SITE:"file:///android_asset/intro.html");
  }
  private void watchSite(){
    handler.removeCallbacks(slowCheck);
    slowShown=false;
    handler.postDelayed(slowCheck,22000);
  }
  private void openInBrowser(){
    try {
      Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(SITE));
      i.addCategory(Intent.CATEGORY_BROWSABLE);
      startActivity(i);
    } catch(Throwable e) { showError(); }
  }
  private TextView makeAction(String label){
    TextView t = new TextView(this);
    t.setText(label); t.setTextColor(Color.WHITE); t.setTextSize(15);
    t.setGravity(Gravity.CENTER);
    t.setBackgroundColor(Color.rgb(16,101,76));
    t.setPadding(dp(10),dp(12),dp(10),dp(12));
    return t;
  }
  private void showSlowSite(){
    if(root==null || isFinishing() || slowShown)return;
    if(web!=null && web.getUrl()!=null && web.getUrl().startsWith("file:"))return;
    slowShown=true;
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(16),dp(15),dp(16),dp(12));
    GradientDrawable background = new GradientDrawable();
    background.setColor(Color.rgb(251,250,243));
    background.setCornerRadius(dp(20));
    background.setStroke(dp(2),Color.rgb(230,190,100));
    panel.setBackground(background);
    panel.setElevation(dp(9));
    TextView heading = new TextView(this);
    heading.setText("Le site tarde à répondre");
    heading.setTextSize(18);
    heading.setTextColor(Color.rgb(19,61,46));
    heading.setTypeface(null,1);
    panel.addView(heading);
    TextView detail = new TextView(this);
    detail.setText("Le chargement peut venir de Google Apps Script. Réessaie ou ouvre ton espace dans Chrome.");
    detail.setTextSize(14);
    detail.setTextColor(Color.rgb(61,77,66));
    detail.setPadding(0,dp(6),0,dp(12));
    panel.addView(detail);
    TextView retry=makeAction("↻ Réessayer");
    panel.addView(retry,new LinearLayout.LayoutParams(-1,dp(50)));
    TextView chrome=makeAction("↗ Ouvrir dans Chrome");
    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(50));
    cp.topMargin=dp(6);
    panel.addView(chrome,cp);
    TextView close=new TextView(this);
    close.setText("Continuer à attendre");
    close.setGravity(Gravity.CENTER);
    close.setTextSize(13);
    close.setTextColor(Color.rgb(21,78,58));
    close.setPadding(0,dp(12),0,dp(2));
    panel.addView(close);
    FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
    pp.setMargins(dp(14),0,dp(14),dp(15));
    root.addView(panel,pp);
    retry.setOnClickListener(v->{root.removeView(panel);watchSite();web.loadUrl(SITE);});
    chrome.setOnClickListener(v->openInBrowser());
    close.setOnClickListener(v->root.removeView(panel));
  }
  private void showError(){
    runOnUiThread(()->{
      if(root==null)return;
      TextView notice=new TextView(this);
      notice.setText("Connexion indisponible. Vérifiez Internet et appuyez sur ⟳ pour réessayer.");
      notice.setTextColor(Color.WHITE);notice.setTextSize(14);
      notice.setGravity(Gravity.CENTER);notice.setPadding(dp(12),dp(12),dp(12),dp(12));
      notice.setBackgroundColor(Color.rgb(10,65,49));
      root.addView(notice,new FrameLayout.LayoutParams(-1,dp(90),Gravity.BOTTOM));
      notice.setOnClickListener(v->root.removeView(notice));
    });
  }
  @Override public void onBackPressed(){
    if(web!=null&&web.canGoBack())web.goBack();
    else super.onBackPressed();
  }
  @Override protected void onDestroy(){
    handler.removeCallbacks(slowCheck);
    if(web!=null){web.destroy();web=null;}
    super.onDestroy();
  }
}
