/**
 * RPS Arena — Husband & Wife Google Apps Script Backend (HIGH PERFORMANCE OPTIMIZED)
 * Features:
 * - Smart In-Memory & Script Caching (avoids redundant spreadsheet scans)
 * - State Versioning & ETag (returns 304-equivalent { unchanged: true } when no moves/chat changed)
 * - Concurrency LockService (guarantees zero race conditions during simultaneous move submissions)
 * - Fast Batch Data Processing & Display Name Cache
 */

// Spreadsheet Sheet Names
var SHEETS = {
  USERS: 'Users',
  SESSIONS: 'Sessions',
  CHAT: 'Chat',
  GAME_MOVES: 'GameMoves',
  GAME_RESULTS: 'GameResults',
  EVENTS: 'Events'
};

var HEADERS = {
  Users: ['userId', 'displayName', 'role', 'passwordHash', 'createdAt', 'lastLoginAt', 'status'],
  Sessions: ['sessionId', 'husbandId', 'wifeId', 'sessionStatus', 'createdAt', 'updatedAt'],
  Chat: ['messageId', 'sessionId', 'senderId', 'senderRole', 'message', 'timestamp'],
  GameMoves: ['moveId', 'sessionId', 'roundNumber', 'playerId', 'move', 'submittedAt'],
  GameResults: ['resultId', 'sessionId', 'roundNumber', 'winnerId', 'result', 'recordedAt'],
  Events: ['eventId', 'sessionId', 'userId', 'eventType', 'eventData', 'timestamp']
};

/**
 * Ensures required sheets exist with cached check to eliminate 1500ms overhead on every poll.
 */
function ensureInitialized(force) {
  var cache = CacheService.getScriptCache();
  if (!force && cache.get('rps_init_checked') === 'true') {
    return;
  }
  initSpreadsheet();
  try {
    cache.put('rps_init_checked', 'true', 21600); // 6 hours
  } catch (_e) {}
}

/**
 * Initializes the spreadsheet by creating missing tabs and inserting bold header rows.
 */
function initSpreadsheet() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  for (var key in HEADERS) {
    var sheetName = key;
    var sheet = ss.getSheetByName(sheetName);
    if (!sheet) {
      sheet = ss.insertSheet(sheetName);
    }
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(HEADERS[key]);
      sheet.getRange(1, 1, 1, HEADERS[key].length).setFontWeight('bold');
      sheet.setFrozenRows(1);
    }
  }
  try {
    CacheService.getScriptCache().put('rps_init_checked', 'true', 21600);
  } catch (_e) {}
  return { success: true, message: 'Spreadsheet initialized successfully with all 6 tabs.' };
}

/**
 * HTTP GET endpoint for fast status checks and state queries.
 */
