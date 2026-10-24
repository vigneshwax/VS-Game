import crypto from 'node:crypto';
import { CHOICES, WINNERS, evaluateWinner, isValidChoice } from './gameLogic.js';

export class RoomManager {
  constructor(roomTimeoutMs = 30 * 60 * 1000) {
    // In-memory JavaScript Maps on the server
    this.rooms = new Map();
    this.secretChoices = new Map(); // roomId -> Map(playerId -> choice)
    this.lastChatTimestamps = new Map(); // playerId -> timestamp
    this.roomTimeoutMs = roomTimeoutMs;

    // Periodic cleanup of abandoned rooms
    this.cleanupInterval = setInterval(() => this.cleanupAbandonedRooms(), 60000);
  }

  generateRoomId() {
    let id;
    do {
      id = 'RPS-' + crypto.randomInt(1000, 9999);
    } while (this.rooms.has(id));
    return id;
  }

  createRoom(username, playerId, sessionToken) {
    const cleanName = (username || '').trim();
    if (!cleanName) {
      throw new Error('Username is required');
    }

    const roomId = this.generateRoomId();
    const token = sessionToken || crypto.randomUUID();

    const room = {
      roomId,
      player1: {
        id: playerId,
        username: cleanName,
        sessionToken: token,
        score: 0,
        hasSubmitted: false,
        revealedChoice: null,
        isConnected: true
      },
      player2: null,
      roundNumber: 1,
      draws: 0,
      status: 'WAITING_FOR_OPPONENT',
      lastWinner: WINNERS.UNDECIDED,
      chatMessages: [
        {
          id: crypto.randomUUID(),
          senderId: 'SYSTEM',
          senderName: 'Arena Arbiter',
          text: `Welcome to room ${roomId}! Waiting for challenger...`,
          timestamp: Date.now(),
          isSystem: true
        }
      ],
      createdAt: Date.now(),
      lastActiveAt: Date.now()
    };

    this.rooms.set(roomId, room);
    this.secretChoices.set(roomId, new Map());
    return { room, sessionToken: token };
  }

  joinRoom(roomId, username, playerId, sessionToken) {
    const cleanCode = (roomId || '').trim().toUpperCase();
    const cleanName = (username || '').trim();
    if (!cleanName) {
      throw new Error('Username is required');
    }

    const room = this.rooms.get(cleanCode);
    if (!room) {
      throw new Error(`Room ${cleanCode} does not exist or has expired`);
    }

    // Reconnection check
    if (room.player1.id === playerId || (sessionToken && room.player1.sessionToken === sessionToken)) {
      room.player1.isConnected = true;
      room.player1.id = playerId;
      room.lastActiveAt = Date.now();
      return { room, sessionToken: room.player1.sessionToken };
    }

    if (room.player2) {
      if (room.player2.id === playerId || (sessionToken && room.player2.sessionToken === sessionToken)) {
        room.player2.isConnected = true;
        room.player2.id = playerId;
        room.lastActiveAt = Date.now();
        return { room, sessionToken: room.player2.sessionToken };
      }
      throw new Error(`Room ${cleanCode} is full (maximum 2 players)`);
    }

    // Add Player 2
    const token = sessionToken || crypto.randomUUID();
    room.player2 = {
      id: playerId,
      username: cleanName,
      sessionToken: token,
      score: 0,
      hasSubmitted: false,
      revealedChoice: null,
      isConnected: true
    };
    room.status = 'IN_PROGRESS';
    room.lastActiveAt = Date.now();
    room.chatMessages.push({
      id: crypto.randomUUID(),
      senderId: 'SYSTEM',
      senderName: 'Arena Arbiter',
      text: `${cleanName} joined the duel! Select your moves.`,
      timestamp: Date.now(),
      isSystem: true
    });

    return { room, sessionToken: token };
  }

  getRoom(roomId) {
    return this.rooms.get((roomId || '').trim().toUpperCase());
  }

