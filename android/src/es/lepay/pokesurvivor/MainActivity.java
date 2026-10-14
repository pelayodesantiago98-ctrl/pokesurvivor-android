package es.lepay.pokesurvivor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * PokéSurvivor (un jugador, sin conexión): el juego web dentro de un WebView.
 * Los ficheros salen de assets/www y se sirven como https://appassets.androidplatform.net/
 * para que el juego vea un origen https normal (localStorage, fuentes, etc.).
 * Cualquier otra dirección se bloquea: la app no usa internet.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/index.html";
    private static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put("html", "text/html");
        MIME.put("js", "application/javascript");
        MIME.put("css", "text/css");
        MIME.put("json", "application/json");
        MIME.put("png", "image/png");
        MIME.put("jpg", "image/jpeg");
        MIME.put("gif", "image/gif");
        MIME.put("webp", "image/webp");
        MIME.put("svg", "image/svg+xml");
        MIME.put("ico", "image/x-icon");
        MIME.put("ttf", "font/ttf");
        MIME.put("woff", "font/woff");
        MIME.put("woff2", "font/woff2");
        MIME.put("mp3", "audio/mpeg");
        MIME.put("ogg", "audio/ogg");
        MIME.put("wav", "audio/wav");
    }

    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String scheme = req.getUrl().getScheme();
                if (!"http".equals(scheme) && !"https".equals(scheme)) return null;   // data:, blob:...
                if (!HOST.equals(req.getUrl().getHost())) {
                    // Sin internet: todo lo de fuera se corta.
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Offline", null, null);
                }
                String path = req.getUrl().getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                return asset(path.substring(1));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) {
                    // Dentro de la app sólo existe el juego (las páginas de guía/contacto de la web no van en el APK).
                    String p = u.getPath();
                    return !(p == null || p.equals("/") || p.equals("/index.html"));
                }
                // Enlaces de fuera (créditos, GitHub...): al navegador del móvil.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException e) {
                    // sin navegador: no se hace nada
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.i("PokeSurvivor", m.messageLevel() + " " + m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }

            // alert()/confirm() con el nombre del juego, no "La página https://appassets... dice:".
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                dialog(message, result, false);
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                dialog(message, result, true);
                return true;
            }
        });
        // Depuración desde el PC: chrome://inspect (o adb forward + DevTools).
        WebView.setWebContentsDebuggingEnabled(true);
        setContentView(web);
        hideBars();
        if (state != null) web.restoreState(state);
        else web.loadUrl(HOME);
    }

    private void dialog(String message, final JsResult result, boolean cancelable) {
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("PokéSurvivor")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                .setOnCancelListener(d -> result.cancel());
        if (cancelable) b.setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel());
        b.show();
    }

    private WebResourceResponse asset(String path) {
        try {
            InputStream in = getAssets().open("www/" + path);
            int dot = path.lastIndexOf('.');
            String ext = dot >= 0 ? path.substring(dot + 1).toLowerCase() : "";
            String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "no-cache");
            return new WebResourceResponse(mime, "utf-8", 200, "OK", headers, in);
        } catch (IOException e) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
        }
    }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Al salir de la app, el juego se pausa como al cambiar de pestaña (visibilitychange).
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        hideBars();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Atrás = Escape: cierra ventanas / pausa la partida, como en el PC.
        web.evaluateJavascript(
                "(function(){var o={key:'Escape',code:'Escape',keyCode:27,which:27,bubbles:true};" +
                "window.dispatchEvent(new KeyboardEvent('keydown',o));" +
                "window.dispatchEvent(new KeyboardEvent('keyup',o));})()", null);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
