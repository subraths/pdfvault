# CI/CD

Three GitHub Actions workflows live in [`.github/workflows/`](workflows/):

| Workflow | File | Trigger | What it does |
| --- | --- | --- | --- |
| **CI** | `ci.yml` | every push to `main`, every PR | Backend typecheck + `serverless package`; Android `assembleDebug`; desktop `compileKotlin`. No secrets needed. |
| **Backend Deploy** | `backend-deploy.yml` | push to `main` touching `backend/**`, or manual dispatch | Smoke-tests against a throwaway Mongo DB, then `serverless deploy`. |
| **Android Release** | `android-release.yml` | push a `v*` tag, or manual dispatch | Builds a release APK and attaches it to a **GitHub Release**. |

> This repo isn't a git repo yet. To use these: `git init && git add . && git commit -m "…"`, create a
> GitHub repo, `git push`, then add the secrets/variables below under **Settings → Secrets and variables → Actions**.

## Required configuration

### Backend Deploy — repository **secrets**

| Secret | Notes |
| --- | --- |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | IAM user allowed to deploy (Lambda, API Gateway, CloudFormation, S3, IAM, logs). |
| `MONGODB_URI` | Atlas connection string. **Allow-list GitHub runners** or use `0.0.0.0/0` on the Atlas project. |
| `JWT_SECRET` | Long random string. |
| `ENCRYPTION_KEY` | `openssl rand -base64 32`. |
| `MONGODB_DB` *(optional)* | Defaults to `pdfvault`. |
| `JWT_TTL_SECONDS` *(optional)* | Defaults to `2592000` (30 days). |

The smoke-test step also uses `MONGODB_URI`/`JWT_SECRET`/`ENCRYPTION_KEY`; it forces the DB name
`pdfvault_smoketest` and drops it afterwards, so your real data is untouched. (If you'd rather CI
never touch Atlas, delete the "Smoke test" step from `backend-deploy.yml`.)

Default stage is `prod`, region `ap-south-1` — override via the **Run workflow** dispatch inputs.
Optionally create a `production` environment (Settings → Environments) to require a manual approval
before each deploy.

### Android Release — repository **secrets** (all optional)

Set all four to sign with your real upload key. Omit them and the APK is signed with the debug key
(installable, fine for testing, but not a store/upgrade-safe key).

| Secret | How to produce |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 upload.jks` (the whole keystore, base64-encoded). |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Generate a keystore once with:

```sh
keytool -genkeypair -v -keystore upload.jks -alias pdfvault \
  -keyalg RSA -keysize 2048 -validity 10000
```

### Android Release — repository **variable** (optional)

| Variable | Notes |
| --- | --- |
| `BACKEND_BASE_URL` | The deployed API base URL. Baked into the APK's `BuildConfig`. Leave unset → the app is local-only (cloud-sync UI hidden). It's a public URL, so it's a *variable*, not a secret. |

## Cutting an Android release

```sh
# bump versionCode/versionName in app/build.gradle.kts first, then:
git tag v0.1.0
git push origin v0.1.0
```

The workflow builds the APK and publishes it (with auto-generated release notes) to
**Releases → PdfVault v0.1.0**, with `PdfVault-v0.1.0.apk` attached. You can also run it manually
from the **Actions** tab (**Android Release → Run workflow**) and type the tag name.

## Deploying the backend manually

**Actions → Backend Deploy → Run workflow**, choose the stage (`prod`/`dev`) and region. Or just
push a `backend/**` change to `main`.
