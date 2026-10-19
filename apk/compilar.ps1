param(
    [string]$Gradle = "$PSScriptRoot\gradlew.bat",
    [string]$Jdk = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
if ($Jdk) { $env:JAVA_HOME = $Jdk }
if ($AndroidSdk) { $env:ANDROID_HOME = $AndroidSdk }
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/jlink.exe'))) {
    throw 'Configura JAVA_HOME con un JDK completo (recomendado: 21), o usa -Jdk. Un JRE no es suficiente.'
}
if (-not $env:ANDROID_HOME -and -not (Test-Path "$PSScriptRoot/local.properties")) {
    throw 'Configura ANDROID_HOME o usa -AndroidSdk con la ruta del SDK.'
}
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path 'app/src/main/assets/bosque_alerta.tflite')) { throw 'Primero entrenar y exportar el modelo.' }
    & $Gradle assembleDebug lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación o la revisión Android. El entregable anterior no fue reemplazado.' }
    New-Item -ItemType Directory -Force 'entregables' | Out-Null
    Copy-Item 'app/build/outputs/apk/debug/app-debug.apk' 'entregables/BosqueAlerta-debug.apk'
    Write-Host 'APK actualizado: apk/entregables/BosqueAlerta-debug.apk'
} finally { Pop-Location }
