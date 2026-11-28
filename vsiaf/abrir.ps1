$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'iniciar.ps1')
$python = (Get-Command pythonw.exe -ErrorAction Stop).Source
$programa = Join-Path $PSScriptRoot 'activos.py'
$existente = Get-CimInstance Win32_Process -Filter "name='pythonw.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($programa) }
if (!$existente) {
    Start-Process -FilePath $python -ArgumentList @(('"' + $programa + '"')) -WorkingDirectory $PSScriptRoot
}
