# SessionX — Unified Session Handler for Burp Suite

> **"You configure it once. Then it just works."**

SessionX is a modern Burp Suite extension that eliminates the session handling pain every pentester faces on every engagement. Instead of stacking 3 extensions and writing regex config for 45 minutes, you describe your session once and SessionX handles the rest — token extraction, multi-location injection, refresh triggering, loop prevention and structured logging.

---

## ✨ Key Features

### 1. Unified Multi-Token Profile
One profile covers your entire authentication surface:
- **Bearer tokens** in `Authorization` headers
- **Session cookies** (injected into `Cookie` header)
- **CSRF tokens** (extracted from HTML/JSON, injected in headers or body)
- **Refresh tokens** (separate credential for re-authentication)
- **Custom tokens** anywhere you need

### 2. Multi-Step Login Sequence
Most real-world auth flows aren't a single POST. SessionX handles them in order:
```
Step 1: GET /login           → extract CSRF token
Step 2: POST /login          → use {{step0:CSRF}} in body → get session cookie
Step 3: POST /api/auth/token → use cookie → get Bearer token
```

### 3. URL Scope Control (Whitelist / Blacklist)
Control exactly where the extension fires:
- **Whitelist mode:** only process URLs matching your patterns
- **Blacklist mode:** process everything except matched URLs
- Wildcard support: `*.target.com/api/*`
- Per-rule enable/disable toggles with comments

### 4. Explainable Activity Log
Every action is visible and explained:
```
[22:31:44] [REFRESH]  401 from GET /api/users — re-running login for "API Auth"
[22:31:44] [INFO]     Step 1/2 — GET https://target.com/login → 200 OK
[22:31:44] [TOKEN]    CSRF extracted (step 1) → a3f9...cd12
[22:31:45] [TOKEN]    SESSION_COOKIE extracted → PHPSESSID=abc123...
[22:31:45] [INFO]     Token store updated — injection active
```

### 5. Profile Export / Import
Session configs are portable JSON. Share across your team or reuse across engagements. Import takes 5 seconds.

### 6. In-Memory Token Replacement
Unlike Burp macros which fire a full login request for **every single proxied request**, SessionX extracts tokens once, stores them in memory, and injects the stored value into subsequent requests. Zero duplicate requests.

### 7. Proper JSON + XML Body Injection
Not just headers and cookies — SessionX correctly replaces tokens inside JSON request bodies using Jackson, XML bodies using DOM parsing, and form-encoded bodies with URL-aware key replacement.

---

## 📦 Installation

1. Download the latest `SessionX.jar` from the [Releases page](https://github.com/zalakamal08/SessionX/releases/latest)
2. Open Burp Suite
3. Go to **Extensions → Installed → Add**
4. Set Extension Type to **Java**, browse to `SessionX.jar`, click **Next**
5. A **"SessionX"** tab appears in the top bar

---

## 🛠️ Building from Source

Requires JDK 17+ and Maven.

```bash
# Clone
git clone https://github.com/zalakamal08/SessionX.git
cd SessionX

# Build fat JAR
mvn clean package -DskipTests

# Output: target/sessionx-1.0.jar
```

---

## 🚀 Quick Start — DVWA Example

1. **Create a new profile** — click `+ New Profile` in the sidebar
2. **Login Sequence tab:**
   - Step 1: `GET https://dvwa.local/login.php` → Label: "Get CSRF"
   - Step 2: `POST https://dvwa.local/login` → Body: `username=admin&password=password&user_token={{step0:CSRF}}&Login=Login`
3. **Tokens tab:**
   - Row 1: Type=CSRF, Extract From=Response Body (HTML), Regex=`user_token.*?value="([^"]+)"`, Step=0, Inject At=Body Form, Key=user_token
   - Row 2: Type=Session Cookie, Extract From=Response Cookie, Regex=`PHPSESSID=([^;]+)`, Step=1, Inject At=Cookie, Key=PHPSESSID
4. **Scope tab:** Whitelist → `*.dvwa.local/*`
5. **Error/Refresh tab:** Status code=`302`, Body keyword=`login.php`, Exclude URL=`/login`
6. Click **Save**, toggle to **ACTIVE**
7. Browse DVWA — watch the Activity Log show extractions + injections in real time

---

## 📋 Comparison vs Existing Tools

| Feature | Burp Macros | ATOR | SH++ | **SessionX** |
|---------|------------|------|------|-------------|
| In-memory token replacement | ❌ | ✅ | ❌ | ✅ |
| JSON body injection | ❌ | ✅ | Partial | ✅ |
| Multi-token unified profile | ❌ | ❌ | Partial | ✅ |
| Multi-step login sequence | Via macros | ✅ | ❌ | ✅ |
| Whitelist / Blacklist scope | ❌ | ❌ | ❌ | ✅ |
| Explainable activity log | ❌ | ❌ | ❌ | ✅ |
| Profile export / import | ❌ | ❌ | ❌ | ✅ |

---

## 🤝 Contributing

Pull requests are welcome!

1. Open an issue describing the bug or feature
2. Fork the repo and create a branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'feat: add your feature'`
4. Push: `git push origin feature/your-feature`
5. Open a Pull Request

---

## 📄 License

Open-source. See [LICENSE](LICENSE) for details.