function doGet(e) {
  var params = (e && e.parameter) ? e.parameter : {};
  var action = params.action || 'health';

  try {
    ensureInitialized(action === 'init');
    var result;

    switch (action) {
      case 'health':
        result = { success: true, service: 'RPS Arena Fast API', timestamp: new Date().toISOString() };
        break;
      case 'init':
        result = initSpreadsheet();
        break;
      case 'get_chat':
        result = getChatMessages(params.sessionId, params.since);
        break;
      case 'get_session_state':
        result = getSessionState(params.sessionId, params.userId, params.v || params.version);
        break;
      default:
        result = { success: false, error: 'Unknown GET action: ' + action };
    }

    return ContentService.createTextOutput(JSON.stringify(result))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ success: false, error: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

/**
 * HTTP POST endpoint for mutations: Auth, Session Management, Moves, and Chat.
 */
function doPost(e) {
  try {
    var body = {};
    if (e && e.postData && e.postData.contents) {
      body = JSON.parse(e.postData.contents);
    } else if (e && e.parameter) {
      body = e.parameter;
    }

    var action = body.action || '';
    ensureInitialized(action === 'init');
    var result;

    switch (action) {
      case 'init':
        result = initSpreadsheet();
        break;
      case 'login':
        result = handleLogin(body.displayName, body.role, body.passwordHash);
        break;
      case 'get_or_create_session':
        result = handleGetOrCreateSession(body.userId, body.role);
        break;
      case 'join_session':
        result = handleJoinSession(body.sessionId, body.userId, body.role);
        break;
      case 'send_chat':
        result = handleSendChat(body.sessionId, body.userId, body.senderRole, body.message);
        break;
      case 'submit_move':
        result = handleSubmitMove(body.sessionId, body.userId, body.roundNumber, body.move);
        break;
      case 'ready_next_round':
        result = handleReadyNextRound(body.sessionId, body.userId);
        break;
      case 'get_session_state':
        result = getSessionState(body.sessionId, body.userId, body.version);
        break;
      default:
        result = { success: false, error: 'Unknown action: ' + action };
    }

    return ContentService.createTextOutput(JSON.stringify(result))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ success: false, error: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

// -------------------------------------------------------------
// CORE BUSINESS LOGIC & OPTIMIZED SPREADSHEET CRUD
// -------------------------------------------------------------

function handleLogin(displayName, role, passwordHash) {
  displayName = (displayName || '').trim();
  role = (role || '').trim();
  passwordHash = (passwordHash || '').trim();

  if (!displayName) return { success: false, error: 'Display name is required.' };
  if (!role || (role !== 'Husband' && role !== 'Wife')) {
    return { success: false, error: 'Role must be either Husband or Wife.' };
  }
  if (!passwordHash) return { success: false, error: 'PIN/Password hash is required.' };

  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEETS.USERS);
  var data = sheet.getDataRange().getValues();

  var foundRow = -1;
  var existingUser = null;

  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    if (row[1].toString().toLowerCase() === displayName.toLowerCase() && row[2].toString().toLowerCase() === role.toLowerCase()) {
      foundRow = i + 1;
      existingUser = {
        userId: row[0],
        displayName: row[1],
        role: row[2],
        passwordHash: row[3],
        createdAt: row[4],
        status: row[6]
      };
      break;
    }
  }

  var now = new Date().toISOString();

  if (existingUser) {
    if (existingUser.passwordHash !== passwordHash) {
      return { success: false, error: 'Incorrect PIN or password for ' + role + ' (' + displayName + ').' };
    }
    sheet.getRange(foundRow, 6).setValue(now);
    cacheUserDisplayName(existingUser.userId, existingUser.displayName);
    return {
      success: true,
      data: {
        userId: existingUser.userId,
        displayName: existingUser.displayName,
        role: existingUser.role,
        sessionToken: Utilities.getUuid()
      }
    };
  } else {
    var newUserId = 'usr_' + role.toLowerCase() + '_' + Utilities.getUuid().substring(0, 8);
    sheet.appendRow([newUserId, displayName, role, passwordHash, now, now, 'ACTIVE']);
    cacheUserDisplayName(newUserId, displayName);
    return {
      success: true,
      data: {
        userId: newUserId,
        displayName: displayName,
        role: role,
        sessionToken: Utilities.getUuid()
      }
    };
  }
}

function handleGetOrCreateSession(userId, role) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEETS.SESSIONS);
  var data = sheet.getDataRange().getValues();
  var now = new Date().toISOString();

  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    var status = row[3];
    if (status === 'WAITING' || status === 'ACTIVE') {
      var sessionId = row[0];
      var husbandId = row[1];
      var wifeId = row[2];

      if (role === 'Husband' && !husbandId) {
        sheet.getRange(i + 1, 2).setValue(userId);
        husbandId = userId;
      }
      if (role === 'Wife' && !wifeId) {
        sheet.getRange(i + 1, 3).setValue(userId);
        wifeId = userId;
      }

      if (husbandId && wifeId && status === 'WAITING') {
        sheet.getRange(i + 1, 4).setValue('ACTIVE');
        status = 'ACTIVE';
      }

      sheet.getRange(i + 1, 6).setValue(now);
      return {
        success: true,
        data: getSessionState(sessionId, userId, null).data
      };
    }
  }

  var newSessionId = 'COUPLE-' + Math.floor(1000 + Math.random() * 9000);
  var husbandCol = role === 'Husband' ? userId : '';
  var wifeCol = role === 'Wife' ? userId : '';
  var initialStatus = (husbandCol && wifeCol) ? 'ACTIVE' : 'WAITING';

  sheet.appendRow([newSessionId, husbandCol, wifeCol, initialStatus, now, now]);

  logEvent(newSessionId, userId, 'SESSION_CREATED', 'Role: ' + role);
  addChatMessage(newSessionId, 'SYSTEM', 'Arena Arbiter', 'Welcome to the Couple Arena! Session ' + newSessionId + ' started.');

  return {
    success: true,
    data: getSessionState(newSessionId, userId, null).data
  };
}

