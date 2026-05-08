const admin = require('firebase-admin');
const fs = require('fs');

const serviceAccount = require('/opt/game-server/firebase-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: 'https://base-cloud-305a6-default-rtdb.firebaseio.com'
});

const db = admin.database();
const firestore = admin.firestore();

function getCurrentDomain() {
  const filePath = '/opt/tunnel/public_url.txt';
  if (fs.existsSync(filePath)) {
    return fs.readFileSync(filePath, 'utf8').trim();
  }
  console.error('Arquivo public_url.txt não encontrado!');
  return null;
}

function withPublicPort(domain) {
  if (!domain) return null;
  if (domain.indexOf(':443') !== -1) return domain;
  return domain + ':443';
}

async function updateFirebase() {
  const domain = getCurrentDomain();
  const publishedDomain = withPublicPort(domain);
  if (!publishedDomain) return;

  try {
    await db.ref('domain').set({
      game_server: publishedDomain,
      cluster_bridge: domain,
      updated_at: Date.now(),
      status: 'online'
    });
    await firestore.collection('cluster').doc('public').set({
      publicBaseUrl: domain,
      workerMaxLoadPercent: 50,
      updatedAt: new Date().toISOString()
    }, { merge: true });
    console.log('Domínio enviado pro Firebase:', publishedDomain);
  } catch (error) {
    console.error('Erro ao atualizar Firebase:', error);
  }
}

setInterval(() => {
  updateFirebase().catch((error) => console.error('Erro no agendador Firebase:', error));
}, 10000);
updateFirebase().catch((error) => console.error('Erro na inicialização Firebase:', error));

