import test from 'node:test';
import assert from 'node:assert/strict';
import { CHOICES, WINNERS, evaluateWinner } from '../src/gameLogic.js';
import { RoomManager } from '../src/roomManager.js';

test('1. A player creates a room', () => {
  const manager = new RoomManager();
  const { room, sessionToken } = manager.createRoom('WarriorAlice', 'p1_123');
  assert.ok(room.roomId.startsWith('RPS-'));
  assert.equal(room.player1.username, 'WarriorAlice');
  assert.equal(room.player2, null);
  assert.equal(room.status, 'WAITING_FOR_OPPONENT');
  assert.ok(sessionToken);
  manager.close();
});

test('2. A second player joins using the room code', () => {
  const manager = new RoomManager();
  const { room: created } = manager.createRoom('Alice', 'p1_123');
  const { room: joined } = manager.joinRoom(created.roomId, 'Bob', 'p2_456');
  assert.equal(joined.player2.username, 'Bob');
  assert.equal(joined.status, 'IN_PROGRESS');
  manager.close();
});

test('3. A third player is rejected', () => {
  const manager = new RoomManager();
  const { room } = manager.createRoom('Alice', 'p1');
  manager.joinRoom(room.roomId, 'Bob', 'p2');

  assert.throws(() => {
    manager.joinRoom(room.roomId, 'Charlie', 'p3');
  }, /full \(maximum 2 players\)/);
  manager.close();
});

test('4. Both players exchange chat messages', () => {
  const manager = new RoomManager();
  const { room } = manager.createRoom('Alice', 'p1');
  manager.joinRoom(room.roomId, 'Bob', 'p2');

  const msg1 = manager.sendChatMessage(room.roomId, 'p1', 'Good luck Bob!');
  assert.equal(msg1.text, 'Good luck Bob!');
  assert.equal(msg1.senderName, 'Alice');

  const msg2 = manager.sendChatMessage(room.roomId, 'p2', 'You too Alice!');
  assert.equal(msg2.text, 'You too Alice!');
  assert.equal(msg2.senderName, 'Bob');
  manager.close();
});

test('5. Rock beats Scissors', () => {
  const winner = evaluateWinner(CHOICES.ROCK, CHOICES.SCISSORS);
  assert.equal(winner, WINNERS.PLAYER_ONE);
});

test('6. Scissors beats Paper', () => {
  const winner = evaluateWinner(CHOICES.SCISSORS, CHOICES.PAPER);
  assert.equal(winner, WINNERS.PLAYER_ONE);
});

test('7. Paper beats Rock', () => {
  const winner = evaluateWinner(CHOICES.PAPER, CHOICES.ROCK);
  assert.equal(winner, WINNERS.PLAYER_ONE);
});

test('8. Identical choices produce a draw', () => {
  assert.equal(evaluateWinner(CHOICES.ROCK, CHOICES.ROCK), WINNERS.DRAW);
  assert.equal(evaluateWinner(CHOICES.PAPER, CHOICES.PAPER), WINNERS.DRAW);
  assert.equal(evaluateWinner(CHOICES.SCISSORS, CHOICES.SCISSORS), WINNERS.DRAW);
});

test('9. Scores update correctly', () => {
  const manager = new RoomManager();
  const { room } = manager.createRoom('Alice', 'p1');
  manager.joinRoom(room.roomId, 'Bob', 'p2');

  // Alice plays Rock, Bob plays Scissors -> Alice wins
  manager.submitChoice(room.roomId, 'p1', CHOICES.ROCK);
  const result = manager.submitChoice(room.roomId, 'p2', CHOICES.SCISSORS);

  assert.equal(result.room.player1.score, 1);
  assert.equal(result.room.player2.score, 0);
  assert.equal(result.room.lastWinner, WINNERS.PLAYER_ONE);
  manager.close();
});

test('10. A disconnected player can reconnect within the grace period', () => {
  const manager = new RoomManager();
  const { room, sessionToken } = manager.createRoom('Alice', 'p1_socket_1');
  manager.handleDisconnect('p1_socket_1');

  // Reconnect with same sessionToken
  const reconnected = manager.joinRoom(room.roomId, 'Alice', 'p1_socket_2', sessionToken);
  assert.equal(reconnected.room.player1.isConnected, true);
  assert.equal(reconnected.room.player1.id, 'p1_socket_2');
  manager.close();
});

test('11. Invalid room codes are rejected', () => {
  const manager = new RoomManager();
  assert.throws(() => {
    manager.joinRoom('INVALID-9999', 'Bob', 'p2');
  }, /does not exist or has expired/);
  manager.close();
});

test('12. A player cannot impersonate the opponent', () => {
  const manager = new RoomManager();
  const { room } = manager.createRoom('Alice', 'p1');
  manager.joinRoom(room.roomId, 'Bob', 'p2');

  assert.throws(() => {
    manager.submitChoice(room.roomId, 'stranger_hacker', CHOICES.ROCK);
  }, /Unauthorized player/);
  manager.close();
});

test('13. A player cannot reveal their choice before the opponent submits', () => {
  const manager = new RoomManager();
  const { room } = manager.createRoom('Alice', 'p1');
  manager.joinRoom(room.roomId, 'Bob', 'p2');

  const partial = manager.submitChoice(room.roomId, 'p1', CHOICES.ROCK);
  assert.equal(partial.revealed, false);
  assert.equal(partial.room.player1.revealedChoice, null);
  manager.close();
});

test('14. A player cannot submit multiple choices in the same round', () => {
  const manager = new RoomManager();
  const { room } = manager.createRoom('Alice', 'p1');
  manager.joinRoom(room.roomId, 'Bob', 'p2');

  manager.submitChoice(room.roomId, 'p1', CHOICES.ROCK);
  assert.throws(() => {
    manager.submitChoice(room.roomId, 'p1', CHOICES.PAPER);
  }, /Choice already submitted/);
  manager.close();
});

test('15. Abandoned rooms are cleaned up correctly', () => {
  // Room with 50ms timeout for testing cleanup
  const manager = new RoomManager(50);
  const { room } = manager.createRoom('Alice', 'p1');
  assert.ok(manager.getRoom(room.roomId));

  // Advance time
  room.lastActiveAt = Date.now() - 100;
  manager.cleanupAbandonedRooms();

  assert.equal(manager.getRoom(room.roomId), undefined);
  manager.close();
});
