const PROJECT_ID = "parentaudioconnect";
const encoder = new TextEncoder();

function base64url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" }
  });
}

async function createLiveKitToken(env, uid, room, canPublish) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "HS256", typ: "JWT" };
  const payload = {
    iss: env.LIVEKIT_API_KEY, sub: uid, iat: now, nbf: now - 5, exp: now + 600,
    video: { roomJoin: true, room, canPublish, canSubscribe: true, canPublishData: canPublish }
  };
  const unsigned = base64url(encoder.encode(JSON.stringify(header))) + "." +
    base64url(encoder.encode(JSON.stringify(payload)));
  const key = await crypto.subtle.importKey("raw", encoder.encode(env.LIVEKIT_API_SECRET),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(unsigned));
  return unsigned + "." + base64url(new Uint8Array(signature));
}

async function authenticate(idToken, env) {
  if (typeof idToken !== "string" || idToken.length < 100 || !env.FIREBASE_WEB_API_KEY) return null;
  const response = await fetch(
    "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" + encodeURIComponent(env.FIREBASE_WEB_API_KEY),
    { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ idToken }) }
  );
  if (!response.ok) return null;
  const data = await response.json();
  const user = data.users?.[0];
  if (!user?.localId || user.disabled === true) return null;
  return user.localId;
}

function pairingUrl(code) {
  return "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID +
    "/databases/(default)/documents/pairings/" + encodeURIComponent(code);
}

function pemToArrayBuffer(pem) {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

async function getFcmAccessToken(env) {
  if (!env.FCM_SERVICE_ACCOUNT_JSON) throw new Error("FCM service account is not configured");
  const account = JSON.parse(env.FCM_SERVICE_ACCOUNT_JSON);
  if (!account.client_email || !account.private_key || !account.project_id) {
    throw new Error("FCM service account is invalid");
  }
  const now = Math.floor(Date.now() / 1000);
  const unsigned = base64url(encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT" }))) + "." +
    base64url(encoder.encode(JSON.stringify({
      iss: account.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600
    })));
  const key = await crypto.subtle.importKey(
    "pkcs8", pemToArrayBuffer(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(unsigned));
  const assertion = unsigned + "." + base64url(new Uint8Array(signature));
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });
  if (!response.ok) throw new Error("Could not obtain FCM access token");
  const result = await response.json();
  if (!result.access_token) throw new Error("FCM access token missing");
  return { accessToken: result.access_token, projectId: account.project_id };
}

async function sendCameraRequest(env, fcmToken, code, requestId, command, facing) {
  const credentials = await getFcmAccessToken(env);
  const response = await fetch(
    "https://fcm.googleapis.com/v1/projects/" + encodeURIComponent(credentials.projectId) + "/messages:send",
    {
      method: "POST",
      headers: {
        Authorization: "Bearer " + credentials.accessToken,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          data: { command, code, requestId, facing },
          android: { priority: "HIGH", ttl: "30s" }
        }
      })
    }
  );
  if (!response.ok) throw new Error("FCM rejected camera request");
  return await response.json();
}

