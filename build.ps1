<#
  PokéSurvivor para Android (un jugador, sin conexión).
    .\build.ps1            -> dist\PokeSurvivor-<versión>.apk
    .\build.ps1 -Update    -> antes hace git pull del juego web (upstream\)
    .\build.ps1 -Install   -> al acabar lo instala por adb en el móvil conectado
  Toolchain portable en C:\Users\lepay\android-tools (JDK 17 + SDK 34). Sin Gradle.
#>
param([switch]$Update, [switch]$Install)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$TOOLS = 'C:\Users\lepay\android-tools'
$GIT = 'C:\Users\lepay\3ds-tools\git\cmd\git.exe'
$JDK = "$TOOLS\jdk\bin"
$BT = "$TOOLS\sdk\build-tools\34.0.0"
$JAR = "$TOOLS\sdk\platforms\android-34\android.jar"
$KEY = "$TOOLS\pokesurvivor.jks"          # misma firma siempre: así se puede actualizar encima
$ROOT = $PSScriptRoot
$UP = "$ROOT\upstream"
$B = "$ROOT\build"
$WWW = "$B\assets\www"                 # aapt2 -A empaqueta build\assets -> assets/ del APK
$env:JAVA_HOME = "$TOOLS\jdk"
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Run($exe) {
  & $exe @args
  if ($LASTEXITCODE -ne 0) { throw "Falló: $exe $args" }
}

if ($Update) { Run $GIT -C $UP pull --ff-only }
$commit = (& $GIT -C $UP log -1 --format='%h').Trim()
$stamp = [int64](& $GIT -C $UP log -1 --format='%ct')
$versionCode = [int](($stamp - 1700000000) / 60)      # sube con cada versión del juego web
$versionName = "1.0-$commit"
Write-Host "Juego web: $commit -> versión $versionName ($versionCode)"

# ---------- 1. Web: copiar el juego y adaptarlo a un jugador ----------
if (Test-Path $B) { Remove-Item -Recurse -Force $B }
New-Item -ItemType Directory -Force $WWW, "$B\gen", "$B\classes", "$B\res" | Out-Null
Copy-Item "$UP\css" "$WWW\css" -Recurse
Copy-Item "$UP\js" "$WWW\js" -Recurse
Copy-Item "$UP\assets" "$WWW\assets" -Recurse
Copy-Item "$UP\favicon.ico" $WWW
Remove-Item "$WWW\js\app.min.js", "$WWW\assets\og-image.jpg" -ErrorAction SilentlyContinue
Copy-Item "$ROOT\web\android.js" "$WWW\js\android.js"
Copy-Item "$ROOT\web\android.css" "$WWW\css\android.css"

# Sin nube: el juego entra solo en modo local.
[IO.File]::WriteAllText("$WWW\js\core\firebase-config.js", "/* Android, un jugador: sin nube. */`nG.FIREBASE_CONFIG = null;`n", $utf8)

# Sin service worker de la PWA (la app ya lo tiene todo dentro).
$mainJs = [IO.File]::ReadAllText("$WWW\js\main.js", $utf8)
if (-not $mainJs.Contains("'serviceWorker' in navigator")) { throw 'main.js ya no registra el service worker como se esperaba' }
[IO.File]::WriteAllText("$WWW\js\main.js", $mainJs.Replace("'serviceWorker' in navigator", 'false'), $utf8)

$html = [IO.File]::ReadAllText("$UP\dev.html", $utf8)
$mainTag = '<script defer src="js/main.js"></script>'
if (-not $html.Contains($mainTag)) { throw 'dev.html ya no carga js/main.js como se esperaba' }
$html = $html.Replace($mainTag, "<script defer src=""js/android.js""></script>`n$mainTag")
if ($html -notmatch '</head>') { throw 'dev.html sin </head>' }
$html = $html.Replace('</head>', "<link rel=""stylesheet"" href=""css/android.css"">`n</head>")
$html = [regex]::Replace($html, '<link rel="(preconnect|manifest|canonical)"[^>]*>\s*', '')
$html = [regex]::Replace($html, '<!-- seo:begin -->[\s\S]*?<!-- seo:end -->', '')
[IO.File]::WriteAllText("$WWW\index.html", $html, $utf8)

# ---------- 2. Recursos e icono ----------
$icons = @{ 'mdpi' = 48; 'xhdpi' = 96; 'xxhdpi' = 144; 'xxxhdpi' = 192 }
foreach ($d in $icons.Keys) {
  New-Item -ItemType Directory -Force "$B\res\mipmap-$d" | Out-Null
  Copy-Item "$UP\assets\icons\icon-$($icons[$d]).png" "$B\res\mipmap-$d\ic_launcher.png"
}
Run "$BT\aapt2.exe" compile --dir "$B\res" -o "$B\res.zip"

# ---------- 3. APK sin código: manifiesto + recursos + assets/www ----------
Run "$BT\aapt2.exe" link -o "$B\base.apk" -I $JAR --manifest "$ROOT\android\AndroidManifest.xml" `
  --java "$B\gen" -R "$B\res.zip" --auto-add-overlay `
  --version-code $versionCode --version-name $versionName

# ---------- 4. Java -> dex ----------
$src = @(Get-ChildItem "$ROOT\android\src", "$B\gen" -Recurse -Filter *.java | ForEach-Object FullName)
Run "$JDK\javac.exe" -encoding UTF-8 --release 8 -classpath $JAR -d "$B\classes" -nowarn @src
$cls = @(Get-ChildItem "$B\classes" -Recurse -Filter *.class | ForEach-Object FullName)
Run "$BT\d8.bat" --release --min-api 24 --lib $JAR --output $B @cls

Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$B\base.apk", 'Update')
[void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$B\classes.dex", 'classes.dex')
# Los assets se meten aquí y no con aapt2 -A: en Windows aapt2 guarda las rutas con '\'
# y Android no las encuentra. Las PNG/TTF ya van comprimidas: se guardan tal cual.
$base = (Resolve-Path "$B\assets").Path.Length + 1
foreach ($f in Get-ChildItem "$B\assets" -Recurse -File) {
  $name = 'assets/' + $f.FullName.Substring($base).Replace('\', '/')
  $lvl = if ($f.Extension -in '.png', '.jpg', '.ttf', '.ico') { 'NoCompression' } else { 'Optimal' }
  [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $f.FullName, $name, $lvl)
}
$zip.Dispose()

# ---------- 5. Alinear y firmar ----------
if (-not (Test-Path $KEY)) {
  Run "$JDK\keytool.exe" -genkeypair -keystore $KEY -storepass pokesurvivor -keypass pokesurvivor `
    -alias pokesurvivor -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=PokeSurvivor Android'
}
New-Item -ItemType Directory -Force "$ROOT\dist" | Out-Null
$apk = "$ROOT\dist\PokeSurvivor-$versionName.apk"
Run "$BT\zipalign.exe" -f -p 4 "$B\base.apk" "$B\aligned.apk"
Run "$BT\apksigner.bat" sign --ks $KEY --ks-pass pass:pokesurvivor --ks-key-alias pokesurvivor --out $apk "$B\aligned.apk"
Run "$BT\apksigner.bat" verify $apk
Remove-Item "$apk.idsig" -ErrorAction SilentlyContinue
Write-Host ("OK: {0} ({1:N1} MB)" -f $apk, ((Get-Item $apk).Length / 1MB))

if ($Install) { Run "$TOOLS\sdk\platform-tools\adb.exe" install -r $apk }
