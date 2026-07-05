# CI/CD

Three GitHub Actions workflows live in [`.github/workflows/`](workflows/):

| Workflow | File | Trigger | What it does |
| --- | --- | --- | --- |
| **CI** | `ci.yml` | every push to `main`, every PR | Backend typecheck + `serverless package`; Android `assembleDebug`; desktop `compileKotlin`. No secrets needed. |
| **Backend Deploy** | `backend-deploy.yml` | push to `main` touching `backend/**`, or manual dispatch | Typecheck + smoke test against Atlas (throwaway DB), then `serverless deploy`. |
| **Android Release** | `android-release.yml` | push a `v*` tag, or manual dispatch | Builds a release APK and attaches it to a **GitHub Release**. |

> This repo isn't a git repo yet. To use these: `git init && git add . && git commit -m "…"`, create a
> GitHub repo, `git push`, then add the secrets/variables below under **Settings → Secrets and variables → Actions**.

## Required configuration

### Backend Deploy — repository **secrets**

| Secret | Notes |
| --- | --- |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | IAM user allowed to deploy — see **IAM permissions** below. |
| `MONGODB_URI` | Atlas connection string, baked into the deployed Lambda's env. |
| `JWT_SECRET` | Long random string. |
| `ENCRYPTION_KEY` | `openssl rand -base64 32`. |
| `MONGODB_DB` *(optional)* | Defaults to `pdfvault`. |
| `JWT_TTL_SECONDS` *(optional)* | Defaults to `2592000` (30 days). |

#### IAM permissions for the deploy user

`serverless deploy` provisions via **CloudFormation** and needs to manage S3 (deployment bucket),
Lambda, IAM (the function's execution role), CloudWatch Logs, and API Gateway. A bare user gets
`not authorized to perform: cloudformation:CreateChangeSet` (and would then hit several more).

**Least-privilege (recommended):** attach [`backend/aws/deploy-policy.json`](../backend/aws/deploy-policy.json)
— it's scoped to `pdfvault-backend-*` resources. Apply it to your deploy user (`--user-name` is your
IAM user, e.g. `pdfvauld-lambda-backend`):

```sh
aws iam put-user-policy \
  --user-name pdfvauld-lambda-backend \
  --policy-name pdfvault-serverless-deploy \
  --policy-document file://backend/aws/deploy-policy.json
```

If a later deploy still reports one missing action, add that exact action to the JSON and re-apply
(CloudFormation surfaces them one stack-event at a time).

**Quick unblock (broad):** if you just want it deploying now, attach the AWS-managed
`AdministratorAccess` policy to the user instead, and tighten later:

```sh
aws iam attach-user-policy --user-name pdfvauld-lambda-backend \
  --policy-arn arn:aws:iam::aws:policy/AdministratorAccess
```

**Smoke tests run in two places:**
- **CI (`ci.yml`, every PR):** the 18-assertion `scripts/smoke.mjs` runs against an ephemeral
  `mongo:7` service container — no secrets, never touches Atlas, works on fork PRs.
- **Deploy (`backend-deploy.yml`):** the same smoke test runs against **real Atlas** using your
  secrets, then deploys only if it passes. It forces the DB name `pdfvault_smoketest` and drops it
  afterwards, so your real data is untouched — and it doubles as a check that the deployed Lambda's
  Atlas creds actually connect. (This is why the deploy runner needs Atlas Network Access; you've set
  `0.0.0.0/0`.)

> ⚠️ **Atlas Network Access for the deployed Lambda.** `serverless deploy` itself doesn't connect to
> Mongo, but the *running* Lambda does. Public Lambdas egress from rotating AWS IPs, so add
> `0.0.0.0/0` to your Atlas project's **Network Access** list (or set up VPC + NAT + PrivateLink for a
> fixed IP). Without this the deploy succeeds but the API returns 500s at runtime — the same
> `SSL alert number 80` you'd see for a blocked IP.

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
