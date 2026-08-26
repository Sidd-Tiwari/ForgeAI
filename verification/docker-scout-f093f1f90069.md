# Docker Scout verification — `f093f1f90069`

The three release images were scanned locally on 2026-08-26 with Docker Scout CLI 1.23.1. The scan was restricted to critical and high severity vulnerabilities and returned no vulnerable package for each image.

## Commands

```powershell
docker scout cves forgeai-gateway:git-f093f1f90069 --only-severity critical,high --format only-packages --only-vuln-packages
docker scout cves forgeai-autopr:git-f093f1f90069 --only-severity critical,high --format only-packages --only-vuln-packages
docker scout cves forgeai-dashboard:git-f093f1f90069 --only-severity critical,high --format only-packages --only-vuln-packages
```

## Results

| Image | Indexed packages | Critical | High | Docker Scout result |
|---|---:|---:|---:|---|
| `forgeai-gateway:git-f093f1f90069` | 336 | 0 | 0 | No vulnerable package detected |
| `forgeai-autopr:git-f093f1f90069` | 77 | 0 | 0 | No vulnerable package detected |
| `forgeai-dashboard:git-f093f1f90069` | 26 | 0 | 0 | No vulnerable package detected |

The immutable image digests and corresponding SPDX documents are recorded in [`release-manifest.json`](artifacts/release-manifest.json).
