import http from 'node:http';
import net from 'node:net';
import { RoomManager } from './roomManager.js';

const HTTP_PORT = process.env.PORT || 3001;
const TCP_PORT = process.env.TCP_PORT || 8887;
const roomManager = new RoomManager();

console.log(`[RPS Arena Server] Initializing authoritative backend...`);

// --- 1. TCP SOCKET SERVER (Direct Android / Native Client Support) ---
const tcpClients = new Map(); // socket -> { playerId, roomId }

const tcpServer = net.createServer((socket) => {
  const remoteAddr = `${socket.remoteAddress}:${socket.remotePort}`;
  console.log(`[TCP CONNECT] Client connected from ${remoteAddr}`);

  let buffer = '';

  socket.on('data', (chunk) => {
    buffer += chunk.toString('utf-8');
    let boundary = buffer.indexOf('\n');
    while (boundary !== -1) {
      const line = buffer.substring(0, boundary).trim();
      buffer = buffer.substring(boundary + 1);
      boundary = buffer.indexOf('\n');

      if (!line) continue;
      handleTcpMessage(socket, line);
    }
  });

  socket.on('error', (err) => {
    console.log(`[TCP ERROR] ${remoteAddr}: ${err.message}`);
  });

  socket.on('close', () => {
    console.log(`[TCP DISCONNECT] Client disconnected from ${remoteAddr}`);
    const clientData = tcpClients.get(socket);
    if (clientData && clientData.playerId) {
      const updated = roomManager.handleDisconnect(clientData.playerId);
      if (updated) {
        broadcastRoomState(updated);
      }
    }
    tcpClients.delete(socket);
  });
});

function handleTcpMessage(socket, raw) {
  try {
    const data = JSON.parse(raw);
    const { action, playerId } = data;

    if (playerId) {
      const existing = tcpClients.get(socket) || {};
      tcpClients.set(socket, { ...existing, playerId });
    }

    switch (action) {
      case 'create_room': {
        const { username } = data;
        const { room } = roomManager.createRoom(username, playerId);
        console.log(`[CREATE_ROOM] Room ${room.roomId} created by ${username} (${playerId})`);
        const existing = tcpClients.get(socket) || {};
        tcpClients.set(socket, { ...existing, roomId: room.roomId });
        sendTcpRoomState(socket, room);
        break;
      }
      case 'join_room': {
        const { roomId, username } = data;
        const { room } = roomManager.joinRoom(roomId, username, playerId);
        console.log(`[JOIN_ROOM] ${username} (${playerId}) joined ${room.roomId}`);
        const existing = tcpClients.get(socket) || {};
        tcpClients.set(socket, { ...existing, roomId: room.roomId });
        broadcastRoomState(room);
        break;
      }
      case 'submit_choice': {
        const { roomId, choice } = data;
        console.log(`[MOVE] Player ${playerId} submitted in ${roomId}`);
        const { room } = roomManager.submitChoice(roomId, playerId, choice);
        broadcastRoomState(room);
        break;
      }
      case 'ready_next': {
        const { roomId } = data;
        console.log(`[READY_NEXT] Player ${playerId} ready for next round in ${roomId}`);
        const room = roomManager.nextRound(roomId, playerId);
        broadcastRoomState(room);
        break;
      }
      case 'chat_message': {
        const { roomId, text } = data;
        roomManager.sendChatMessage(roomId, playerId, text);
        const room = roomManager.getRoom(roomId);
        if (room) broadcastRoomState(room);
        break;
      }
      default:
        console.log(`[WARN] Unknown action: ${action}`);
    }
  } catch (err) {
    console.log(`[ACTION ERROR] ${err.message}`);
    sendTcpError(socket, err.message);
  }
}

function sendTcpRoomState(socket, room) {
  const payload = JSON.stringify({ type: 'room_state', ...room }) + '\n';
  socket.write(payload);
}

function broadcastRoomState(room) {
  const payload = JSON.stringify({ type: 'room_state', ...room }) + '\n';
  for (const [s, data] of tcpClients.entries()) {
    if (data.roomId === room.roomId && !s.destroyed) {
      s.write(payload);
    }
  }
}

function sendTcpError(socket, message) {
  const payload = JSON.stringify({ type: 'error', message }) + '\n';
  socket.write(payload);
}

tcpServer.listen(TCP_PORT, '0.0.0.0', () => {
  console.log(`[TCP Server] Listening on 0.0.0.0:${TCP_PORT} (Android direct socket protocol)`);
});

// --- 2. HTTP REST & HEALTH SERVER ---
const httpServer = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  if (url.pathname === '/health' || url.pathname === '/') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      service: 'RPS Arena Backend',
      tcpPort: TCP_PORT,
      httpPort: HTTP_PORT,
      activeRooms: roomManager.rooms.size,
      timestamp: Date.now()
    }));
    return;
  }

  if (req.method === 'GET' && url.pathname === '/api/rooms/get') {
    const roomId = url.searchParams.get('roomId');
    const room = roomManager.getRoom(roomId);
    if (!room) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Room not found' }));
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(roomManager.getSanitizedRoom(room)));
    return;
  }

  if (req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        const data = body ? JSON.parse(body) : {};

        if (url.pathname === '/api/rooms/create') {
          const { username, playerId, sessionToken } = data;
          const result = roomManager.createRoom(username, playerId || 'p1_' + Date.now(), sessionToken);
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(result));
          return;
        }

        if (url.pathname === '/api/rooms/join') {
          const { roomId, username, playerId, sessionToken } = data;
          const result = roomManager.joinRoom(roomId, username, playerId || 'p2_' + Date.now(), sessionToken);
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(result));
          return;
        }

        if (url.pathname === '/api/game/move') {
          const { roomId, playerId, choice } = data;
          const result = roomManager.submitChoice(roomId, playerId, choice);
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(result));
          return;
        }

        if (url.pathname === '/api/chat/send') {
          const { roomId, playerId, text } = data;
          const msg = roomManager.sendChatMessage(roomId, playerId, text);
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: true, message: msg }));
          return;
        }

        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Endpoint not found' }));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: err.message }));
      }
    });
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Not found' }));
});

httpServer.listen(HTTP_PORT, '0.0.0.0', () => {
  console.log(`[HTTP Server] Listening on 0.0.0.0:${HTTP_PORT}`);
});
