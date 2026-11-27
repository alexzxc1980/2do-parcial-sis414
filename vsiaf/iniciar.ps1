$ErrorActionPreference = 'Stop'
$programa = Join-Path $PSScriptRoot 'conectar.py'
$existente = Get-CimInstance Win32_Process -Filter "name='python.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($programa) }
if ($existente) { Write-Output 'La conexión con FoxPro ya está funcionando'; exit 0 }
$python = (Get-Command python.exe -ErrorAction Stop).Source
$carpeta = Join-Path $env:LOCALAPPDATA 'SIS414/segundo-parcial'
New-Item -ItemType Directory -Force -Path $carpeta | Out-Null
$proceso = Start-Process -FilePath $python -ArgumentList @(('"' + $programa + '"')) -WindowStyle Hidden -PassThru -WorkingDirectory $PSScriptRoot -RedirectStandardOutput (Join-Path $carpeta 'conexion.log') -RedirectStandardError (Join-Path $carpeta 'errores.log')
Write-Output "Conexión FoxPro iniciada: $($proceso.Id)"
