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

function Export-Attestations {
    param(
        [string]$OciArchive,
        [string]$SpdxDestination,
        [string]$ProvenanceDestination
    )

    $extractRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("forgeai-attestation-" + [guid]::NewGuid())
    $null = New-Item -ItemType Directory -Path $extractRoot
    try {
        & tar -xf $OciArchive -C $extractRoot
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to extract OCI attestation archive"
        }
        $rootIndex = Get-Content -Raw -LiteralPath (Join-Path $extractRoot "index.json") | ConvertFrom-Json
        $pending = New-Object System.Collections.Queue
        foreach ($descriptor in $rootIndex.manifests) {
            $pending.Enqueue($descriptor)
        }
        $foundSpdx = $false
        $foundProvenance = $false
        while ($pending.Count -gt 0) {
            $descriptor = $pending.Dequeue()
            $documentDigest = $descriptor.digest.Split(":", 2)[1]
            $documentPath = Join-Path $extractRoot "blobs/sha256/${documentDigest}"
            $document = Get-Content -Raw -LiteralPath $documentPath | ConvertFrom-Json
            if ($document.manifests) {
                foreach ($nestedDescriptor in $document.manifests) {
                    $pending.Enqueue($nestedDescriptor)
                }
                continue
            }
            if ($descriptor.annotations.'vnd.docker.reference.type' -ne "attestation-manifest") {
                continue
            }
            $manifest = $document
            foreach ($layer in $manifest.layers) {
                $layerDigest = $layer.digest.Split(":", 2)[1]
                $statementPath = Join-Path $extractRoot "blobs/sha256/${layerDigest}"
                $statement = Get-Content -Raw -LiteralPath $statementPath | ConvertFrom-Json
                if ($statement.predicateType -match "spdx") {
                    $statement.predicate |
                        ConvertTo-Json -Depth 100 |
                        Set-Content -Encoding UTF8 -LiteralPath $SpdxDestination
                    $foundSpdx = $true
                }
                if ($statement.predicateType -match "slsa.dev/provenance") {
                    $statement |
                        ConvertTo-Json -Depth 100 |
                        Set-Content -Encoding UTF8 -LiteralPath $ProvenanceDestination
                    $foundProvenance = $true
                }
            }
        }
        if (-not $foundSpdx) {
            throw "BuildKit did not emit an SPDX SBOM attestation"
        }
        if (-not $foundProvenance) {
            throw "BuildKit did not emit a SLSA provenance attestation"
        }
    }
    finally {
        Remove-Item -Recurse -Force -LiteralPath $extractRoot
    }
}

foreach ($service in $services) {
    $image = "forgeai-${service}:git-${Tag}"
    $metadataPath = Join-Path $artifactRoot "${service}-build-metadata.json"
    $sbomPath = Join-Path $artifactRoot "${service}.spdx.json"
    $provenancePath = Join-Path $artifactRoot "${service}-provenance.intoto.json"
    $ociArchive = Join-Path $artifactRoot "${service}-attested.oci.tar"

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

    & docker buildx build `
        --provenance=mode=max `
        --sbom=true `
        --output "type=oci,dest=${ociArchive}" `
        $service
    if ($LASTEXITCODE -ne 0) {
        throw "OCI attestation export failed for ${service}"
    }
    Export-Attestations `
        -OciArchive $ociArchive `
        -SpdxDestination $sbomPath `
        -ProvenanceDestination $provenancePath
    Remove-Item -Force -LiteralPath $ociArchive

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
        provenance = $provenancePath
        build_metadata = $metadataPath
    }
}

$manifestPath = Join-Path $artifactRoot "release-manifest.json"
$releaseImages | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -LiteralPath $manifestPath
$releaseImages | Format-Table service, tag, immutable_ref, build_digest -AutoSize
Write-Output "RELEASE_MANIFEST=${manifestPath}"