function handleJoinSession(sessionId, userId, role) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEETS.SESSIONS);
  var data = sheet.getDataRange().getValues();

  var foundRow = -1;
  var targetSessionId = (sessionId || '').trim().toUpperCase();

  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    if (row[0].toString().toUpperCase() === targetSessionId || (!targetSessionId && (row[3] === 'WAITING' || row[3] === 'ACTIVE'))) {
      foundRow = i + 1;
      targetSessionId = row[0];
      var husbandId = row[1];
      var wifeId = row[2];

      if (role === 'Wife') {
        sheet.getRange(foundRow, 3).setValue(userId);
        wifeId = userId;
      } else if (role === 'Husband') {
        sheet.getRange(foundRow, 2).setValue(userId);
        husbandId = userId;
      }

      var newStatus = (husbandId && wifeId) ? 'ACTIVE' : 'WAITING';
      sheet.getRange(foundRow, 4).setValue(newStatus);
      sheet.getRange(foundRow, 6).setValue(new Date().toISOString());

      logEvent(targetSessionId, userId, 'SESSION_JOINED', 'Role: ' + role);
      addChatMessage(targetSessionId, 'SYSTEM', 'Arena Arbiter', role + ' joined the session! Arena is ready.');

      return {
        success: true,
        data: getSessionState(targetSessionId, userId, null).data
      };
    }
  }

  return { success: false, error: 'No active session found with code ' + targetSessionId };
}

/**
 * Optimized session state resolution with Versioning (ETag) to skip re-reading unchanged data.
 */
function getSessionState(sessionId, currentUserId, clientVersion) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sessionSheet = ss.getSheetByName(SHEETS.SESSIONS);
  var sessionData = sessionSheet.getDataRange().getValues();

  var sessionRow = null;
  for (var i = 1; i < sessionData.length; i++) {
    if (sessionData[i][0].toString().toUpperCase() === sessionId.toString().toUpperCase()) {
      sessionRow = sessionData[i];
      break;
    }
  }

  if (!sessionRow) {
    return { success: false, error: 'Session ' + sessionId + ' not found.' };
  }

  var husbandId = sessionRow[1];
  var wifeId = sessionRow[2];
  var sessionStatus = sessionRow[3];

  // Batch query results and moves
  var resultsSheet = ss.getSheetByName(SHEETS.GAME_RESULTS);
  var resultsData = resultsSheet.getDataRange().getValues();

  var husbandScore = 0;
  var wifeScore = 0;
  var draws = 0;
  var maxRound = 1;
  var lastResult = null;
  var totalResultsForSession = 0;

  for (var r = 1; r < resultsData.length; r++) {
    var resRow = resultsData[r];
    if (resRow[1] === sessionId) {
      totalResultsForSession++;
      var roundNum = parseInt(resRow[2], 10) || 1;
      if (roundNum >= maxRound) {
        maxRound = roundNum + 1;
      }
      var winnerId = resRow[3];
      if (winnerId === husbandId) husbandScore++;
      else if (winnerId === wifeId) wifeScore++;
      else if (winnerId === 'DRAW') draws++;
      lastResult = {
        roundNumber: roundNum,
        winnerId: winnerId,
        result: resRow[4]
      };
    }
  }

  var movesSheet = ss.getSheetByName(SHEETS.GAME_MOVES);
  var movesData = movesSheet.getDataRange().getValues();

  var husbandMove = null;
  var wifeMove = null;
  var totalMovesInRound = 0;

  for (var m = 1; m < movesData.length; m++) {
    var mRow = movesData[m];
    if (mRow[1] === sessionId && parseInt(mRow[2], 10) === maxRound) {
      if (mRow[3] === husbandId) {
        husbandMove = mRow[4];
        totalMovesInRound++;
      }
      if (mRow[3] === wifeId) {
        wifeMove = mRow[4];
        totalMovesInRound++;
      }
    }
  }

  var chatSheet = ss.getSheetByName(SHEETS.CHAT);
  var chatData = chatSheet.getDataRange().getValues();
  var totalChatForSession = 0;
  var chatList = [];

  for (var c = 1; c < chatData.length; c++) {
    var cRow = chatData[c];
    if (cRow[1].toString().toUpperCase() === sessionId.toString().toUpperCase()) {
      totalChatForSession++;
      chatList.push({
        id: cRow[0],
        sessionId: cRow[1],
        senderId: cRow[2],
        senderName: cRow[3],
        text: cRow[4],
        timestamp: cRow[5],
        isSystem: cRow[2] === 'SYSTEM'
      });
    }
  }

  // Generate lightweight version string: r{round}_m{movesCount}_res{resultsCount}_c{chatCount}_s{sessionStatus}
  var currentVersion = 'r' + maxRound + '_m' + totalMovesInRound + '_res' + totalResultsForSession + '_c' + totalChatForSession + '_' + sessionStatus;

  // FAST PATH: Client sent exact matching version -> return immediate lightweight response!
  if (clientVersion && clientVersion === currentVersion) {
    return {
      success: true,
      unchanged: true,
      version: currentVersion
    };
  }

  var husbandName = getUserDisplayName(husbandId) || 'Husband';
  var wifeName = getUserDisplayName(wifeId) || 'Wife';

  var bothSubmitted = (husbandMove !== null && wifeMove !== null);
  var roundStatus = bothSubmitted ? 'ROUND_REVEAL' : (sessionStatus === 'ACTIVE' ? 'IN_PROGRESS' : 'WAITING_FOR_OPPONENT');

  return {
    success: true,
    unchanged: false,
    version: currentVersion,
    data: {
      sessionId: sessionId,
      roundNumber: maxRound,
      draws: draws,
      status: roundStatus,
      lastWinner: lastResult ? (lastResult.winnerId === husbandId ? 'PLAYER_ONE' : lastResult.winnerId === wifeId ? 'PLAYER_TWO' : 'DRAW') : 'UNDECIDED',
      husband: {
        id: husbandId,
        username: husbandName,
        score: husbandScore,
        hasSubmitted: husbandMove !== null,
        revealedChoice: bothSubmitted ? husbandMove : 'NONE',
        isConnected: Boolean(husbandId)
      },
      wife: {
        id: wifeId,
        username: wifeName,
        score: wifeScore,
        hasSubmitted: wifeMove !== null,
        revealedChoice: bothSubmitted ? wifeMove : 'NONE',
        isConnected: Boolean(wifeId)
      },
      chatMessages: chatList
    }
  };
}