  submitChoice(roomId, playerId, choice) {
    const room = this.getRoom(roomId);
    if (!room) throw new Error('Room not found');
    if (!isValidChoice(choice)) throw new Error('Invalid choice');
    if (room.status !== 'IN_PROGRESS') throw new Error('Round is not currently active');

    const secrets = this.secretChoices.get(room.roomId);
    if (!secrets) throw new Error('Room state error');

    if (secrets.has(playerId)) {
      throw new Error('Choice already submitted for this round');
    }

    const isP1 = room.player1.id === playerId;
    const isP2 = room.player2 && room.player2.id === playerId;
    if (!isP1 && !isP2) throw new Error('Unauthorized player');

    // Store secretly on server side
    secrets.set(playerId, choice);
    if (isP1) room.player1.hasSubmitted = true;
    if (isP2) room.player2.hasSubmitted = true;
    room.lastActiveAt = Date.now();

    // Check if both players have submitted
    const p1Choice = secrets.get(room.player1.id);
    const p2Choice = room.player2 ? secrets.get(room.player2.id) : null;

    if (p1Choice && p2Choice) {
      // Authoritative simultaneous reveal
      const winner = evaluateWinner(p1Choice, p2Choice);
      room.player1.revealedChoice = p1Choice;
      room.player2.revealedChoice = p2Choice;
      room.lastWinner = winner;
      room.status = 'ROUND_REVEAL';

      if (winner === WINNERS.PLAYER_ONE) room.player1.score += 1;
      else if (winner === WINNERS.PLAYER_TWO) room.player2.score += 1;
      else if (winner === WINNERS.DRAW) room.draws += 1;

      const outcomeText = winner === WINNERS.DRAW
        ? `Round ${room.roundNumber} Draw! Both warriors chose ${p1Choice}.`
        : `${winner === WINNERS.PLAYER_ONE ? room.player1.username : room.player2.username} wins Round ${room.roundNumber}! (${winner === WINNERS.PLAYER_ONE ? p1Choice : p2Choice} beats ${winner === WINNERS.PLAYER_ONE ? p2Choice : p1Choice})`;

      room.chatMessages.push({
        id: crypto.randomUUID(),
        senderId: 'SYSTEM',
        senderName: 'Arena Arbiter',
        text: outcomeText,
        timestamp: Date.now(),
        isSystem: true
      });

      return { room, revealed: true };
    }

    // Still waiting for second player — do NOT reveal hidden choice
    return { room: this.getSanitizedRoom(room), revealed: false };
  }

  nextRound(roomId, playerId) {
    const room = this.getRoom(roomId);
    if (!room) throw new Error('Room not found');

    const secrets = this.secretChoices.get(room.roomId);
    if (secrets) secrets.clear();

    room.roundNumber += 1;
    room.status = 'IN_PROGRESS';
    room.lastWinner = WINNERS.UNDECIDED;
    room.player1.hasSubmitted = false;
    room.player1.revealedChoice = null;
    if (room.player2) {
      room.player2.hasSubmitted = false;
      room.player2.revealedChoice = null;
    }
    room.lastActiveAt = Date.now();

    room.chatMessages.push({
      id: crypto.randomUUID(),
      senderId: 'SYSTEM',
      senderName: 'Arena Arbiter',
      text: `Round ${room.roundNumber} started! Lock in your moves.`,
      timestamp: Date.now(),
      isSystem: true
    });

    return room;
  }

  sendChatMessage(roomId, playerId, text) {
    const room = this.getRoom(roomId);
    if (!room) throw new Error('Room not found');

    const trimmed = (text || '').trim();
    if (!trimmed) throw new Error('Empty message not allowed');
    if (trimmed.length > 500) throw new Error('Message exceeds 500 characters');

    // Rate limiting: 200ms
    const now = Date.now();
    const lastTime = this.lastChatTimestamps.get(playerId) || 0;
    if (now - lastTime < 200) {
      throw new Error('Rate limit exceeded: please slow down');
    }
    this.lastChatTimestamps.set(playerId, now);

    let sender = null;
    if (room.player1.id === playerId) sender = room.player1;
    else if (room.player2 && room.player2.id === playerId) sender = room.player2;
    if (!sender) throw new Error('Unauthorized sender');

    const msg = {
      id: crypto.randomUUID(),
      senderId: sender.id,
      senderName: sender.username,
      text: trimmed,
      timestamp: now,
      isSystem: false
    };

    room.chatMessages.push(msg);
    room.lastActiveAt = now;
    return msg;
  }

  handleDisconnect(playerId) {
    for (const [roomId, room] of this.rooms.entries()) {
      let changed = false;
      if (room.player1 && room.player1.id === playerId) {
        room.player1.isConnected = false;
        changed = true;
      }
      if (room.player2 && room.player2.id === playerId) {
        room.player2.isConnected = false;
        changed = true;
      }
      if (changed) {
        room.lastActiveAt = Date.now();
        room.chatMessages.push({
          id: crypto.randomUUID(),
          senderId: 'SYSTEM',
          senderName: 'Arena Arbiter',
          text: 'A warrior disconnected temporarily.',
          timestamp: Date.now(),
          isSystem: true
        });
        return room;
      }
    }
    return null;
  }

  getSanitizedRoom(room) {
    // If round is in progress, ensure hidden choices are never leaked to client payload
    if (room.status === 'IN_PROGRESS') {
      return {
        ...room,
        player1: { ...room.player1, revealedChoice: null },
        player2: room.player2 ? { ...room.player2, revealedChoice: null } : null
      };
    }
    return room;
  }

  cleanupAbandonedRooms() {
    const now = Date.now();
    for (const [roomId, room] of this.rooms.entries()) {
      if (now - room.lastActiveAt > this.roomTimeoutMs) {
        this.rooms.delete(roomId);
        this.secretChoices.delete(roomId);
      }
    }
  }

  close() {
    if (this.cleanupInterval) clearInterval(this.cleanupInterval);
  }
}
