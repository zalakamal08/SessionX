# SessionX — Header-Based Authorization Bypass Tester

> **"You configure which headers matter. SessionX handles the rest."**

SessionX is a professional Burp Suite extension that automates **header substitution authorization bypass testing** — the same workflow you'd follow manually with Autorize, but with surgical multi-header control.

## 🎯 The Pain Point It Solves

When testing authorization, you always have a high-priv session and you want to know:
- Which endpoints respond the same way with a **low-priv token**?
- Which endpoints still respond with a **blank/removed header** (unauthenticated)?
- Which endpoints let me **add a custom header** to bypass controls?

Doing this manually means copy-pasting tokens, repeating requests, and eyeballing responses. SessionX does it automatically for every proxied request.

## ✨ Key Features

### Multi-Header Interception Rules
Select **multiple headers** simultaneously, each with its own mode:

| Mode | What it does |
|---|---|
| **Replace** | Swap the header value (e.g `Authorization: Bearer lowprivtoken`) |
| **Remove** | Delete the header entirely — tests unauthenticated access |
| **Add** | Inject a new header that wasn't in the original |

### Autorize-Style Results Table
Every intercepted request gets a comparison row:

| # | Method | URL | Orig. Status | Orig. Len | Mod. Status | Mod. Len | Result |
|---|---|---|---|---|---|---|---|
| 1 | GET | /api/users/profile | 200 | 1842 | 200 | 1842 | 🔴 Vulnerable |
| 2 | POST | /api/admin/action | 200 | 412 | 403 | 89 | 🟢 Enforced |
| 3 | GET | /api/items | 200 | 3200 | 200 | 3100 | 🟡 Interesting |

### Color-Coded Vulnerability Status
- **🔴 Vulnerable** — Same status class + content-length within 5% → authorization **not enforced**
- **🟢 Enforced** — Significant difference in status or length → authorization **is enforced**
- **🟡 Interesting** — Status matches but length differs 5–20% → **needs manual review**
- **⏳ Pending** — Modified request still in flight

### Per-Request Detail View
Click any row to see:
- **Original Tab**: original request & response
- **Modified Tab**: modified request & response (with what headers were changed)

### Quick-Add Common Headers
One-click to add `Authorization`, `Cookie`, `X-Api-Key`, `X-Auth-Token`, `X-User-Id`, `X-Forwarded-For` to your rule set.

### Smart Filtering
- Automatically skips static assets (`.js`, `.css`, `.png`, `.jpg`, `.ico`, fonts)
- Optionally intercept Repeater requests too
- Toggle ON/OFF without losing results

## 📦 Installation

1. Download the latest `SessionX.jar` from the [Releases page](https://github.com/zalakamal08/SessionX/releases/latest)
2. Open Burp Suite
3. Go to **Extensions → Installed → Add**
4. Set *Extension Type* to **Java**, browse to `SessionX.jar`, click **Next**
5. The **SessionX** tab appears in Burp's top bar

## 🚀 Quick Start

1. Open the **SessionX** tab → click **Configuration**
2. Use the quick-add buttons to add `Authorization`
3. In the Mode column, choose `Replace`
4. In the Replacement Value column, put your low-priv token: `Bearer lowprivtoken`
5. *(Optional)* Add another row with `Cookie` → Mode: `Remove` to test unauthenticated
6. Click **Apply Rules**
7. Go back to the first tab, click **SessionX: OFF** to toggle it **ON**
8. Browse your target app as a high-privilege user
9. Watch the table fill up — 🔴 rows need investigation!

## 🛠️ Building from Source

Requires JDK 17+ and Maven. Build is automatically run by GitHub Actions on every push.

```bash
git clone https://github.com/zalakamal08/SessionX.git
cd SessionX
mvn clean package -DskipTests
# Output: target/SessionX.jar
```

## 📋 How Vulnerability Detection Works

For each request, SessionX:
1. Lets the original request pass through Burp normally
2. Asynchronously replays a **modified copy** with your header rules applied
3. Compares the responses:
   - **Status class match** (both 2xx, both 4xx, etc.)
   - **Content-length difference** (as a % of original length)

| Condition | Status |
|---|---|
| Same status class AND length diff ≤ 5% | 🔴 Vulnerable |
| Same status class AND length diff 5–20% | 🟡 Interesting |
| Status class differs OR length diff > 20% | 🟢 Enforced |

## 🤝 Contributing

1. Open an issue describing the bug or feature
2. Fork the repo and create a branch: `git checkout -b feature/your-feature`
3. Commit: `git commit -m 'feat: description'`
4. Push and open a Pull Request

## 📄 License

Open-source. See [LICENSE](LICENSE) for details.
