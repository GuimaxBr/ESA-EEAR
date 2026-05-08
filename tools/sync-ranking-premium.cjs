const admin = require("firebase-admin");
const fs = require("fs");
const path = require("path");

function resolveServiceAccountPath() {
  const providedPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
  if (!providedPath) {
    throw new Error("Defina FIREBASE_SERVICE_ACCOUNT_PATH apontando para o JSON da service account.");
  }

  const fullPath = path.resolve(providedPath);
  if (!fs.existsSync(fullPath)) {
    throw new Error(`Service account nao encontrada em: ${fullPath}`);
  }

  return fullPath;
}

async function main() {
  const serviceAccountPath = resolveServiceAccountPath();
  const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, "utf8"));

  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });

  const firestore = admin.firestore();
  const usersSnapshot = await firestore.collection("users").get();

  let updatedCount = 0;
  let skippedCount = 0;
  let missingRankingCount = 0;

  for (const userDoc of usersSnapshot.docs) {
    const userData = userDoc.data() || {};
    const rankingRef = firestore.collection("ranking").doc(userDoc.id);
    const rankingSnapshot = await rankingRef.get();

    if (!rankingSnapshot.exists) {
      missingRankingCount += 1;
      continue;
    }

    const nextPremium = userData.isPremium === true;
    const currentPremium = rankingSnapshot.get("isPremium") === true;

    if (currentPremium === nextPremium) {
      skippedCount += 1;
      continue;
    }

    await rankingRef.set({ isPremium: nextPremium }, { merge: true });
    updatedCount += 1;
    console.log(`sync ${userDoc.id}: ${currentPremium} -> ${nextPremium}`);
  }

  console.log("");
  console.log("Sincronizacao concluida.");
  console.log(`Atualizados: ${updatedCount}`);
  console.log(`Sem mudanca: ${skippedCount}`);
  console.log(`Sem doc em ranking: ${missingRankingCount}`);
}

main().catch((error) => {
  console.error("Falha ao sincronizar premium do ranking:", error.message || error);
  process.exit(1);
});
