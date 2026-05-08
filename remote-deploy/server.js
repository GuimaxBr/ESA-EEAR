/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");
const http = require("http");
const crypto = require("crypto");
const { WebSocketServer } = require("ws");

const CONFIG_PATH = path.join(__dirname, "obscureshell-websocket-config.merged.json");
const config = JSON.parse(fs.readFileSync(CONFIG_PATH, "utf8"));
const CLUSTER_PROXY_HOST = process.env.CLUSTER_PROXY_HOST || "127.0.0.1";
const CLUSTER_PROXY_PORT = Number(process.env.CLUSTER_PROXY_PORT || 8787);

const AmmoType = {
  LIVE: { isLive: true, label: "real" },
  BLANK: { isLive: false, label: "festim" },
};

const GamePhase = {
  ROUND_INTRO: "ROUND_INTRO",
  AMMO_REVEAL: "AMMO_REVEAL",
  CHEST_DRAW: "CHEST_DRAW",
  PLAYING: "PLAYING",
  GAME_OVER: "GAME_OVER",
};

const ItemType = {
  CARD_A: "CARD_A",
  CARD_QUEEN: "CARD_QUEEN",
  CARD_KING: "CARD_KING",
  CARD_H: "CARD_H",
  CARD_C: "CARD_C",
  CARD_JOKER_KING: "CARD_JOKER_KING",
};

const ITEM_DISPLAY = {
  [ItemType.CARD_A]: "Carta A",
  [ItemType.CARD_QUEEN]: "Carta Queen",
  [ItemType.CARD_KING]: "Carta King",
  [ItemType.CARD_H]: "Carta Coracao",
  [ItemType.CARD_C]: "Carta Mente",
  [ItemType.CARD_JOKER_KING]: "Carta Joker King",
};

const BOT_NAMES = ["Carrasco", "Espectro", "Cacador", "Sentinela"];
const connectedClients = new Set();

function randomInt(min, max) {
  return Math.floor(Math.random() * ((max - min) + 1)) + min;
}

function chance(percent) {
  return Math.random() * 100 < percent;
}

function shuffle(items) {
  for (let i = items.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [items[i], items[j]] = [items[j], items[i]];
  }
}

function createRoomCode() {
  return crypto.randomBytes(4).toString("hex").toUpperCase();
}

function sanitizeRoomName(roomName, playerName) {
  const trimmed = String(roomName || "").trim();
  if (trimmed.length > 0) {
    return trimmed.slice(0, 40);
  }
  const owner = String(playerName || "").trim() || "Jogador";
  return `Sala de ${owner}`.slice(0, 40);
}

function drawRandomItem() {
  const items = Object.values(ItemType);
  return items[randomInt(0, items.length - 1)];
}

