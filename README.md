# PokéSurvivor para Android (un jugador)

APK sin conexión del juego web [JPTER78/pokesurvivor](https://github.com/JPTER78/pokesurvivor) (MIT).
Es el mismo juego (HTML/JS original) dentro de un WebView, con lo online quitado.

## Qué cambia respecto a la web
- Sin nube: `firebase-config.js` va a `null` → el juego arranca en su modo local y guarda en el móvil.
- Sin cuenta: la primera vez sólo pide el nombre (`web/android.js` crea la cuenta local con una contraseña fija).
- Fuera: Amigos/cooperativo, Ranking, Apoyar, Salir, el actualizador y el service worker (`web/android.css`).
- La app bloquea cualquier petición a internet; los ficheros se sirven desde el APK como
  `https://appassets.androidplatform.net/`.
- Pantalla completa, horizontal, pantalla siempre encendida. Botón Atrás = Escape (pausa / cerrar ventanas).

## Compilar
```powershell
.\build.ps1             # dist\PokeSurvivor-1.0-<commit>.apk
.\build.ps1 -Update     # antes trae la última versión del juego web (git pull en upstream\)
.\build.ps1 -Install    # y lo instala por adb en el dispositivo conectado
```
Sin Gradle: aapt2 + javac + d8 + zipalign + apksigner. Toolchain portable en
`C:\Users\lepay\android-tools` (JDK 17, SDK 34). Firma: `android-tools\pokesurvivor.jks`
(no perderla: con otra firma no se puede actualizar encima sin desinstalar y perder la partida).

`upstream\` es un clon del repo web; el build copia de ahí `dev.html` (scripts sin ofuscar),
`js\`, `css\` y `assets\` y aplica los parches. Si el autor cambia `main.js` o `dev.html`
de forma que los parches no encajan, el build se para con un mensaje.

## Depurar
La consola JS sale en logcat con la etiqueta `PokeSurvivor`. DevTools: `adb forward tcp:9333
localabstract:webview_devtools_remote_<pid>` y abrir `http://127.0.0.1:9333/json/list`.