/**
 * Thread-safe move submission using LockService to prevent race conditions during rapid play.
 */
function handleSubmitMove(sessionId, userId, roundNumber, move) {
  move = (move || '').trim().toUpperCase();
  if (['ROCK', 'PAPER', 'SCISSORS'].indexOf(move) === -1) {
    return { success: false, error: 'Invalid move: ' + move };
  }

  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(8000); // Wait up to 8s for thread-safe lock
  } catch (_e) {
    return { success: false, error: 'Server busy. Retrying move...' };
  }

  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sessionSheet = ss.getSheetByName(SHEETS.SESSIONS);
    var sessionData = sessionSheet.getDataRange().getValues();

    var sessionRow = null;
    for (var i = 1; i < sessionData.length; i++) {
      if (sessionData[i][0] === sessionId) {
        sessionRow = sessionData[i];
        break;
      }
    }
    if (!sessionRow) return { success: false, error: 'Session not found' };

    var husbandId = sessionRow[1];
    var wifeId = sessionRow[2];

    if (userId !== husbandId && userId !== wifeId) {
      return { success: false, error: 'Unauthorized user for this session' };
    }

    var movesSheet = ss.getSheetByName(SHEETS.GAME_MOVES);
    var movesData = movesSheet.getDataRange().getValues();

    for (var m = 1; m < movesData.length; m++) {
      var row = movesData[m];
      if (row[1] === sessionId && parseInt(row[2], 10) === parseInt(roundNumber, 10) && row[3] === userId) {
        return { success: true, data: getSessionState(sessionId, userId, null).data };
      }
    }

    var now = new Date().toISOString();
    var moveId = 'mov_' + Utilities.getUuid().substring(0, 8);
    movesSheet.appendRow([moveId, sessionId, parseInt(roundNumber, 10), userId, move, now]);

    // Check opponent move
    var otherId = (userId === husbandId) ? wifeId : husbandId;
    var otherMove = null;

    var updatedMoves = movesSheet.getDataRange().getValues();
    for (var k = 1; k < updatedMoves.length; k++) {
      var uRow = updatedMoves[k];
      if (uRow[1] === sessionId && parseInt(uRow[2], 10) === parseInt(roundNumber, 10) && uRow[3] === otherId) {
        otherMove = uRow[4];
        break;
      }
    }

    if (otherMove) {
      var husbandChoice = (userId === husbandId) ? move : otherMove;
      var wifeChoice = (userId === wifeId) ? move : otherMove;

      var winnerResult = evaluateRps(husbandChoice, wifeChoice);
      var winnerId = 'DRAW';
      if (winnerResult === 'HUSBAND') winnerId = husbandId;
      if (winnerResult === 'WIFE') winnerId = wifeId;

      var resultId = 'res_' + Utilities.getUuid().substring(0, 8);
      var resultsSheet = ss.getSheetByName(SHEETS.GAME_RESULTS);
      resultsSheet.appendRow([resultId, sessionId, parseInt(roundNumber, 10), winnerId, winnerResult, now]);

      var hName = getUserDisplayName(husbandId) || 'Husband';
      var wName = getUserDisplayName(wifeId) || 'Wife';
      var outcomeMsg = (winnerId === 'DRAW')
        ? 'Round ' + roundNumber + ' DRAW! Both picked ' + husbandChoice + '.'
        : (winnerId === husbandId ? hName : wName) + ' won Round ' + roundNumber + '! (' + (winnerId === husbandId ? husbandChoice : wifeChoice) + ' beats ' + (winnerId === husbandId ? wifeChoice : husbandChoice) + ')';

      addChatMessage(sessionId, 'SYSTEM', 'Arena Arbiter', outcomeMsg);
    }

    return {
      success: true,
      data: getSessionState(sessionId, userId, null).data
    };
  } finally {
    lock.releaseLock();
  }
}