function safeSend(ws, payload) {
  if (ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

class PlayerState {
  constructor({ id, seatIndex, name, avatarKey, isHuman, isBot = false }) {
    this.id = id;
    this.seatIndex = seatIndex;
    this.name = name;
    this.avatarKey = avatarKey || "smile";
    this.isHuman = isHuman;
    this.isBot = isBot;
    this.maxHp = 4;
    this.hp = this.maxHp;
    this.lastDamageAtMs = 0;
    this.inventory = new Map();
  }

  get isAlive() {
    return this.hp > 0;
  }

  reset() {
    this.hp = this.maxHp;
    this.lastDamageAtMs = 0;
    this.inventory.clear();
  }

  itemCount(itemType) {
    return this.inventory.get(itemType) || 0;
  }

  addItem(itemType, amount = 1) {
    this.inventory.set(itemType, this.itemCount(itemType) + amount);
  }

  removeOne(itemType) {
    const count = this.itemCount(itemType);
    if (count <= 0) {
      return false;
    }

    if (count === 1) {
      this.inventory.delete(itemType);
    } else {
      this.inventory.set(itemType, count - 1);
    }
    return true;
  }

  clearInventory() {
    this.inventory.clear();
  }

  applyDamage(amount, timestampMs) {
    this.hp = Math.max(0, this.hp - amount);
    this.lastDamageAtMs = timestampMs;
  }

  heal(amount) {
    this.hp = Math.min(this.maxHp, this.hp + amount);
  }
}

class GunSystem {
  constructor() {
    this.magazine = [];
    this.lastLoadout = { totalShells: 0, liveShells: 0, blankShells: 0 };
  }

  get remainingShells() {
    return this.magazine.length;
  }

  peekCurrent() {
    return this.magazine[0] || null;
  }

  fireCurrent() {
    if (this.magazine.length === 0) {
      return null;
    }
    return this.magazine.shift();
  }

  reload(alivePlayers) {
    this.magazine = [];
    const totalShells = alivePlayers >= 4 ? 8 : alivePlayers === 3 ? 7 : 6;
    const liveShells = totalShells === 8 ? randomInt(3, 5) : randomInt(2, 4);
    const blankShells = totalShells - liveShells;

    for (let i = 0; i < liveShells; i += 1) this.magazine.push(AmmoType.LIVE);
    for (let i = 0; i < blankShells; i += 1) this.magazine.push(AmmoType.BLANK);
    shuffle(this.magazine);

    this.lastLoadout = { totalShells, liveShells, blankShells };
    return this.lastLoadout;
  }
}

function expandCards(player) {
  const result = [];
  for (const itemType of Object.values(ItemType)) {
    for (let count = 0; count < player.itemCount(itemType); count += 1) {
      result.push(itemType);
    }
  }
  return result;
}

function inventoryLabel(player) {
  const cards = expandCards(player);
  if (cards.length === 0) {
    return "Sem cartas";
  }
  return cards.map((card) => ITEM_DISPLAY[card]).join("  |  ");
}

class RoomGame {
  constructor(room) {
    this.room = room;
    this.players = [];
    this.gunSystem = new GunSystem();
    this.skipChargesByPlayerId = new Map();
    this.knownShellByPlayerId = new Map();
    this.hintByPlayerId = new Map();
    this.doubleDamageByPlayerId = new Map();
    this.shellHistory = [];
    this.currentTurnIndex = 0;
    this.currentRound = 1;
    this.roundStarterPlayerId = null;
    this.statusMessage = "Mesa pronta. A rodada 1 vai comecar.";
    this.phase = GamePhase.ROUND_INTRO;
    this.currentLoadout = { totalShells: 0, liveShells: 0, blankShells: 0 };
    this.pendingChestCards = new Map();
    this.revealedChestCards = new Map();
    this.updatedAt = Date.now();
    this.lastAction = null;
    this.actionSequence = 0;
    this.phaseTimer = null;
    this.botTurnTimer = null;
    this.winnerPlayerId = null;
  }

  initializeMatch() {
    this.clearTimers();
    this.players = this.room.seats
      .slice()
      .sort((a, b) => a.seatIndex - b.seatIndex)
      .map((seat) => new PlayerState({
        id: seat.playerId,
        seatIndex: seat.seatIndex,
        name: seat.name,
        avatarKey: seat.avatarKey,
        isHuman: seat.human,
        isBot: !seat.human,
      }));

    this.players.forEach((player) => player.reset());
    this.currentTurnIndex = 0;
    this.currentRound = 1;
    this.roundStarterPlayerId = this.players[0] ? this.players[0].id : null;
    this.prepareRoundSetup();
    this.touch();
    this.scheduleRoundIntro();
  }

  getCurrentPlayer() {
    return this.players[this.currentTurnIndex];
  }

  alivePlayers() {
    return this.players.filter((player) => player.isAlive);
  }

  aliveOpponentsOf(player) {
    return this.alivePlayers().filter((target) => target.id !== player.id);
  }

  isGameOver() {
    return this.alivePlayers().length <= 1 || this.phase === GamePhase.GAME_OVER;
  }

  playerById(playerId) {
    return this.players.find((player) => player.id === playerId) || null;
  }

  clearTimers() {
    clearTimeout(this.phaseTimer);
    clearTimeout(this.botTurnTimer);
  }

  touch() {
    this.updatedAt = Date.now();
  }

  scheduleRoundIntro() {
    this.phaseTimer = setTimeout(() => {
      if (this.phase === GamePhase.ROUND_INTRO) {
        this.showAmmoReveal();
      }
    }, config.phaseTimingsMs.roundIntro);
  }

  showAmmoReveal() {
    if (this.phase !== GamePhase.ROUND_INTRO) {
      return;
    }
    this.phase = GamePhase.AMMO_REVEAL;
    this.statusMessage = "Decore a composicao do cilindro. Depois abra o bau para revelar suas cartas.";
    this.touch();
    this.room.broadcastState();

    this.phaseTimer = setTimeout(() => {
      if (this.phase === GamePhase.AMMO_REVEAL) {
        this.startChestDrawPhase();
      }
    }, config.phaseTimingsMs.ammoReveal);
  }

  startChestDrawPhase() {
    if (this.phase !== GamePhase.AMMO_REVEAL) {
      return;
    }
    this.phase = GamePhase.CHEST_DRAW;
    this.currentTurnIndex = this.indexForPlayerId(this.roundStarterPlayerId);
    this.selectNextChestDrawer(true);
    this.statusMessage = `Toque no bau 3 vezes para revelar as cartas da rodada. ${this.getCurrentPlayer().name} compra primeiro.`;
    this.touch();
    this.room.broadcastState();
  }

  selectNextChestDrawer(resetToFirstPlayer) {
    if (resetToFirstPlayer) {
      const firstAliveIndex = this.indexForPlayerId(this.roundStarterPlayerId);
      if (firstAliveIndex >= 0) {
        this.currentTurnIndex = firstAliveIndex;
      }
    }

    for (let attempt = 0; attempt < this.players.length; attempt += 1) {
      const player = this.players[this.currentTurnIndex];
      const pending = this.pendingChestCards.get(player.id) || [];
      if (player.isAlive && pending.length > 0) {
        return true;
      }
      this.currentTurnIndex = (this.currentTurnIndex + 1) % this.players.length;
    }
    return false;
  }

  startCombatPhase() {
    if (this.phase !== GamePhase.CHEST_DRAW) {
      return;
    }
    const remainingDraws = Array.from(this.pendingChestCards.values()).reduce((sum, cards) => sum + cards.length, 0);
    if (remainingDraws > 0) {
      return;
    }
    this.currentTurnIndex = this.indexForPlayerId(this.roundStarterPlayerId);
    this.phase = GamePhase.PLAYING;
    this.statusMessage = `Combate liberado. ${this.getCurrentPlayer().name} comeca.`;
    this.touch();
    this.room.broadcastState();
    this.queueBotTurnIfNeeded();
  }

  prepareRoundSetup() {
    this.currentLoadout = this.gunSystem.reload(this.alivePlayers().length);
    this.knownShellByPlayerId.clear();
    this.hintByPlayerId.clear();
    this.skipChargesByPlayerId.clear();
    this.doubleDamageByPlayerId.clear();
    this.winnerPlayerId = null;
    this.phase = GamePhase.ROUND_INTRO;
    this.statusMessage = `Rodada ${this.currentRound} preparada. A mesa vai revelar a composicao do cilindro.`;
    this.pendingChestCards.clear();
    this.revealedChestCards.clear();

    for (const player of this.players.filter((entry) => entry.isAlive)) {
      player.clearInventory();
      const hand = [drawRandomItem(), drawRandomItem(), drawRandomItem()];
      hand.forEach((card) => player.addItem(card));
      this.pendingChestCards.set(player.id, hand.slice());
      this.revealedChestCards.set(player.id, []);
    }
  }

  allHandsDrawn() {
    return this.alivePlayers()
      .every((player) => (this.pendingChestCards.get(player.id) || []).length === 0);
  }

  simpleResult(message) {
    const winner = this.winnerPlayer();
    return {
      message,
      shellFired: null,
      gameFinished: this.phase === GamePhase.GAME_OVER,
      winnerName: winner ? winner.name : null,
      actorId: null,
      targetId: null,
    };
  }

  recordAction(action) {
    const winner = this.winnerPlayer();
    this.actionSequence += 1;
    this.lastAction = {
      message: this.statusMessage,
      shellFired: action.shellFired || null,
      gameFinished: this.phase === GamePhase.GAME_OVER,
      winnerName: winner ? winner.name : null,
      actorId: action.actorId || null,
      targetId: action.targetId || null,
    };
    this.touch();
    return this.lastAction;
  }

  winnerPlayer() {
    if (this.winnerPlayerId != null) {
      return this.playerById(this.winnerPlayerId);
    }
    return this.alivePlayers()[0] || null;
  }

  revealNextChestCard(playerId) {
    if (this.phase !== GamePhase.CHEST_DRAW) {
      return this.simpleResult("O bau so abre durante a fase de compra.");
    }

    const player = this.playerById(playerId);
    if (!player || !player.isAlive) {
      return this.simpleResult("Jogador invalido para revelar carta.");
    }
    if (this.getCurrentPlayer().id !== playerId) {
      return this.simpleResult("Nao e o turno desse jogador.");
    }

    const pending = this.pendingChestCards.get(playerId) || [];
    if (pending.length === 0) {
      this.startCombatPhase();
      return this.simpleResult(this.statusMessage);
    }

    const card = pending.shift();
    const revealed = this.revealedChestCards.get(playerId) || [];
    revealed.push(card);
    this.statusMessage = `${player.name} puxou ${ITEM_DISPLAY[card]} do bau.`;
    if (pending.length === 0) {
      this.statusMessage += " Mao completa.";
    } else {
      this.statusMessage += ` Faltam ${pending.length} carta(s) para o combate.`;
    }

    this.pendingChestCards.set(playerId, pending);
    this.revealedChestCards.set(playerId, revealed);
    this.touch();

    if (this.allHandsDrawn()) {
      this.startCombatPhase();
    } else {
      if (pending.length === 0 && this.selectNextChestDrawer(false)) {
        this.statusMessage += ` Agora ${this.getCurrentPlayer().name} revela o bau.`;
      }
      this.room.broadcastState();
    }
    return this.simpleResult(this.statusMessage);
  }

  useCardA(playerId) {
    const player = this.playerById(playerId);
    if (!player.removeOne(ItemType.CARD_A)) {
      return this.simpleResult(`${player.name} tentou usar a Carta A sem ter uma na mao.`);
    }
    const ammoType = this.gunSystem.peekCurrent();
    if (ammoType) {
      this.knownShellByPlayerId.set(player.id, ammoType);
      this.hintByPlayerId.set(player.id, `Proximo cartucho conhecido: ${ammoType.label}.`);
    }
    this.statusMessage = `${player.name} revelou o proximo cartucho: ${ammoType ? ammoType.label : "desconhecido"}.`;
    return this.recordAction({ message: this.statusMessage });
  }

  useCardKing(playerId) {
    const player = this.playerById(playerId);
    if (!player.removeOne(ItemType.CARD_KING)) {
      return this.simpleResult(`${player.name} tentou usar a Carta King sem ter uma na mao.`);
    }
    this.doubleDamageByPlayerId.set(player.id, true);
    this.statusMessage = `${player.name} armou dano duplo para o proximo disparo real.`;
    return this.recordAction({ message: this.statusMessage });
  }

  useCardHeart(playerId) {
    const player = this.playerById(playerId);
    if (!player.removeOne(ItemType.CARD_H)) {
      return this.simpleResult(`${player.name} tentou usar a Carta Coracao sem ter uma na mao.`);
    }
    const previousHp = player.hp;
    player.heal(1);
    this.statusMessage = player.hp > previousHp
      ? `${player.name} recuperou 1 HP com a Carta Coracao.`
      : `${player.name} usou a Carta Coracao, mas ja estava com a vida cheia.`;
    return this.recordAction({ message: this.statusMessage });
  }

  useCardMind(playerId) {
    const player = this.playerById(playerId);
    if (!player.removeOne(ItemType.CARD_C)) {
      return this.simpleResult(`${player.name} tentou usar a Carta Mente sem ter uma na mao.`);
    }
    const tailShells = this.gunSystem.magazine.slice(-2);
    let historyLabel = "Nao ha municao suficiente para revelar o fim da rodada.";
    if (tailShells.length === 1) {
      historyLabel = `A ultima municao da rodada e ${tailShells[0].label}.`;
    } else if (tailShells.length >= 2) {
      historyLabel = `As duas ultimas municoes da rodada sao ${tailShells[0].label} e ${tailShells[1].label}.`;
    }
    this.hintByPlayerId.set(player.id, historyLabel);
    this.statusMessage = `${player.name} leu a mesa. ${historyLabel}`;
    return this.recordAction({ message: this.statusMessage });
  }

  useQueenOnTarget(playerId, targetId) {
    const player = this.playerById(playerId);
    const target = this.aliveOpponentsOf(player).find((entry) => entry.id === targetId);
    if (!target) {
      return this.simpleResult("O alvo escolhido nao esta disponivel.");
    }
    if (!player.removeOne(ItemType.CARD_QUEEN)) {
      return this.simpleResult(`${player.name} tentou usar a Carta Queen sem ter uma na mao.`);
    }
    this.skipChargesByPlayerId.set(target.id, (this.skipChargesByPlayerId.get(target.id) || 0) + 1);
    this.statusMessage = `${player.name} marcou ${target.name} para perder o proximo turno.`;
    return this.recordAction({ message: this.statusMessage });
  }

  useJokerKingOnTarget(playerId, targetId, targetCardType = null) {
    const player = this.playerById(playerId);
    const target = this.aliveOpponentsOf(player).find((entry) => entry.id === targetId);
    if (!target) {
      return this.simpleResult("O alvo escolhido nao esta disponivel.");
    }
    if (!player.removeOne(ItemType.CARD_JOKER_KING)) {
      return this.simpleResult(`${player.name} tentou usar Joker King sem ter essa carta.`);
    }

    const availableCards = expandCards(target);
    if (availableCards.length === 0) {
      this.statusMessage = `${target.name} nao tinha carta para roubar.`;
      return this.recordAction({ message: this.statusMessage });
    }

    if (targetCardType && !availableCards.includes(targetCardType)) {
      return this.simpleResult(`${target.name} nao possui a carta escolhida.`);
    }

    const stolenCard = targetCardType || availableCards[randomInt(0, availableCards.length - 1)];
    target.removeOne(stolenCard);
    player.addItem(stolenCard);
    this.statusMessage = `${player.name} roubou ${ITEM_DISPLAY[stolenCard]} de ${target.name}.`;
    return this.recordAction({ message: this.statusMessage });
  }

  useItem(playerId, itemType) {
    if (this.phase !== GamePhase.PLAYING) {
      return this.simpleResult("As cartas so podem ser usadas durante o combate.");
    }
    if (this.getCurrentPlayer().id !== playerId) {
      return this.simpleResult("Nao e o turno desse jogador.");
    }

    switch (itemType) {
      case ItemType.CARD_A:
        return this.useCardA(playerId);
      case ItemType.CARD_KING:
        return this.useCardKing(playerId);
      case ItemType.CARD_H:
        return this.useCardHeart(playerId);
      case ItemType.CARD_C:
        return this.useCardMind(playerId);
      case ItemType.CARD_QUEEN:
        return this.simpleResult("Escolha um inimigo para aplicar a Carta Queen.");
      case ItemType.CARD_JOKER_KING:
        return this.simpleResult("Escolha um inimigo para roubar uma carta com Joker King.");
      default:
        return this.simpleResult("Carta desconhecida.");
    }
  }

  useTargetItem(playerId, itemType, targetId, targetCardType = null) {
    if (this.phase !== GamePhase.PLAYING) {
      return this.simpleResult("As cartas so podem ser usadas durante o combate.");
    }
    if (this.getCurrentPlayer().id !== playerId) {
      return this.simpleResult("Nao e o turno desse jogador.");
    }

    if (itemType === ItemType.CARD_QUEEN) {
      return this.useQueenOnTarget(playerId, targetId);
    }
    if (itemType === ItemType.CARD_JOKER_KING) {
      return this.useJokerKingOnTarget(playerId, targetId, targetCardType);
    }
    return this.simpleResult("Essa carta nao usa alvo.");
  }

  advanceTurn() {
    let attempts = 0;
    do {
      this.currentTurnIndex = (this.currentTurnIndex + 1) % this.players.length;
      const player = this.players[this.currentTurnIndex];
      if (!player.isAlive) {
        attempts += 1;
        continue;
      }

      const skipCharges = this.skipChargesByPlayerId.get(player.id) || 0;
      if (skipCharges > 0) {
        this.skipChargesByPlayerId.set(player.id, skipCharges - 1);
        this.statusMessage += ` ${player.name} perdeu o turno.`;
        attempts += 1;
        continue;
      }
      return;
    } while (attempts <= this.players.length * 2);
  }

  finalizeCombatAction(advanceTurn) {
    if (this.alivePlayers().length <= 1) {
      this.phase = GamePhase.GAME_OVER;
      const winner = this.alivePlayers()[0];
      this.winnerPlayerId = winner ? winner.id : null;
      this.statusMessage = winner
        ? `${this.statusMessage} ${winner.name} venceu a partida.`
        : `${this.statusMessage} Todos cairam.`;
      return;
    }

    if (advanceTurn) {
      this.advanceTurn();
    }

    if (this.gunSystem.remainingShells === 0) {
      this.roundStarterPlayerId = this.getCurrentPlayer() ? this.getCurrentPlayer().id : this.roundStarterPlayerId;
      this.currentRound += 1;
      this.prepareRoundSetup();
      this.scheduleRoundIntro();
    } else {
      this.queueBotTurnIfNeeded();
    }
  }

  fireAtTarget(shooter, target) {
    const firedShell = this.gunSystem.fireCurrent();
    if (!firedShell) {
      return this.simpleResult("A arma esta vazia.");
    }

    this.knownShellByPlayerId.clear();
    this.hintByPlayerId.clear();
    this.shellHistory.unshift(firedShell);
    this.shellHistory = this.shellHistory.slice(0, 2);
    const selfInflictedShot = shooter.id === target.id;
    const hasDoubleDamage = this.doubleDamageByPlayerId.get(shooter.id) === true;

    const baseMessage = selfInflictedShot
      ? `${shooter.name} atirou em si.`
      : `${shooter.name} atirou em ${target.name}.`;

    if (firedShell.isLive) {
      const damage = hasDoubleDamage ? 2 : 1;
      target.applyDamage(damage, Date.now());
      this.statusMessage = damage === 2
        ? `${baseMessage} O cartucho era real e causou dano duplo.`
        : `${baseMessage} O cartucho era real e causou dano.`;
    } else {
      this.statusMessage = selfInflictedShot
        ? `${baseMessage} O cartucho era festim. ${shooter.name} continua com a vez.`
        : `${baseMessage} O cartucho era festim.`;
    }
    if (hasDoubleDamage) {
      this.doubleDamageByPlayerId.delete(shooter.id);
    }

    const shouldAdvanceTurn = !(selfInflictedShot && !firedShell.isLive);
    this.finalizeCombatAction(shouldAdvanceTurn);
    return this.recordAction({
      message: this.statusMessage,
      shellFired: firedShell,
      actorId: shooter.id,
      targetId: target.id,
    });
  }

  shootSelf(playerId) {
    if (this.phase !== GamePhase.PLAYING) {
      return this.simpleResult("Aguarde a compra das cartas terminar.");
    }
    const shooter = this.playerById(playerId);
    if (!shooter || this.getCurrentPlayer().id !== shooter.id) {
      return this.simpleResult("Nao e o turno desse jogador.");
    }
    return this.fireAtTarget(shooter, shooter);
  }

  shootTarget(playerId, targetId) {
    if (this.phase !== GamePhase.PLAYING) {
      return this.simpleResult("Aguarde a compra das cartas terminar.");
    }
    const shooter = this.playerById(playerId);
    if (!shooter || this.getCurrentPlayer().id !== shooter.id) {
      return this.simpleResult("Nao e o turno desse jogador.");
    }
    const target = this.aliveOpponentsOf(shooter).find((player) => player.id === targetId);
    if (!target) {
      return this.simpleResult("O alvo escolhido nao esta disponivel.");
    }
    return this.fireAtTarget(shooter, target);
  }

  surrender(playerId) {
    const player = this.playerById(playerId);
    if (!player || this.phase === GamePhase.GAME_OVER) {
      return this.simpleResult("Nao foi possivel desistir agora.");
    }

    const winner = this.aliveOpponentsOf(player)[0] || null;
    this.phase = GamePhase.GAME_OVER;
    this.winnerPlayerId = winner ? winner.id : null;
    this.statusMessage = winner
      ? `${player.name} desistiu. ${winner.name} venceu a partida.`
      : `${player.name} desistiu. Partida encerrada.`;

    return this.recordAction({
      message: this.statusMessage,
      actorId: player.id,
      targetId: winner ? winner.id : null,
    });
  }

  queueBotTurnIfNeeded() {
    clearTimeout(this.botTurnTimer);
    const player = this.getCurrentPlayer();
    if (!player || player.isHuman || this.phase !== GamePhase.PLAYING || this.isGameOver()) {
      this.room.broadcastState();
      return;
    }

    this.room.broadcastState();
    this.botTurnTimer = setTimeout(() => {
      if (this.phase === GamePhase.PLAYING && !this.getCurrentPlayer().isHuman) {
        this.performEnemyTurn();
      }
    }, config.phaseTimingsMs.enemyTurnDelay);
  }

  performEnemyTurn() {
    const enemy = this.getCurrentPlayer();
    if (!enemy || enemy.isHuman || this.phase !== GamePhase.PLAYING) {
      return;
    }

    const opponents = this.aliveOpponentsOf(enemy);

    if (enemy.itemCount(ItemType.CARD_A) > 0 && chance(16)) {
      this.useCardA(enemy.id);
      return this.room.broadcastState();
    }
    if (enemy.itemCount(ItemType.CARD_KING) > 0 && chance(14)) {
      this.useCardKing(enemy.id);
      return this.room.broadcastState();
    }
    if (enemy.itemCount(ItemType.CARD_H) > 0 && enemy.hp < enemy.maxHp && chance(22)) {
      this.useCardHeart(enemy.id);
      return this.room.broadcastState();
    }
    if (enemy.itemCount(ItemType.CARD_C) > 0 && this.gunSystem.magazine.length > 0 && chance(18)) {
      this.useCardMind(enemy.id);
      return this.room.broadcastState();
    }
    if (enemy.itemCount(ItemType.CARD_QUEEN) > 0 && opponents.length > 0 && chance(14)) {
      const target = opponents[randomInt(0, opponents.length - 1)];
      this.useQueenOnTarget(enemy.id, target.id);
      return this.room.broadcastState();
    }

    const stealableOpponents = opponents.filter((player) => expandCards(player).length > 0);
    if (enemy.itemCount(ItemType.CARD_JOKER_KING) > 0 && stealableOpponents.length > 0 && chance(10)) {
      const target = stealableOpponents[randomInt(0, stealableOpponents.length - 1)];
      this.useJokerKingOnTarget(enemy.id, target.id);
      return this.room.broadcastState();
    }

    const shouldAttackOthers = opponents.length > 0 && !chance(35);
    if (shouldAttackOthers) {
      const target = opponents[randomInt(0, opponents.length - 1)];
      this.fireAtTarget(enemy, target);
    } else {
      this.fireAtTarget(enemy, enemy);
    }
    this.room.broadcastState();
  }

  snapshotForPlayer(localPlayerId) {
    const currentPlayer = this.getCurrentPlayer();
    const winner = this.winnerPlayer();
    const localPlayer = this.playerById(localPlayerId);
    const revealedLocalCards = this.revealedChestCards.get(localPlayerId) || [];
    const localVisibleCards = localPlayer
      ? (this.phase === GamePhase.CHEST_DRAW ? revealedLocalCards.slice() : expandCards(localPlayer))
      : [];

    return {
      players: this.players.map((player, index) => {
        const chat = this.room.chatMessageFor(player.id);
        const audioStatus = this.room.audioStatusFor(player.id);
        return {
          id: player.id,
          name: player.name,
          avatarKey: player.avatarKey,
          hp: player.hp,
          maxHp: player.maxHp,
          isAlive: player.isAlive,
          isHuman: player.isHuman,
          isCurrentTurn: index === this.currentTurnIndex,
          inventoryLabel: inventoryLabel(player),
          lastDamageAtMs: player.lastDamageAtMs,
          publicCards: this.phase === GamePhase.ROUND_INTRO || this.phase === GamePhase.AMMO_REVEAL
            ? []
            : this.phase === GamePhase.CHEST_DRAW
              ? (this.revealedChestCards.get(player.id) || []).slice()
              : expandCards(player),
          chatMessage: chat ? chat.text : null,
          chatExpiresAtMs: chat ? chat.expiresAtMs : 0,
          isRecordingAudio: audioStatus ? audioStatus.mode === "recording" : false,
          audioStatusText: audioStatus ? audioStatus.text : null,
          audioStatusExpiresAtMs: audioStatus ? audioStatus.expiresAtMs : 0,
        };
      }),
      turnLabel: `Turno de ${currentPlayer.name}`,
      statusLabel: this.statusMessage,
      ammoRemaining: this.gunSystem.remainingShells,
      roundNumber: this.currentRound,
      nextShellHint: this.hintByPlayerId.get(localPlayerId) || null,
      humanCards: localVisibleCards,
      drawsRemaining: (this.pendingChestCards.get(localPlayerId) || []).length,
      canDrawFromChest:
        this.phase === GamePhase.CHEST_DRAW &&
        this.getCurrentPlayer().id === localPlayerId &&
        (this.pendingChestCards.get(localPlayerId) || []).length > 0,
      phase: this.phase,
      roundIntroLabel: this.phase === GamePhase.ROUND_INTRO ? "PROXIMA RODADA" : null,
      ammoRevealLabel:
        this.phase === GamePhase.AMMO_REVEAL
          ? `${this.currentLoadout.liveShells} reais  |  ${this.currentLoadout.blankShells} festim`
          : null,
      winnerLabel:
        this.phase === GamePhase.GAME_OVER
          ? `${winner ? winner.name : "Sem vencedor"} vencedor`
          : null,
    };
  }

  indexForPlayerId(playerId) {
    if (playerId == null) {
      return this.players.findIndex((player) => player.isAlive);
    }

    const exactIndex = this.players.findIndex((player) => player.id === playerId && player.isAlive);
    if (exactIndex >= 0) {
      return exactIndex;
    }

    return this.players.findIndex((player) => player.isAlive);
  }
}

class Room {
  constructor(hostSocket, playerName, roomName, password, avatarKey) {
    this.id = createRoomCode();
    this.roomName = sanitizeRoomName(roomName, playerName);
    this.password = typeof password === "string" && password.trim().length > 0 ? password.trim() : null;
    this.status = "WAITING";
    this.matchStarted = false;
    this.hostPlayerId = 1;
    this.createdAt = Date.now();
    this.updatedAt = this.createdAt;
    this.emptySince = null;
    this.socketsByPlayerId = new Map();
    this.chatMessagesByPlayerId = new Map();
    this.audioStatusByPlayerId = new Map();
    this.seats = [{
      playerId: 1,
      seatIndex: 0,
      name: playerName,
      avatarKey: avatarKey || "smile",
      connected: true,
      host: true,
      human: true,
    }];
    this.game = new RoomGame(this);
    this.attachSocket(1, hostSocket);
  }

  attachSocket(playerId, socket) {
    const previousSocket = this.socketsByPlayerId.get(playerId);
    if (previousSocket && previousSocket !== socket) {
      previousSocket.playerId = null;
      previousSocket.roomId = null;
      try {
        previousSocket.close(1000, "Sessao substituida por reconexao");
      } catch (error) {
        console.warn("Falha ao encerrar socket anterior:", error.message || error);
      }
    }

    socket.playerId = playerId;
    socket.roomId = this.id;
    this.socketsByPlayerId.set(playerId, socket);
    const seat = this.seats.find((entry) => entry.playerId === playerId);
    if (seat) {
      seat.connected = true;
    }
    this.emptySince = null;
    this.updatedAt = Date.now();
  }

  detachSocket(playerId) {
    this.socketsByPlayerId.delete(playerId);
    const seat = this.seats.find((entry) => entry.playerId === playerId);
    if (seat) {
      seat.connected = false;
    }
    if (this.seats.filter((entry) => entry.human && entry.connected).length === 0) {
      this.emptySince = Date.now();
    }
    this.updatedAt = Date.now();
    this.promoteHostIfNeeded();
  }

  promoteHostIfNeeded() {
    const hostSeat = this.seats.find((seat) => seat.playerId === this.hostPlayerId);
    if (hostSeat && hostSeat.connected) {
      return;
    }
    const nextHost = this.seats.find((seat) => seat.human && seat.connected);
    if (!nextHost) {
      return;
    }
    this.hostPlayerId = nextHost.playerId;
    this.seats.forEach((seat) => {
      seat.host = seat.playerId === this.hostPlayerId;
    });
  }

  nextSeatIndex() {
    const used = new Set(this.seats.map((seat) => seat.seatIndex));
    for (let index = 0; index < config.rooms.maxPlayers; index += 1) {
      if (!used.has(index)) {
        return index;
      }
    }
    throw new Error("Nao ha assentos livres.");
  }

  canReconnect(requestedPlayerId) {
    return typeof requestedPlayerId === "number"
      && this.seats.some((seat) => seat.playerId === requestedPlayerId && seat.human);
  }

  requiresPassword(inputPassword) {
    if (!this.password) {
      return false;
    }
    return String(inputPassword || "").trim() !== this.password;
  }

  addHumanPlayer(socket, playerName, requestedPlayerId, avatarKey) {
    const reconnectSeat =
      typeof requestedPlayerId === "number"
        ? this.seats.find((seat) => seat.playerId === requestedPlayerId && seat.human)
        : null;

    if (reconnectSeat) {
      reconnectSeat.name = playerName || reconnectSeat.name;
      reconnectSeat.avatarKey = avatarKey || reconnectSeat.avatarKey || "smile";
      reconnectSeat.connected = true;
      this.attachSocket(reconnectSeat.playerId, socket);
      return reconnectSeat.playerId;
    }

    const humanSeats = this.seats.filter((seat) => seat.human);
    if (humanSeats.length >= config.rooms.maxPlayers) {
      throw new Error("A sala atingiu o limite de jogadores humanos.");
    }

    const seatIndex = this.nextSeatIndex();
    const playerId = seatIndex + 1;
    this.seats.push({
      playerId,
      seatIndex,
      name: playerName,
      avatarKey: avatarKey || "smile",
      connected: true,
      host: false,
      human: true,
    });
    this.attachSocket(playerId, socket);
    return playerId;
  }

  canStart() {
    const connectedHumans = this.seats.filter((seat) => seat.human && seat.connected).length;
    return connectedHumans >= config.rooms.minHumansToStart;
  }

  publicSummary() {
    const humanSeats = this.seats.filter((seat) => seat.human);
    const hostSeat = this.seats.find((seat) => seat.playerId === this.hostPlayerId);
    const isFull = humanSeats.length >= config.rooms.maxPlayers;
    return {
      roomId: this.id,
      roomName: this.roomName,
      hostName: hostSeat ? hostSeat.name : "Sem host",
      hasPassword: Boolean(this.password),
      playerCount: humanSeats.length,
      maxPlayers: config.rooms.maxPlayers,
      status: this.status,
      matchStarted: this.matchStarted,
      canJoin: !this.matchStarted && !isFull,
    };
  }

  startMatch(requestedByPlayerId) {
    if (requestedByPlayerId !== this.hostPlayerId) {
      throw new Error("Apenas o anfitriao pode iniciar a partida.");
    }
    if (!this.canStart()) {
      throw new Error("A sala ainda nao atingiu o minimo de jogadores humanos.");
    }
    const canRestart = this.matchStarted && this.game.phase === GamePhase.GAME_OVER;
    if (this.matchStarted && !canRestart) {
      throw new Error("A partida ja foi iniciada.");
    }
    this.matchStarted = true;
    this.status = "ACTIVE";
    this.chatMessagesByPlayerId.clear();
    this.audioStatusByPlayerId.clear();
    this.game.initializeMatch();
    this.broadcastState(canRestart ? "Revanche iniciada." : "Partida iniciada.");
  }

  serializedStateFor(localPlayerId) {
    return {
      roomId: this.id,
      roomName: this.roomName,
      localPlayerId,
      hostPlayerId: this.hostPlayerId,
      status: this.status,
      matchStarted: this.matchStarted,
      canStart: this.canStart(),
      players: this.seats
        .slice()
        .sort((a, b) => a.seatIndex - b.seatIndex)
        .map((seat) => ({
          playerId: seat.playerId,
          seatIndex: seat.seatIndex,
          name: seat.name,
          avatarKey: seat.avatarKey || "smile",
          connected: seat.connected,
          host: seat.host,
          human: seat.human,
        })),
      snapshot: this.matchStarted ? this.game.snapshotForPlayer(localPlayerId) : null,
      message: this.matchStarted ? this.game.statusMessage : "Sala aguardando inicio.",
      updatedAt: this.matchStarted ? this.game.updatedAt : this.updatedAt,
      actionSequence: this.matchStarted ? this.game.actionSequence : 0,
      lastAction: this.matchStarted ? this.game.lastAction : null,
    };
  }

  broadcastState(messageOverride = null) {
    this.cleanupExpiredChatMessages();
    this.cleanupExpiredAudioStatuses();
    for (const seat of this.seats.filter((entry) => entry.human)) {
      const socket = this.socketsByPlayerId.get(seat.playerId);
      if (!socket || socket.readyState !== socket.OPEN) {
        continue;
      }
      const roomState = this.serializedStateFor(seat.playerId);
      if (messageOverride) {
        roomState.message = messageOverride;
      }
      socket.send(JSON.stringify({
        type: "room_state",
        message: roomState.message,
        roomState,
      }));
    }
  }

  setChatMessage(playerId, text) {
    this.chatMessagesByPlayerId.set(playerId, {
      text,
      expiresAtMs: Date.now() + 7000,
    });
    this.updatedAt = Date.now();
  }

  chatMessageFor(playerId) {
    const message = this.chatMessagesByPlayerId.get(playerId) || null;
    if (!message) {
      return null;
    }
    if (message.expiresAtMs <= Date.now()) {
      this.chatMessagesByPlayerId.delete(playerId);
      return null;
    }
    return message;
  }

  cleanupExpiredChatMessages() {
    const now = Date.now();
    for (const [playerId, message] of this.chatMessagesByPlayerId.entries()) {
      if (message.expiresAtMs <= now) {
        this.chatMessagesByPlayerId.delete(playerId);
      }
    }
  }

  setVoiceRecordingState(playerId, recording) {
    if (!recording) {
      this.audioStatusByPlayerId.delete(playerId);
      this.updatedAt = Date.now();
      return;
    }

    this.audioStatusByPlayerId.set(playerId, {
      mode: "recording",
      text: "Audio",
      expiresAtMs: Date.now() + 10_000,
    });
    this.updatedAt = Date.now();
  }

  setSpeakingState(playerId, durationMs) {
    this.audioStatusByPlayerId.set(playerId, {
      mode: "speaking",
      text: "Audio",
      expiresAtMs: Date.now() + Math.max(1500, Math.min(Number(durationMs) || 0, 8_000)),
    });
    this.updatedAt = Date.now();
  }

  audioStatusFor(playerId) {
    const status = this.audioStatusByPlayerId.get(playerId) || null;
    if (!status) {
      return null;
    }
    if (status.expiresAtMs <= Date.now()) {
      this.audioStatusByPlayerId.delete(playerId);
      return null;
    }
    return status;
  }

  cleanupExpiredAudioStatuses() {
    const now = Date.now();
    for (const [playerId, status] of this.audioStatusByPlayerId.entries()) {
      if (status.expiresAtMs <= now) {
        this.audioStatusByPlayerId.delete(playerId);
      }
    }
  }

  broadcastVoiceMessage(originPlayerId, payload) {
    for (const seat of this.seats.filter((entry) => entry.human && entry.playerId !== originPlayerId)) {
      const socket = this.socketsByPlayerId.get(seat.playerId);
      if (!socket || socket.readyState !== socket.OPEN) {
        continue;
      }
      socket.send(JSON.stringify({
        type: "voice_message",
        message: `${payload.playerName} enviou audio.`,
        voiceMessage: payload,
      }));
    }
  }
}

const rooms = new Map();
const EMPTY_ROOM_GRACE_MS = 15000;

function findRoom(roomId) {
  const room = rooms.get(String(roomId || "").trim().toUpperCase());
  if (!room) {
    throw new Error("Sala nao encontrada.");
  }
  return room;
}

function listPublicRooms() {
  return Array.from(rooms.values())
    .filter((room) => !room.matchStarted)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .map((room) => room.publicSummary());
}

function sendRoomList(ws, message) {
  safeSend(ws, {
    type: "room_list",
    message: message || null,
    rooms: listPublicRooms(),
  });
}

function broadcastRoomList(message) {
  for (const client of connectedClients) {
    sendRoomList(client, message || null);
  }
}

function cleanupEmptyRooms() {
  if (!config.rooms.destroyWhenEmpty) {
    return;
  }

  const now = Date.now();
  for (const room of rooms.values()) {
    const connectedHumanCount = room.seats.filter((seat) => seat.human && seat.connected).length;
    if (connectedHumanCount > 0 || !room.emptySince) {
      continue;
    }
    if ((now - room.emptySince) <= EMPTY_ROOM_GRACE_MS) {
      continue;
    }
    room.game.clearTimers();
    rooms.delete(room.id);
  }
}

async function maybePublishFirebaseDomain() {
  const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
  const databaseUrl = process.env.FIREBASE_DATABASE_URL;
  const publishedUrl = process.env.PUBLISHED_HTTPS_URL || config.firebasePublish.publishedHttpsUrl;

  if (!serviceAccountPath || !databaseUrl || !publishedUrl || publishedUrl.includes("SEU_DOMINIO_PUBLICO")) {
    console.log("Firebase publish skipped. Configure FIREBASE_SERVICE_ACCOUNT_PATH, FIREBASE_DATABASE_URL e PUBLISHED_HTTPS_URL.");
    return;
  }

  const admin = require("firebase-admin");
  const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, "utf8"));
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    databaseURL: databaseUrl,
  });
  await admin.database().ref(config.firebasePublish.realtimeDatabasePath).set(publishedUrl);
  console.log(`Firebase publish OK: ${config.firebasePublish.realtimeDatabasePath} => ${publishedUrl}`);
}

