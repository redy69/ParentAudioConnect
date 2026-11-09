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

export default {
  async fetch(request, env) {
    try {
      if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);
      const url = new URL(request.url);
      if (url.pathname !== "/token" && url.pathname !== "/register-device") {
        return jsonResponse({ error: "Not found" }, 404);
      }

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

      if (!env.LIVEKIT_API_KEY || !env.LIVEKIT_API_SECRET || !env.LIVEKIT_URL) {
        return jsonResponse({ error: "Server configuration error" }, 500);
      }
      const digest = await crypto.subtle.digest("SHA-256", encoder.encode(code));
      const room = "pair_" + Array.from(new Uint8Array(digest))
        .map(byte => byte.toString(16).padStart(2, "0")).join("");
      const isParent = callerUid === parentUid;
      const token = await createLiveKitToken(env, callerUid, room, isParent);
      return jsonResponse({ token, serverUrl: env.LIVEKIT_URL, role: isParent ? "parent" : "child", expiresIn: 600 });
    } catch (error) {
      return jsonResponse({ error: "Internal server error" }, 500);
    }
  }
};
