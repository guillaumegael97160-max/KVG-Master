package com.numerikcloud.infirmiere;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
  private static final String URL="https://script.google.com/macros/s/AKfycbzOBx77cV58KWbfK_O62SltpLSvRYGEw3sakHkRM69iS9ImRuiKU4zP0vo2JpiWzzn9pA/exec";
  private WebView web;
  private FrameLayout container;
  private LinearLayout splash, offline;
  private android.webkit.ValueCallback<Uri[]> fileCallback;
  private boolean failed=false;
  private long backPressed=0;
  private int dp(int x){ return (int)(x*getResources().getDisplayMetrics().density+0.5f); }
  @SuppressLint("SetJavaScriptEnabled")
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
    fullScreen();
    container=new FrameLayout(this);
    container.setBackgroundColor(Color.WHITE);
    web=new WebView(this);
    web.setBackgroundColor(Color.WHITE);
    container.addView(web,new FrameLayout.LayoutParams(-1,-1));
    makeSplash();
    makeOffline();
    setContentView(container);
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
    s.setJavaScriptCanOpenWindowsAutomatically(true);
    s.setSupportMultipleWindows(false);
    s.setAllowFileAccess(false); s.setAllowContentAccess(true);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
    s.setBuiltInZoomControls(false);
    CookieManager.getInstance().setAcceptCookie(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
    if(Build.VERSION.SDK_INT>=26) s.setSafeBrowsingEnabled(true);
    web.setWebViewClient(new WebViewClient(){
      @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap favicon){failed=false;offline.setVisibility(View.GONE);splash.setVisibility(View.VISIBLE);}
      @Override public void onPageFinished(WebView v,String url){splash.setVisibility(View.GONE);if(failed)offline.setVisibility(View.VISIBLE);CookieManager.getInstance().flush();}
      @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError err){if(req.isForMainFrame())failed=true;}
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){return handle(req.getUrl());}
      @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return handle(Uri.parse(url));}
    });
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onShowFileChooser(WebView v,android.webkit.ValueCallback<Uri[]> cb,FileChooserParams p){
        if(fileCallback!=null)fileCallback.onReceiveValue(null);
        fileCallback=cb;
        try {Intent i=p.createIntent(); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,301); return true;}
        catch(Exception e){ fileCallback=null;Toast.makeText(MainActivity.this,"Fichier indisponible",Toast.LENGTH_SHORT).show();return false;}
      }
    });
    web.setDownloadListener((url,ua,disp,mime,size)->{
      try{
        if(!url.startsWith("https://"))return;
        DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url));
        r.setMimeType(mime);r.setTitle(URLUtil.guessFileName(url,disp,mime));
        r.addRequestHeader("User-Agent",ua);
        String cookie=CookieManager.getInstance().getCookie(url);
        if(cookie!=null)r.addRequestHeader("Cookie",cookie);
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setDestinationInExternalFilesDir(MainActivity.this,Environment.DIRECTORY_DOWNLOADS,URLUtil.guessFileName(url,disp,mime));
        ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
        Toast.makeText(MainActivity.this,"Téléchargement en cours",Toast.LENGTH_SHORT).show();
      }catch(Exception e){Toast.makeText(MainActivity.this,"Téléchargement impossible",Toast.LENGTH_SHORT).show();}
    });
    if(b==null)web.loadUrl(URL);else web.restoreState(b);
  }
  private TextView text(String value,int size,boolean bold){
    TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(35,43,56));t.setGravity(Gravity.CENTER);
    if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;
  }
  private void makeSplash(){
    splash=new LinearLayout(this);splash.setGravity(Gravity.CENTER);splash.setOrientation(LinearLayout.VERTICAL);splash.setBackgroundColor(Color.rgb(249,249,248));
    ImageView image=new ImageView(this);image.setImageResource(R.drawable.logo_numerikcloud);image.setAdjustViewBounds(true);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    splash.addView(image,new LinearLayout.LayoutParams(dp(300),dp(300)));
    splash.addView(text("INFIRMIÈRE",23,true),new LinearLayout.LayoutParams(-1,dp(45)));
    splash.addView(text("Vos archives dans votre cloud",14,false),new LinearLayout.LayoutParams(-1,dp(38)));
    ProgressBar bar=new ProgressBar(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(40),dp(40));p.topMargin=dp(24);splash.addView(bar,p);
    container.addView(splash,new FrameLayout.LayoutParams(-1,-1));
  }
  private void makeOffline(){
    offline=new LinearLayout(this);offline.setOrientation(LinearLayout.VERTICAL);offline.setGravity(Gravity.CENTER);offline.setPadding(dp(20),dp(20),dp(20),dp(20));offline.setBackgroundColor(Color.WHITE);
    offline.addView(text("Connexion indisponible",22,true));
    offline.addView(text("Vérifiez votre connexion Internet.",16,false));
    Button reload=new Button(this);reload.setText("Réessayer");reload.setOnClickListener(v->{offline.setVisibility(View.GONE);web.loadUrl(URL);});
    offline.addView(reload);offline.setVisibility(View.GONE);container.addView(offline,new FrameLayout.LayoutParams(-1,-1));
  }
  private boolean handle(Uri u){
    String sc=u.getScheme();
    if(sc==null||"https".equalsIgnoreCase(sc)||"about".equalsIgnoreCase(sc))return false;
    if("http".equalsIgnoreCase(sc))return true;
    try{Intent i=new Intent(Intent.ACTION_VIEW,u);startActivity(i);}catch(Exception ignored){}
    return true;
  }
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==301&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}}
  @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else if(System.currentTimeMillis()-backPressed<1800)super.onBackPressed();else{backPressed=System.currentTimeMillis();Toast.makeText(this,"Appuyez à nouveau pour quitter",Toast.LENGTH_SHORT).show();}}
  @Override protected void onPause(){super.onPause();web.onPause();CookieManager.getInstance().flush();}
  @Override protected void onResume(){super.onResume();web.onResume();fullScreen();}
  @Override protected void onSaveInstanceState(Bundle b){web.saveState(b);super.onSaveInstanceState(b);}
  @Override protected void onDestroy(){web.destroy();super.onDestroy();}
  private void fullScreen(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);}
}