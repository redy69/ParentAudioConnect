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


function getLiveKitProjects(env) {
  const projects = [];
  const add = (id, key, secret, url) => {
    if (typeof key === "string" && key && typeof secret === "string" && secret &&
        typeof url === "string" && /^wss:\/\/[a-z0-9.-]+$/i.test(url)) {
      projects.push({ id, apiKey: key, apiSecret: secret, url });
    }
  };
  add("main", env.LIVEKIT_API_KEY, env.LIVEKIT_API_SECRET, env.LIVEKIT_URL);
  for (let i = 2; i <= 5; i++) {
    add("backup-" + i, env["LIVEKIT_BACKUP_" + i + "_API_KEY"],
      env["LIVEKIT_BACKUP_" + i + "_API_SECRET"], env["LIVEKIT_BACKUP_" + i + "_URL"]);
  }
  return projects;
}

async function createLiveKitCandidates(projects, uid, room, canPublish) {
  const candidates = [];
  for (const project of projects) {
    try {
      const token = await createLiveKitToken(
        { LIVEKIT_API_KEY: project.apiKey, LIVEKIT_API_SECRET: project.apiSecret },
        uid, room, canPublish
      );
      candidates.push({ id: project.id, token, serverUrl: project.url });
    } catch (_) {
      // A misconfigured backup must not prevent other configured projects from working.
    }
  }
  if (candidates.length === 0) throw new Error("No usable LiveKit project credentials");
  return candidates;
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

function pairingUrl(code, collection = "pairings") {
  return "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID +
    "/databases/(default)/documents/" + collection + "/" + encodeURIComponent(code);
}

function pemToArrayBuffer(pem) {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\\s/g, "");
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
  if (!response.ok) {
    let detail = "unknown";
    try {
      const failure = await response.json();
      detail = failure.error || failure.error_description || "provider_rejected_request";
    } catch (_) {}
    throw new Error("FCM_OAUTH_" + response.status + "_" + String(detail).slice(0, 100));
  }
  const result = await response.json();
  if (!result.access_token) throw new Error("FCM access token missing");
  return {
    accessToken: result.access_token,
    projectId: account.project_id,
    serviceAccountEmail: account.client_email
  };
}