function handleReadyNextRound(sessionId, userId) {
  var currentState = getSessionState(sessionId, userId, null);
  return {
    success: true,
    data: currentState.data
  };
}

function handleSendChat(sessionId, senderId, senderRole, text) {
  var trimmed = (text || '').trim();
  if (!trimmed) return { success: false, error: 'Message cannot be empty' };
  if (trimmed.length > 500) return { success: false, error: 'Message exceeds 500 characters' };

  var senderName = getUserDisplayName(senderId) || senderRole || 'Spouse';
  addChatMessage(sessionId, senderId, senderName, trimmed);

  return {
    success: true,
    data: getChatMessages(sessionId).data
  };
}

function addChatMessage(sessionId, senderId, senderName, messageText) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEETS.CHAT);
  var msgId = 'msg_' + Utilities.getUuid().substring(0, 8);
  var now = new Date().getTime();
  sheet.appendRow([msgId, sessionId, senderId, senderName, messageText, now]);
}

function getChatMessages(sessionId, sinceTimestamp) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEETS.CHAT);
  var data = sheet.getDataRange().getValues();

  var since = sinceTimestamp ? parseInt(sinceTimestamp, 10) : 0;
  var messages = [];

  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    if (row[1].toString().toUpperCase() === sessionId.toString().toUpperCase()) {
      var ts = parseInt(row[5], 10) || 0;
      if (ts >= since) {
        messages.push({
          id: row[0],
          sessionId: row[1],
          senderId: row[2],
          senderName: row[3],
          text: row[4],
          timestamp: ts,
          isSystem: row[2] === 'SYSTEM'
        });
      }
    }
  }
  return { success: true, data: messages };
}

function cacheUserDisplayName(userId, displayName) {
  try {
    CacheService.getScriptCache().put('uname_' + userId, displayName, 21600);
  } catch (_e) {}
}

function getUserDisplayName(userId) {
  if (!userId) return '';
  try {
    var cached = CacheService.getScriptCache().get('uname_' + userId);
    if (cached) return cached;
  } catch (_e) {}

  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEETS.USERS);
  var data = sheet.getDataRange().getValues();
  for (var i = 1; i < data.length; i++) {
    if (data[i][0] === userId) {
      var name = data[i][1];
      cacheUserDisplayName(userId, name);
      return name;
    }
  }
  return '';
}

function logEvent(sessionId, userId, eventType, eventData) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName(SHEETS.EVENTS);
    sheet.appendRow(['evt_' + Utilities.getUuid().substring(0, 6), sessionId, userId, eventType, eventData, new Date().toISOString()]);
  } catch (_e) {}
}

function evaluateRps(p1, p2) {
  if (p1 === p2) return 'DRAW';
  if (
    (p1 === 'ROCK' && p2 === 'SCISSORS') ||
    (p1 === 'SCISSORS' && p2 === 'PAPER') ||
    (p1 === 'PAPER' && p2 === 'ROCK')
  ) {
    return 'HUSBAND';
  }
  return 'WIFE';
}
