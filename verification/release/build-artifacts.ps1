[CmdletBinding()]
param(
    [string]$Tag = "",
    [string]$ArtifactDirectory = "verification/artifacts"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Tag)) {
    $Tag = (git rev-parse --short=12 HEAD).Trim()
}
if ($Tag -notmatch "^[0-9a-z._-]+$") {
    throw "Tag must contain only lowercase letters, digits, dots, underscores, or hyphens"
}

$null = New-Item -ItemType Directory -Force -Path $ArtifactDirectory
$artifactRoot = (Resolve-Path -LiteralPath $ArtifactDirectory).Path
$services = @("gateway", "autopr")
$releaseImages = @()

foreach ($service in $services) {
    $image = "forgeai-${service}:git-${Tag}"
    $metadataPath = Join-Path $artifactRoot "${service}-build-metadata.json"
    $sbomPath = Join-Path $artifactRoot "${service}.cdx.json"

    & docker buildx build `
        --load `
        --provenance=mode=max `
        --sbom=true `
        --metadata-file $metadataPath `
        --tag $image `
        $service
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed for ${service}"
    }

    & docker scout sbom --format cyclonedx --output $sbomPath "local://${image}"
    if ($LASTEXITCODE -ne 0) {
        throw "SBOM generation failed for ${service}"
    }

    $inspect = docker image inspect $image | ConvertFrom-Json
    $metadata = Get-Content -Raw -LiteralPath $metadataPath | ConvertFrom-Json
    $repoDigest = $inspect[0].RepoDigests | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($repoDigest)) {
        throw "Docker did not expose an immutable repository digest for ${image}"
    }
    if (-not $metadata.'buildx.build.provenance') {
        throw "BuildKit did not emit provenance metadata for ${image}"
    }
    if ((Get-Item -LiteralPath $sbomPath).Length -eq 0) {
        throw "SBOM output is empty for ${image}"
    }

    $releaseImages += [pscustomobject]@{
        service = $service
        tag = $image
        immutable_ref = $repoDigest
        image_id = $inspect[0].Id
        build_digest = $metadata.'containerimage.digest'
        sbom = $sbomPath
        provenance = $metadataPath
    }
}

$manifestPath = Join-Path $artifactRoot "release-manifest.json"
$releaseImages | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -LiteralPath $manifestPath
$releaseImages | Format-Table service, tag, immutable_ref, build_digest -AutoSize
Write-Output "RELEASE_MANIFEST=${manifestPath}"
