# RPS Arena: Play. Chat. Compete.

A complete real-time multiplayer Rock, Paper, Scissors application with private rooms, server-authoritative combat resolution, and live in-memory match chat.

---

## 1. Network Connectivity Fix & Root Cause

### The Problem
When running on two different devices, Player 2 encountered:
> **"Failed to connect to host"**

### Root Causes Identified
1. **Loopback IP Assumption (`127.0.0.1`)**:
   Player 1 hosted the room server, but the client code defaulted the host address to `127.0.0.1`. On a separate device (Player 2), `127.0.0.1` resolved to Player 2's own device where no server was listening, causing an immediate connection refusal.
2. **Missing Android Cleartext Traffic Permission**:
   On Android 9+ (API 28+), cleartext (non-TLS) TCP and HTTP traffic to local Wi-Fi IPs is blocked by default unless `android:usesCleartextTraffic="true"` or a network security configuration is present.
3. **No LAN IP Visibility**:
   Player 1 had no way of knowing their device's actual Wi-Fi IP address (e.g. `192.168.1.50`), preventing Player 2 from targeting the correct host.

### Implemented Fixes
1. **Dynamic Wi-Fi IP Detection (`NetworkUtils.kt`)**: Automatically retrieves the device's active LAN IPv4 address (e.g. `192.168.1.45`) and displays it in the Lobby.
2. **Full Target Sharing (`IP#RoomCode`)**:
   - Player 1 can tap **"Copy Full (IP#Code)"** (e.g. `192.168.1.45#RPS-4821`).
   - Player 2 can paste this full target directly into the room code field, which automatically extracts the host IP and room code.
3. **Dedicated Host IP Field**: Added an explicit "Player 1 Host IP / Server" field on the Join screen with clear helper text.
4. **Android Network Security Configuration**:
   - Added `android:usesCleartextTraffic="true"` and `@xml/network_security_config` to `AndroidManifest.xml`.
   - Permitted local subnets and development hosts.
5. **Enhanced Node.js Server (`server/src/server.js`)**:
   - Dual-protocol support: Listens on HTTP (port 3001) and TCP Socket (port 8887) bound to `0.0.0.0`.
   - Supports connecting devices across different networks when deployed to a public host (Render, Railway, Fly.io).

---

## 2. Combat Rules

* **🪨 Rock** beats **✂️ Scissors**
* **✂️ Scissors** beats **📄 Paper**
* **📄 Paper** beats **🪨 Rock**
* **Identical choices** produce a **Draw**
* Secrecy guarantee: Choices remain locked and completely hidden until both submit.
* Unlimited rounds with persistent match scores until room exit.

---

## 3. How to Test on Two Different Devices

### Scenario A: Two Devices on the Same Wi-Fi Network
1. **Device A (Player 1 - Host)**:
   - Open RPS Arena.
   - Enter your username (e.g. `Alpha`).
   - Tap **"Create Private Room"**.
   - Note down the **Room Code** (e.g. `RPS-4821`) and the **Host IP** displayed on screen (e.g. `192.168.1.45`).
   - Or simply tap **"Copy Full (IP#Code)"** and send it via chat/message.
2. **Device B (Player 2 - Challenger)**:
   - Open RPS Arena.
   - Enter your username (e.g. `Bravo`).
   - Tap **"Join Existing Room"**.
   - Paste the full target (e.g. `192.168.1.45#RPS-4821`) into the code field, OR enter code `RPS-4821` and set Host IP to `192.168.1.45`.
   - Tap **"Connect & Join Arena"**.
3. **Play & Chat**:
   - Both devices immediately transition to the Arena.
   - Make moves; verify that neither choice is visible until both players submit.
   - Simultaneous reveal displays the round outcome and updates scores on both devices.
   - Send live chat messages and verify instant two-way delivery.

### Scenario B: Devices on Different Networks (via Central Node.js Server)
1. **Start the Central Backend**:
   ```bash
   cd server
   PORT=3001 TCP_PORT=8887 node src/server.js
   ```
   (Or deploy to Render / Railway / Fly.io as described in Section 5).
2. On both Device A and Device B:
   - Enter the public server IP or domain in the Host field.
   - Device A creates a room &rarr; shares the 6-character code.
   - Device B enters the code &rarr; joins across cellular/different Wi-Fis.

### Scenario C: Single Device Testing (Duel Arena Tester)
* Tap **"Duel Arena Tester"** on the home screen to test both players, hidden moves, simultaneous reveals, and live chat immediately on one screen using the `P1 (Host)` / `P2 (Challenger)` switch bar.

---

## 4. Automated Test Suites

### A. Server Test Suite (15 Scenarios)
Run the Node.js test runner:
```bash
npm test
# or
node --test server/test/game.test.js
```
Validates:
1. Room creation
2. Room joining with code
3. 3rd player rejection
4. Live chat message routing
5. Rock beats Scissors
6. Scissors beats Paper
7. Paper beats Rock
8. Identical choices produce a draw
9. Scoreboard synchronization
10. Reconnection within grace period
11. Invalid room code rejection
12. Anti-impersonation security
13. Choice secrecy before opponent submission
14. Single-choice lock per round
15. Abandoned room cleanup

### B. Android JVM & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
Executes all unit tests and Robolectric context validation (`BUILD SUCCESSFUL`).

---

## 5. Free-Tier Cloud Deployment

### Render (Recommended)
1. In Render Dashboard, click **New > Web Service**.
2. Set Root Directory to `server`.
3. Build Command: `npm install` (optional, uses standard library).
4. Start Command: `npm start`.
5. Environment Variables:
   - `PORT=10000`
   - `TCP_PORT=8887`
   - `ROOM_TIMEOUT_MS=1800000`

### Railway / Fly.io
* Follow standard Node.js Docker/Procfile deployment targeting `server/src/server.js`.

---

## 6. Architecture & Limitations

* **In-Memory Storage**: Per specifications, rooms, player sessions, and chat logs are stored in RAM without a database. Server restarts reset active rooms.
* **Firewall / AP Isolation**: On strict enterprise or guest Wi-Fi networks where client-to-client traffic is blocked by router AP isolation, use Scenario B (central server relay) rather than peer-to-peer Wi-Fi hosting.
