import { onCall, HttpsError } from "firebase-functions/v2/https";
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { google } from "googleapis";

if (getApps().length === 0) initializeApp();

const PACKAGE_NAME = "com.dwell.app";

export const verifyPurchase = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

  const purchaseToken = request.data?.purchaseToken as string | undefined;
  const productId = request.data?.productId as string | undefined;
  if (!purchaseToken || !productId) {
    throw new HttpsError("invalid-argument", "purchaseToken and productId required.");
  }

  // The function's service account needs the "View financial data" Play role.
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  const androidpublisher = google.androidpublisher({ version: "v3", auth });

  let purchase;
  try {
    const res = await androidpublisher.purchases.products.get({
      packageName: PACKAGE_NAME,
      productId,
      token: purchaseToken,
    });
    purchase = res.data;
  } catch (e) {
    throw new HttpsError("permission-denied", "Could not validate purchase.");
  }

  // purchaseState: 0 = purchased, 1 = cancelled, 2 = pending.
  if (purchase.purchaseState !== 0) {
    throw new HttpsError("failed-precondition", "Purchase not in a paid state.");
  }

  await getFirestore().collection("users").doc(uid).set(
    { premium: true, updatedAt: new Date() },
    { merge: true },
  );

  return { premium: true };
});
