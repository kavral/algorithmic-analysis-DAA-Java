param([switch]$Benchmark, [int]$Forks = 3)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force out | Out-Null
    $sources = @(Get-ChildItem -LiteralPath src -Filter '*.java' | ForEach-Object { $_.FullName })
    & javac -encoding UTF-8 -Xlint:all -d out $sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
    if (Test-Path src/Tests.java) {
        & java -cp out Tests
        if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
    }
    if ($Benchmark) {
        if ($Forks -lt 1) { throw 'Forks must be positive' }
        New-Item -ItemType Directory -Force results/tables, results/plots | Out-Null
        # Each fork has its own JVM, warmup, and output file.
        for ($fork = 1; $fork -le $Forks; $fork++) {
            & java -Xms256m -Xmx256m -cp out Benchmark "results/tables/raw-$fork.csv" $fork
            if ($LASTEXITCODE -ne 0) { throw "Benchmark fork $fork failed" }
        }
        $rawFiles = @(1..$Forks | ForEach-Object { "results/tables/raw-$_.csv" })
        & java -cp out Report $rawFiles
        if ($LASTEXITCODE -ne 0) { throw 'Report generation failed' }
    }
} finally {
    Pop-Location
}
