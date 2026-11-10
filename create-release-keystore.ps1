param(
    [string]$KeytoolPath = "C:\Program Files\Java\jdk-17\bin\keytool.exe",
    [string]$KeystorePath = "F:\keys\my-release-key.jks",
    [string]$Alias = "busapp"
)

if (!(Test-Path $KeytoolPath)) {
    Write-Error "keytool not found: $KeytoolPath"
    exit 1
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $KeystorePath) | Out-Null

& $KeytoolPath -genkeypair -v `
    -keystore $KeystorePath `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000

if ($LASTEXITCODE -ne 0) {
    Write-Error "Keystore generation failed."
    exit $LASTEXITCODE
}

Write-Output "Keystore created: $KeystorePath"
Write-Output "Next: fill keystore.properties storePassword/keyPassword and run .\\gradlew.bat :app:bundleRelease"