async function sendCameraRequest(env, fcmToken, code, requestId, command, facing, serverId) {
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
          data: { command, code, requestId, facing, serverId: serverId || "main" },
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
      if (url.pathname !== "/token" && url.pathname !== "/register-device" &&
          url.pathname !== "/camera-lab/token" && url.pathname !== "/request-audio" &&
          url.pathname !== "/camera-token" && url.pathname !== "/request-camera" &&
          url.pathname !== "/select-server") {
        return jsonResponse({ error: "Not found" }, 404);
      }

      const body = await request.json();
      const code = String(body.code || "").trim().toUpperCase();
      if (!/^[A-HJ-NP-Z2-9]{8}$/.test(code)) return jsonResponse({ error: "Invalid pairing code" }, 400);

      const callerUid = await authenticate(body.idToken, env);
      if (!callerUid) return jsonResponse({ error: "Firebase authentication failed" }, 401);

      const pairingCollection = url.pathname === "/camera-lab/token" ? "cameraLabPairings" : "pairings";
      const firestoreResponse = await fetch(pairingUrl(code, pairingCollection), {
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
        const patchUrl = pairingUrl(code, pairingCollection) + "?updateMask.fieldPaths=" + field;
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


      if (url.pathname === "/select-server") {
        if (callerUid !== parentUid) return jsonResponse({ error: "Only the parent can select a LiveKit server" }, 403);
        const kind = body.kind === "camera" ? "camera" : body.kind === "audio" ? "audio" : null;
        const serverId = String(body.serverId || "");
        const projects = getLiveKitProjects(env);
        if (!kind || !projects.some(project => project.id === serverId)) {
          return jsonResponse({ error: "Invalid LiveKit server selection" }, 400);
        }
        const field = kind === "camera" ? "cameraLiveKitServerId" : "audioLiveKitServerId";
        const saved = await fetch(pairingUrl(code, "pairings") + "?updateMask.fieldPaths=" + field, {
          method: "PATCH",
          headers: { Authorization: "Bearer " + body.idToken, "Content-Type": "application/json" },
          body: JSON.stringify({ fields: { [field]: { stringValue: serverId } } })
        });
        if (!saved.ok) return jsonResponse({ error: "Could not save LiveKit server selection" }, 403);
        return jsonResponse({ saved: true, kind, serverId });
      }

      if (url.pathname === "/request-audio") {
        if (callerUid !== parentUid) return jsonResponse({ error: "Only the parent can control remote audio" }, 403);
        const childFcmToken = fields.childFcmToken?.stringValue;
        if (!childFcmToken) return jsonResponse({ error: "Child device has not registered for notifications" }, 409);
        const action = body.action || "start";
        const commands = {
          start: "AUDIO_START",
          stop: "AUDIO_STOP",
          talk_start: "TALK_START",
          talk_stop: "TALK_STOP"
        };
        const command = commands[action];
        if (!command) return jsonResponse({ error: "Invalid audio action" }, 400);
        try {
          const credentials = await getFcmAccessToken(env);
          // Safe diagnostic only: never log private keys, OAuth tokens, or device FCM tokens.
          console.info("FCM credential identity", {
            projectId: credentials.projectId,
            serviceAccountEmail: credentials.serviceAccountEmail
          });
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
                  data: { command, code, serverId: fields.audioLiveKitServerId?.stringValue || "main" },
                  android: { priority: "HIGH", ttl: "30s" }
                }
              })
            }
          );
          if (!response.ok) {
            let detail = "unknown";
            try {
              const failure = await response.json();
              detail = failure.error?.status || failure.error?.message || "provider_rejected_request";
            } catch (_) {}
            throw new Error("FCM_SEND_" + response.status + "_" + String(detail).slice(0, 100));
          }
          return jsonResponse({ sent: true, action });
        } catch (error) {
          console.error("Audio FCM request failed", error?.name || "Error", error?.message || "unknown");
          return jsonResponse({
            error: "Audio FCM request failed",
            diagnostic: String(error?.message || "unknown").slice(0, 160)
          }, 502);
        }
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
        await sendCameraRequest(env, childFcmToken, code, body.requestId, command, facing,
          fields.cameraLiveKitServerId?.stringValue || "main");
        return jsonResponse({ sent: true, requestId: body.requestId, action });
      }

      if (!env.LIVEKIT_API_KEY || !env.LIVEKIT_API_SECRET || !env.LIVEKIT_URL) {
        return jsonResponse({ error: "Server configuration error" }, 500);
      }
      const digest = await crypto.subtle.digest("SHA-256", encoder.encode(code));
      const roomBase = "pair_" + Array.from(new Uint8Array(digest))
        .map(byte => byte.toString(16).padStart(2, "0")).join("");
      const isParent = callerUid === parentUid;

      // Camera Lab remains isolated on its own collection and its existing room behavior.
      if (url.pathname === "/camera-lab/token") {
        const token = await createLiveKitToken(env, callerUid, roomBase, isParent);
        return jsonResponse({ token, serverUrl: env.LIVEKIT_URL,
          role: isParent ? "parent" : "child", expiresIn: 600 });
      }

      const projects = getLiveKitProjects(env);
      if (projects.length === 0) return jsonResponse({ error: "No LiveKit server is configured" }, 500);
      const kind = url.pathname === "/camera-token" ? "camera" : "audio";
      const storedServerId = kind === "camera"
        ? fields.cameraLiveKitServerId?.stringValue
        : fields.audioLiveKitServerId?.stringValue;
      const requestedServerId = String(body.preferredServerId || "");
      const allowedServerIds = ["main", "backup-2", "backup-3", "backup-4", "backup-5"];
      if (storedServerId && !allowedServerIds.includes(storedServerId)) {
        return jsonResponse({ error: "Stored LiveKit server selection is invalid; parent must select a server again" }, 409);
      }
      if (requestedServerId && !allowedServerIds.includes(requestedServerId)) {
        return jsonResponse({ error: "Requested LiveKit server selection is invalid" }, 400);
      }
      if (storedServerId && requestedServerId && storedServerId !== requestedServerId) {
        return jsonResponse({ error: "Requested server does not match the paired device selection" }, 409);
      }
      const preferredServerId = requestedServerId || storedServerId || "";
      let responsePreferredServerId = preferredServerId;
      if (preferredServerId) {
        const exact = projects.filter(project => project.id === preferredServerId);
        if (exact.length === 0) {
          return jsonResponse({ error: "Selected LiveKit server is not configured; parent must choose an available server" }, 409);
        }

        // The parent must be able to fail over the camera session if its paired
        // server is unreachable. Put the currently paired server first, then
        // return the remaining candidates. After a successful connection the
        // parent saves the winning server and notifies the child with that ID.
        // Child requests and audio requests remain pinned to their paired server.
        if ((kind === "camera" || kind === "audio") && isParent && !requestedServerId) {
          const remaining = projects.filter(project => project.id !== preferredServerId);
          projects.splice(0, projects.length, ...exact, ...remaining);
          responsePreferredServerId = "";
        } else {
          projects.splice(0, projects.length, ...exact);
        }
      }

      const room = kind === "camera" ? roomBase + "_camera" : roomBase;
      const canPublish = kind === "camera" ? !isParent : isParent;
      const servers = await createLiveKitCandidates(projects, callerUid, room, canPublish);
      const primary = servers[0];
      return jsonResponse({
        token: primary.token, serverUrl: primary.serverUrl, serverId: primary.id,
        preferredServerId: responsePreferredServerId, servers, role: isParent ? "parent" : "child", expiresIn: 600
      });
    } catch (error) {
      return jsonResponse({ error: "Internal server error" }, 500);
    }
  }
};