function handleAction(ws, parsed) {
  const action = parsed.action;
  const payload = parsed.payload || {};

  switch (action) {
    case "list_rooms": {
      sendRoomList(ws);
      return;
    }
    case "create_room": {
      const playerName = String(payload.playerName || "").trim() || "Jogador";
      const roomName = sanitizeRoomName(payload.roomName, playerName);
      const password = String(payload.password || "").trim() || null;
      const avatarKey = String(payload.avatarKey || "").trim() || "smile";
      const room = new Room(ws, playerName, roomName, password, avatarKey);
      rooms.set(room.id, room);
      safeSend(ws, {
        type: "room_state",
        message: `${room.roomName} criada.`,
        roomState: room.serializedStateFor(1),
      });
      room.broadcastState(`${room.roomName} criada.`);
      broadcastRoomList();
      return;
    }
    case "join_room": {
      const room = findRoom(payload.roomId);
      const playerName = String(payload.playerName || "").trim() || "Jogador";
      const avatarKey = String(payload.avatarKey || "").trim() || "smile";
      const requestedPlayerId = typeof payload.requestedPlayerId === "number" ? payload.requestedPlayerId : null;

      if (room.matchStarted && !room.canReconnect(requestedPlayerId)) {
        throw new Error("Essa partida ja foi iniciada.");
      }

      if (!room.canReconnect(requestedPlayerId) && room.requiresPassword(payload.password)) {
        throw new Error("Senha da sala incorreta.");
      }

      room.addHumanPlayer(
        ws,
        playerName,
        requestedPlayerId,
        avatarKey
      );
      room.broadcastState(`${playerName} entrou na sala.`);
      broadcastRoomList();
      return;
    }
    case "start_match": {
      const room = findRoom(payload.roomId);
      room.startMatch(payload.playerId);
      broadcastRoomList();
      return;
    }
    case "reveal_chest": {
      const room = findRoom(payload.roomId);
      room.game.revealNextChestCard(payload.playerId);
      room.broadcastState();
      return;
    }
    case "shoot_self": {
      const room = findRoom(payload.roomId);
      room.game.shootSelf(payload.playerId);
      room.broadcastState();
      return;
    }
    case "shoot_target": {
      const room = findRoom(payload.roomId);
      room.game.shootTarget(payload.playerId, payload.targetId);
      room.broadcastState();
      return;
    }
    case "surrender": {
      const room = findRoom(payload.roomId);
      room.game.surrender(payload.playerId);
      room.broadcastState();
      return;
    }
    case "use_item": {
      const room = findRoom(payload.roomId);
      room.game.useItem(payload.playerId, payload.itemType);
      room.broadcastState();
      return;
    }
    case "use_target_item": {
      const room = findRoom(payload.roomId);
      room.game.useTargetItem(payload.playerId, payload.itemType, payload.targetId, payload.targetCardType || null);
      room.broadcastState();
      return;
    }
    case "send_chat": {
      const room = findRoom(payload.roomId);
      const player = room.seats.find((seat) => seat.playerId === payload.playerId && seat.human);
      if (!player) {
        throw new Error("Jogador invalido para enviar mensagem.");
      }
      const text = String(payload.text || "").trim().replace(/\s+/g, " ").slice(0, 32);
      if (!text) {
        throw new Error("Mensagem vazia.");
      }
      room.setChatMessage(player.playerId, text);
      room.broadcastState();
      return;
    }
    case "voice_recording": {
      const room = findRoom(payload.roomId);
      const player = room.seats.find((seat) => seat.playerId === payload.playerId && seat.human);
      if (!player) {
        throw new Error("Jogador invalido para gravacao.");
      }
      room.setVoiceRecordingState(player.playerId, Boolean(payload.recording));
      room.broadcastState();
      return;
    }
    case "voice_message": {
      const room = findRoom(payload.roomId);
      const player = room.seats.find((seat) => seat.playerId === payload.playerId && seat.human);
      if (!player) {
        throw new Error("Jogador invalido para audio.");
      }

      const audioBase64 = String(payload.audioBase64 || "").trim();
      const mimeType = String(payload.mimeType || "audio/mp4").trim() || "audio/mp4";
      const durationMs = Math.max(250, Math.min(Number(payload.durationMs) || 0, 8_000));
      if (!audioBase64) {
        throw new Error("Audio vazio.");
      }
      if (audioBase64.length > 1_000_000) {
        throw new Error("Audio excede o limite permitido.");
      }

      room.setVoiceRecordingState(player.playerId, false);
      room.setSpeakingState(player.playerId, durationMs);
      room.broadcastState();
      room.broadcastVoiceMessage(player.playerId, {
        playerId: player.playerId,
        playerName: player.name,
        mimeType,
        audioBase64,
        durationMs,
      });
      return;
    }
    default:
      throw new Error(`Acao desconhecida: ${action}`);
  }
}