export default {
  async fetch(request, env) {
    try {
      if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);
      const url = new URL(request.url);
      const allowedPaths = ["/token", "/register-device", "/request-camera", "/request-audio", "/camera-token", "/safe-zone-event"];
      if (!allowedPaths.includes(url.pathname)) return jsonResponse({ error: "Not found" }, 404);

      const body = await request.json();
      const code = String(body.code || "").trim().toUpperCase();
      if (!/^[A-HJ-NP-Z2-9]{8}$/.test(code)) return jsonResponse({ error: "Invalid pairing code" }, 400);

      const callerUid = await authenticate(body.idToken, env);
      if (!callerUid) return jsonResponse({ error: "Firebase authentication failed" }, 401);

      const firestoreResponse = await fetch(pairingUrl(code), {
        headers: { Authorization: "Bearer " + body.idToken }
      });
      if (!firestoreResponse.ok) return jsonResponse({ error: "Pairing not found or access denied" }, 403);

      const document = await firestoreResponse.json();
      const fields = document.fields || {};
      const parentUid = fields.parentUid?.stringValue;
      const childUid = fields.childUid?.stringValue;

      if (!parentUid || !childUid || parentUid === childUid ||
          (callerUid !== parentUid && callerUid !== childUid)) {
        return jsonResponse({ error: "Not a member of this pairing" }, 403);
      }

      if (url.pathname === "/register-device") {
        const fcmToken = body.fcmToken;
        if (typeof fcmToken !== "string" || fcmToken.length < 20 || fcmToken.length > 4096) {
          return jsonResponse({ error: "Invalid device token" }, 400);
        }
        const field = callerUid === parentUid ? "parentFcmToken" : "childFcmToken";
        const patchUrl = pairingUrl(code) + "?updateMask.fieldPaths=" + field;
        const saved = await fetch(patchUrl, {
          method: "PATCH",
          headers: {
            Authorization: "Bearer " + body.idToken,
            "Content-Type": "application/json"
          },
          body: JSON.stringify({ fields: { [field]: { stringValue: fcmToken } } })
        });
        if (!saved.ok) {
          return jsonResponse({ error: "Could not register device. Check Firestore permissions." }, 403);
        }
        return jsonResponse({ registered: true, role: callerUid === parentUid ? "parent" : "child" });
      }

      if (url.pathname === "/safe-zone-event") {
        if (callerUid !== childUid) return jsonResponse({ error: "Only the paired child can report safe-zone events" }, 403);
        const eventId = String(body.eventId || "");
        const zoneName = String(body.zoneName || "").trim().slice(0, 80);
        const transition = body.transition;
        if (!/^[A-Za-z0-9_-]{8,80}$/.test(eventId) || !zoneName ||
            !["ENTER", "EXIT"].includes(transition)) {
          return jsonResponse({ error: "Invalid safe-zone event" }, 400);
        }
        const parentFcmToken = fields.parentFcmToken?.stringValue;
        if (!parentFcmToken) return jsonResponse({ error: "Parent device has not registered for notifications" }, 409);
        const credentials = await getFcmAccessToken(env);
        const title = transition === "ENTER" ? "SantetHP • Masuk zona aman" : "SantetHP • Keluar zona aman";
        const notificationBody = transition === "ENTER"
          ? "Perangkat anak tiba di " + zoneName + "."
          : "Perangkat anak keluar dari " + zoneName + ".";
        const response = await fetch(
          "https://fcm.googleapis.com/v1/projects/" + encodeURIComponent(credentials.projectId) + "/messages:send",
          {
            method: "POST",
            headers: {
              Authorization: "Bearer " + credentials.accessToken,
              "Content-Type": "application/json"
            },
            body: JSON.stringify({
              message: {
                token: parentFcmToken,
                notification: { title, body: notificationBody },
                data: { command: "SAFE_ZONE_EVENT", code, eventId, zoneName, transition },
                android: {
                  priority: "HIGH",
                  ttl: "3600s",
                  notification: { channel_id: "santethp_safe_zone_events" }
                }
              }
            })
          }
        );
        if (!response.ok) {
          const detail = await response.text();
          return jsonResponse({ error: "FCM rejected safe-zone notification", detail: detail.slice(0, 300) }, 502);
        }
        return jsonResponse({ sent: true, eventId, transition });
      }

      if (url.pathname === "/request-audio") {
        if (callerUid !== parentUid) return jsonResponse({ error: "Only the parent can control remote audio" }, 403);
        const childFcmToken = fields.childFcmToken?.stringValue;
        if (!childFcmToken) return jsonResponse({ error: "Child device has not registered for notifications" }, 409);
        const action = body.action || "start";
        const commands = { start: "AUDIO_START", stop: "AUDIO_STOP" };
        const command = commands[action];
        if (!command) return jsonResponse({ error: "Invalid audio action" }, 400);
        const credentials = await getFcmAccessToken(env);
        const response = await fetch(
          "https://fcm.googleapis.com/v1/projects/" + encodeURIComponent(credentials.projectId) + "/messages:send",
          {
            method: "POST",
            headers: {
              Authorization: "Bearer " + credentials.accessToken,
              "Content-Type": "application/json"
            },
            body: JSON.stringify({
              message: {
                token: childFcmToken,
                data: { command, code },
                android: { priority: "HIGH", ttl: "30s" }
              }
            })
          }
        );
        if (!response.ok) throw new Error("FCM rejected audio request");
        return jsonResponse({ sent: true, action });
      }

      if (url.pathname === "/request-camera") {
        if (callerUid !== parentUid) return jsonResponse({ error: "Only the parent can request the camera" }, 403);
        const childFcmToken = fields.childFcmToken?.stringValue;
        if (!childFcmToken) return jsonResponse({ error: "Child device has not registered for notifications" }, 409);
        if (typeof body.requestId !== "string" || !/^[A-Za-z0-9_-]{8,80}$/.test(body.requestId)) {
          return jsonResponse({ error: "Invalid request ID" }, 400);
        }
        const action = body.action || "start";
        const commands = { start: "CAMERA_START", switch: "CAMERA_SWITCH", stop: "CAMERA_STOP" };
        const command = commands[action];
        if (!command) return jsonResponse({ error: "Invalid camera action" }, 400);
        const facing = body.facing === "front" ? "front" : "back";
        await sendCameraRequest(env, childFcmToken, code, body.requestId, command, facing);
        return jsonResponse({ sent: true, requestId: body.requestId, action });
      }

      if (!env.LIVEKIT_API_KEY || !env.LIVEKIT_API_SECRET || !env.LIVEKIT_URL) {
        return jsonResponse({ error: "Server configuration error" }, 500);
      }
      const digest = await crypto.subtle.digest("SHA-256", encoder.encode(code));
      const roomBase = "pair_" + Array.from(new Uint8Array(digest))
        .map(byte => byte.toString(16).padStart(2, "0")).join("");
      const isParent = callerUid === parentUid;

      if (url.pathname === "/camera-token") {
        const cameraRoom = roomBase + "_camera";
        const token = await createLiveKitToken(env, callerUid, cameraRoom, !isParent);
        return jsonResponse({
          token, serverUrl: env.LIVEKIT_URL,
          role: isParent ? "parent" : "child", expiresIn: 600
        });
      }

      const token = await createLiveKitToken(env, callerUid, roomBase, isParent);
      return jsonResponse({ token, serverUrl: env.LIVEKIT_URL, role: isParent ? "parent" : "child", expiresIn: 600 });
    } catch (error) {
      return jsonResponse({ error: "Internal server error" }, 500);
    }
  }
};
