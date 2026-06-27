/**
 * Dwell test-data seeder.
 *
 * Writes 3 categories (nature, abstract, dark) and 24 placeholder wallpapers
 * (8 per category) so the Phase 1 grid renders before any real assets exist.
 * Images come from picsum.photos (JPEG; real assets are WebP later). Runs with
 * the Admin SDK, which bypasses security rules.
 *
 * Usage:
 *   1. Firebase console > Project settings > Service accounts >
 *      Generate new private key. Save it as seed/serviceAccountKey.json.
 *   2. cd seed && npm install
 *   3. node seed.js
 *
 * Safe to re-run: writes use fixed doc ids, so it upserts rather than dupes.
 */
const admin = require("firebase-admin");
const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

const CATEGORIES = [
  { id: "nature", name: "Nature", order: 0 },
  { id: "abstract", name: "Abstract", order: 1 },
  { id: "dark", name: "Dark", order: 2 },
];

// Muted, on-brand placeholder dominant colors cycled per wallpaper.
const DOMINANT_COLORS = ["#3A5A40", "#6B6B66", "#1A1A18", "#9A9A94", "#2A2A27"];
const PER_CATEGORY = 8;

function imageUrls(seed) {
  const base = `https://picsum.photos/seed/${seed}`;
  return {
    thumb: `${base}/400/600`,
    full_phone: `${base}/1080/2400`,
    full_tablet: `${base}/1600/2560`,
  };
}

async function seed() {
  const now = Date.now();
  let batch = db.batch();
  let writes = 0;

  for (const category of CATEGORIES) {
    batch.set(db.collection("categories").doc(category.id), {
      id: category.id,
      name: category.name,
      order: category.order,
      coverWallpaperId: `${category.id}-0`,
    });
    writes++;

    for (let i = 0; i < PER_CATEGORY; i++) {
      const id = `${category.id}-${i}`;
      // Stagger createdAt so the "All" view (newest-first) has a stable order.
      const createdAtMillis = now - writes * 60_000;
      batch.set(db.collection("wallpapers").doc(id), {
        id,
        title: `${category.name} ${i + 1}`,
        category: category.id,
        tags: [category.id, "placeholder"],
        urls: imageUrls(id),
        dominantColor: DOMINANT_COLORS[(i + category.order) % DOMINANT_COLORS.length],
        isAiGenerated: true,
        createdAt: admin.firestore.Timestamp.fromMillis(createdAtMillis),
        order: i,
      });
      writes++;
    }
  }

  await batch.commit();
  console.log(`Seeded ${CATEGORIES.length} categories and ${CATEGORIES.length * PER_CATEGORY} wallpapers (${writes} writes).`);
}

seed()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Seed failed:", err);
    process.exit(1);
  });