function cleanupSocket(ws) {
  connectedClients.delete(ws);

  if (!ws.roomId || !ws.playerId) {
    return;
  }
  const room = rooms.get(ws.roomId);
  if (!room) {
    return;
  }

  const activeSocket = room.socketsByPlayerId.get(ws.playerId);
  if (activeSocket && activeSocket !== ws) {
    return;
  }

  room.detachSocket(ws.playerId);
  room.broadcastState("Conexao de um jogador foi encerrada.");

  cleanupEmptyRooms();
  broadcastRoomList();
}

function proxyClusterRequest(req, res) {
  const proxyReq = http.request({
    hostname: CLUSTER_PROXY_HOST,
    port: CLUSTER_PROXY_PORT,
    path: req.url || "/",
    method: req.method,
    headers: {
      ...req.headers,
      host: `${CLUSTER_PROXY_HOST}:${CLUSTER_PROXY_PORT}`,
      connection: "close"
    }
  }, (proxyRes) => {
    res.writeHead(proxyRes.statusCode || 502, proxyRes.headers);
    proxyRes.pipe(res);
  });

  proxyReq.on("error", (error) => {
    console.error("Cluster proxy failed:", error);
    if (!res.headersSent) {
      res.writeHead(502, { "content-type": "application/json" });
    }
    res.end(JSON.stringify({
      error: "cluster_unavailable",
      message: "Painel do cluster indisponivel no momento."
    }));
  });

  req.pipe(proxyReq);
}

