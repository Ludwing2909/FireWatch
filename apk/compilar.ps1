param([string]$Gradle = "$PSScriptRoot\gradlew.bat")
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path 'app/src/main/assets/bosque_alerta.tflite')) { throw 'Primero entrenar y exportar el modelo.' }
    & $Gradle assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación Android' }
    New-Item -ItemType Directory -Force 'entregables' | Out-Null
    Copy-Item 'app/build/outputs/apk/debug/app-debug.apk' 'entregables/BosqueAlerta-debug.apk'
} finally { Pop-Location }
