$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Python = "E:\anaconda3\envs\envs2\python.exe"

if (-not (Test-Path -LiteralPath $Python)) {
    throw "Python not found: $Python"
}

Set-Location -LiteralPath $Root
& $Python .\vsinger_sudoku.py