const server = http.createServer((req, res) => {
  if (req.url === "/health") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({
      ok: true,
      service: config.service,
      rooms: rooms.size,
      wsPath: config.transport.path,
    }));
    return;
  }

  proxyClusterRequest(req, res);
});

const wss = new WebSocketServer({
  server,
  path: config.transport.path,
});

wss.on("connection", (ws) => {
  connectedClients.add(ws);

  safeSend(ws, {
    type: "hello",
    message: "Conexao estabelecida com o servidor autoritativo de Obscure Shell.",
  });
  sendRoomList(ws);

  ws.on("message", (rawMessage) => {
    const text = rawMessage.toString("utf8");
    if (text === "ping") {
      ws.send("pong");
      return;
    }

    try {
      const parsed = JSON.parse(text);
      handleAction(ws, parsed);
    } catch (error) {
      safeSend(ws, {
        type: "error",
        message: error.message || "Falha ao processar acao.",
      });
    }
  });

  ws.on("close", () => cleanupSocket(ws));
  ws.on("error", () => cleanupSocket(ws));
});

server.listen(config.transport.port, config.transport.host, async () => {
  console.log(
    `[${new Date().toISOString()}] ${config.service} ouvindo em ws://${config.transport.host}:${config.transport.port}${config.transport.path}`
  );
  try {
    await maybePublishFirebaseDomain();
  } catch (error) {
    console.error("Firebase publish failed:", error);
  }
});

setInterval(cleanupEmptyRooms, 5000);

