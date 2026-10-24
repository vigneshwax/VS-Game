import React, { useState, useEffect, useRef } from 'react';
import { Room, GameChoice, ChatMessage } from './types.ts';
import {
  Swords,
  Copy,
  Check,
  Send,
  Wifi,
  WifiOff,
  HelpCircle,
  Play,
  RotateCcw,
  Sparkles,
  Users
} from 'lucide-react';
import { FloatingChat } from './FloatingChat.tsx';


// Configurable backend URL with automatic fallback
const BACKEND_URL =
  import.meta.env.VITE_SOCKET_URL ||
  (typeof window !== 'undefined'
    ? `${window.location.protocol}//${window.location.hostname}:3001`
    : 'http://localhost:3001');

export function App() {
  const [username, setUsername] = useState(`Warrior_${Math.floor(100 + Math.random() * 900)}`);
  const [roomCodeInput, setRoomCodeInput] = useState('');
  const [serverUrl, setServerUrl] = useState(BACKEND_URL);
  const [room, setRoom] = useState<Room | null>(null);
  const [playerId] = useState(`player_${Math.random().toString(36).substring(2, 9)}`);
  const [selectedChoice, setSelectedChoice] = useState<GameChoice | null>(null);
  const [chatInput, setChatInput] = useState('');
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusText, setStatusText] = useState('Ready to duel');
  const [showRules, setShowRules] = useState(false);
  const [isConnected, setIsConnected] = useState(true);
  const [unreadCount, setUnreadCount] = useState(0);
  const [lastSeenMsgCount, setLastSeenMsgCount] = useState(0);
  const [localMessages, setLocalMessages] = useState<ChatMessage[]>(() => {
    try {
      const saved = localStorage.getItem('rps_arena_chat_history');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const chatEndRef = useRef<HTMLDivElement>(null);

  // Active messages based on whether user is in a room
  const activeMessages = room ? room.chatMessages : localMessages;

  useEffect(() => {
    try {
      localStorage.setItem('rps_arena_chat_history', JSON.stringify(localMessages));
    } catch {}
  }, [localMessages]);

  useEffect(() => {
    const total = activeMessages.length;
    if (total > lastSeenMsgCount) {
      setUnreadCount((prev) => prev + (total - lastSeenMsgCount));
      setLastSeenMsgCount(total);
    }
  }, [activeMessages.length]);

  const handleSendChat = async (text: string) => {
    if (room) {
      try {
        await fetch(`${serverUrl}/api/chat/send`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ roomId: room.roomId, playerId, text })
        });
        const res = await fetch(`${serverUrl}/api/rooms/get?roomId=${room.roomId}`);
        if (res.ok) setRoom(await res.json());
      } catch (err: any) {
        setError(err.message);
      }
    } else {
      const newMsg: ChatMessage = {
        id: `local_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
        senderId: playerId,
        senderName: username,
        text,
        timestamp: Date.now(),
        isSystem: false
      };
      setLocalMessages((prev) => [...prev, newMsg]);
    }
  };

  // Poll room updates periodically if inside a room
  useEffect(() => {
    if (!room) return;
    const interval = setInterval(async () => {
      try {
        const res = await fetch(`${serverUrl}/api/rooms/get?roomId=${room.roomId}`).catch(() => null);
        if (res && res.ok) {
          const updated = await res.json();
          setRoom(updated);
          setIsConnected(true);
        }
      } catch {
        setIsConnected(false);
      }
    }, 1500);
    return () => clearInterval(interval);
  }, [room, serverUrl]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [room?.chatMessages]);

  const handleCreateRoom = async () => {
    if (!username.trim()) {
      setError('Please enter a username');
      return;
    }
    setError(null);
    setStatusText('Creating room...');
    try {
      const res = await fetch(`${serverUrl}/api/rooms/create`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username.trim(), playerId })
      });
      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.error || 'Failed to create room');
      }
      const data = await res.json();
      setRoom(data.room);
      setStatusText(`Room ${data.room.roomId} created. Waiting for opponent...`);
    } catch (err: any) {
      setError(`Backend error: ${err.message}. Ensure backend is running on ${serverUrl}`);
    }
  };

  const handleJoinRoom = async () => {
    const cleanCode = roomCodeInput.trim().toUpperCase();
    if (!username.trim()) {
      setError('Please enter a username');
      return;
    }
    if (!cleanCode) {
      setError('Please enter a room code');
      return;
    }
    setError(null);
    setStatusText(`Connecting to room ${cleanCode}...`);
    try {
      const res = await fetch(`${serverUrl}/api/rooms/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ roomId: cleanCode, username: username.trim(), playerId })
      });
      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.error || 'Failed to join room');
      }
      const data = await res.json();
      setRoom(data.room);
      setStatusText(`Connected to ${cleanCode}! Battle in progress.`);
    } catch (err: any) {
      setError(err.message || 'Failed to connect to host');
    }
  };

  const handleSubmitChoice = async () => {
    if (!room || !selectedChoice) return;
    try {
      const res = await fetch(`${serverUrl}/api/game/move`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ roomId: room.roomId, playerId, choice: selectedChoice })
      });
      if (res.ok) {
        const data = await res.json();
        setRoom(data.room);
      }
    } catch (err: any) {
      setError(err.message);
    }
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!room || !chatInput.trim()) return;
    const text = chatInput.trim();
    setChatInput('');
    try {
      await fetch(`${serverUrl}/api/chat/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ roomId: room.roomId, playerId, text })
      });
      const res = await fetch(`${serverUrl}/api/rooms/get?roomId=${room.roomId}`);
      if (res.ok) setRoom(await res.json());
    } catch (err: any) {
      setError(err.message);
    }
  };

  const copyRoomCode = () => {
    if (!room) return;
    navigator.clipboard.writeText(room.roomId);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const isCurrentP1 = room?.player1?.id === playerId;
  const currentPlayer = isCurrentP1 ? room?.player1 : room?.player2;
  const opponent = isCurrentP1 ? room?.player2 : room?.player1;
  const isRevealed = room?.status === 'ROUND_REVEAL';

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '24px 16px' }}>
      {/* Top Bar */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ background: '#4f46e5', padding: '10px', borderRadius: '14px', display: 'flex' }}>
            <Swords size={28} color="#fff" />
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: '24px', fontWeight: 800, letterSpacing: '0.5px' }}>RPS Arena</h1>
            <p style={{ margin: 0, fontSize: '13px', color: '#06b6d4', fontWeight: 600 }}>Play. Chat. Compete.</p>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', background: '#1e2843', padding: '6px 12px', borderRadius: '10px', fontSize: '12px' }}>
            {isConnected ? <Wifi size={14} color="#10b981" /> : <WifiOff size={14} color="#f43f5e" />}
            <span style={{ color: isConnected ? '#10b981' : '#f43f5e' }}>{isConnected ? 'Online' : 'Offline'}</span>
          </div>
          <button
            onClick={() => setShowRules(true)}
            style={{ background: '#1e2843', border: '1px solid #334155', color: '#94a3b8', padding: '8px 12px', borderRadius: '10px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px' }}
          >
            <HelpCircle size={16} /> Rules
          </button>
        </div>
      </header>

      {error && (
        <div style={{ background: '#451a24', border: '1px solid #f43f5e', color: '#fecdd3', padding: '12px 16px', borderRadius: '12px', marginBottom: '20px', display: 'flex', justifyContent: 'space-between' }}>
          <span>{error}</span>
          <button onClick={() => setError(null)} style={{ background: 'none', border: 'none', color: '#fecdd3', cursor: 'pointer', fontWeight: 'bold' }}>✕</button>
        </div>
      )}

      {!room ? (
        /* Landing / Games Hub Home View */
        <div style={{ maxWidth: '800px', margin: '20px auto', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Hero Banner */}
          <div style={{ background: '#131b2e', border: '1px solid #2e3d66', borderRadius: '24px', padding: '24px', textAlign: 'center' }}>
            <span style={{ background: '#312e81', color: '#06b6d4', padding: '4px 12px', borderRadius: '12px', fontSize: '11px', fontWeight: 700 }}>2-PLAYER ARCADE HUB</span>
            <h2 style={{ fontSize: '28px', fontWeight: 900, margin: '10px 0 6px 0', color: '#f8fafc' }}>Couple Games Arena</h2>
            <p style={{ margin: 0, fontSize: '14px', color: '#94a3b8' }}>Play together, duel secretly, and decide chores with fun multiplayer games!</p>
          </div>

          {/* Featured Game: RPS Arena */}
          <div style={{ background: '#131b2e', border: '2px solid #6366f1', borderRadius: '24px', padding: '28px', boxShadow: '0 10px 30px rgba(99, 102, 241, 0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <span style={{ background: '#4f46e5', color: '#fff', padding: '3px 10px', borderRadius: '8px', fontSize: '11px', fontWeight: 800 }}>⭐ FLAGSHIP GAME</span>
              <span style={{ fontSize: '12px', color: '#06b6d4', fontWeight: 700 }}>ONLINE MULTIPLAYER</span>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '16px' }}>
              <div style={{ fontSize: '32px', background: '#1e2843', padding: '10px 14px', borderRadius: '16px' }}>
                🪨 📄 ✂️
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: '20px', fontWeight: 800 }}>Rock Paper Scissors Arena</h3>
                <p style={{ margin: '2px 0 0 0', fontSize: '13px', color: '#94a3b8' }}>Custom hand gestures, hidden simultaneous reveals & live floating chat.</p>
              </div>
            </div>

            <label style={{ display: 'block', fontSize: '12px', color: '#94a3b8', marginBottom: '6px', fontWeight: 600 }}>PLAYER NAME</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Enter your name"
              style={{ width: '100%', padding: '12px 16px', background: '#1e2843', border: '1px solid #334155', borderRadius: '12px', color: '#f8fafc', marginBottom: '16px', fontSize: '15px' }}
            />

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '16px' }}>
              <button
                onClick={handleCreateRoom}
                style={{ padding: '14px', background: '#6366f1', color: '#fff', border: 'none', borderRadius: '14px', fontSize: '15px', fontWeight: 700, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
              >
                <Sparkles size={18} /> Host New Match
              </button>

              <div style={{ display: 'flex', gap: '8px' }}>
                <input
                  type="text"
                  value={roomCodeInput}
                  onChange={(e) => setRoomCodeInput(e.target.value.toUpperCase())}
                  placeholder="ROOM CODE"
                  style={{ flex: 1, padding: '12px', background: '#1e2843', border: '1px solid #334155', borderRadius: '12px', color: '#f8fafc', fontSize: '14px', textTransform: 'uppercase' }}
                />
                <button
                  onClick={handleJoinRoom}
                  style={{ padding: '0 20px', background: '#06b6d4', color: '#090d16', border: 'none', borderRadius: '12px', fontSize: '14px', fontWeight: 700, cursor: 'pointer' }}
                >
                  Join
                </button>
              </div>
            </div>

            <details style={{ fontSize: '12px', color: '#64748b' }}>
              <summary style={{ cursor: 'pointer' }}>Custom Backend Host Configuration</summary>
              <input
                type="text"
                value={serverUrl}
                onChange={(e) => setServerUrl(e.target.value)}
                placeholder="http://localhost:3001"
                style={{ width: '100%', padding: '8px 12px', background: '#0f172a', border: '1px solid #334155', borderRadius: '8px', color: '#94a3b8', marginTop: '8px' }}
              />
            </details>
          </div>

          {/* Quick Play Mini-Games in Hub */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
            <div style={{ background: '#131b2e', border: '1px solid #2e3d66', borderRadius: '20px', padding: '18px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
                <span style={{ fontSize: '24px' }}>❌⭕</span>
                <div>
                  <h4 style={{ margin: 0, fontSize: '15px', fontWeight: 700 }}>XO Clash (Tic Tac Toe)</h4>
                  <span style={{ fontSize: '11px', color: '#10b981', fontWeight: 600 }}>AVAILABLE IN ANDROID APP</span>
                </div>
              </div>
              <p style={{ margin: 0, fontSize: '12px', color: '#94a3b8' }}>Classic turn-based 3x3 board duel for couples.</p>
            </div>

            <div style={{ background: '#131b2e', border: '1px solid #2e3d66', borderRadius: '20px', padding: '18px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
                <span style={{ fontSize: '24px' }}>🪙</span>
                <div>
                  <h4 style={{ margin: 0, fontSize: '15px', fontWeight: 700 }}>Decision Coin Flip</h4>
                  <span style={{ fontSize: '11px', color: '#f59e0b', fontWeight: 600 }}>CHORE RESOLVER</span>
                </div>
              </div>
              <p style={{ margin: 0, fontSize: '12px', color: '#94a3b8' }}>Can't decide who does dishes or picks dinner? Flip the coin!</p>
            </div>
          </div>
        </div>
      ) : room.status === 'WAITING_FOR_OPPONENT' ? (

        /* Lobby View */
        <div style={{ maxWidth: '520px', margin: '40px auto', background: '#131b2e', border: '1px solid #2e3d66', borderRadius: '24px', padding: '32px', textAlign: 'center' }}>
          <p style={{ color: '#06b6d4', fontSize: '13px', fontWeight: 700, letterSpacing: '1px', margin: '0 0 8px 0' }}>ROOM CREATED</p>
          <h2 style={{ fontSize: '40px', fontWeight: 900, letterSpacing: '4px', margin: '0 0 20px 0', color: '#06b6d4' }}>{room.roomId}</h2>

          <button
            onClick={copyRoomCode}
            style={{ padding: '10px 20px', background: copied ? '#10b981' : '#1e2843', color: '#f8fafc', border: '1px solid #334155', borderRadius: '12px', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '8px', fontWeight: 600, marginBottom: '24px' }}
          >
            {copied ? <Check size={16} /> : <Copy size={16} />} {copied ? 'Copied!' : 'Copy Room Code'}
          </button>

          <div style={{ background: '#1e2843', padding: '16px', borderRadius: '16px', marginBottom: '20px' }}>
            <p style={{ margin: '0 0 8px 0', fontSize: '13px', color: '#94a3b8' }}>Waiting for opponent to connect...</p>
            <div style={{ display: 'flex', justifyContent: 'space-around', alignItems: 'center' }}>
              <div>
                <p style={{ margin: 0, fontWeight: 700 }}>{room.player1.username}</p>
                <span style={{ fontSize: '11px', color: '#10b981' }}>Host (Ready)</span>
              </div>
              <span style={{ color: '#64748b' }}>VS</span>
              <div>
                <p style={{ margin: 0, fontWeight: 700, color: '#64748b' }}>Challenger</p>
                <span style={{ fontSize: '11px', color: '#64748b' }}>Waiting...</span>
              </div>
            </div>
          </div>

          <button onClick={() => setRoom(null)} style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer', textDecoration: 'underline' }}>
            Cancel and Return
          </button>
        </div>
      ) : (
        /* Battle Arena View (Full Screen Stage, Live Chat moved to Floating Bubble) */
        <div style={{ maxWidth: '680px', margin: '0 auto', background: '#131b2e', border: '1px solid #2e3d66', borderRadius: '24px', padding: '24px' }}>
          {/* Scoreboard */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#1e2843', padding: '14px 20px', borderRadius: '16px', marginBottom: '24px' }}>
            <div>
              <span style={{ fontSize: '11px', color: '#6366f1', fontWeight: 700 }}>{room.player1.username} {isCurrentP1 && '(YOU)'}</span>
              <p style={{ margin: 0, fontSize: '24px', fontWeight: 900 }}>{room.player1.score}</p>
            </div>

            <div style={{ textAlign: 'center' }}>
              <span style={{ background: '#312e81', color: '#06b6d4', padding: '4px 10px', borderRadius: '8px', fontSize: '12px', fontWeight: 700 }}>ROUND {room.roundNumber}</span>
              <p style={{ margin: '4px 0 0 0', fontSize: '11px', color: '#64748b' }}>Draws: {room.draws}</p>
            </div>

            <div style={{ textAlign: 'right' }}>
              <span style={{ fontSize: '11px', color: '#8b5cf6', fontWeight: 700 }}>{room.player2?.username || 'Opponent'} {!isCurrentP1 && '(YOU)'}</span>
              <p style={{ margin: 0, fontSize: '24px', fontWeight: 900 }}>{room.player2?.score || 0}</p>
            </div>
          </div>

          {/* Battle Stage */}
          {isRevealed ? (
            <div style={{ textAlign: 'center', padding: '30px 0' }}>
              <h3 style={{ fontSize: '32px', fontWeight: 900, color: room.lastWinner === 'DRAW' ? '#f59e0b' : '#10b981', margin: '0 0 8px 0' }}>
                {room.lastWinner === 'DRAW' ? "IT'S A DRAW!" : 'ROUND REVEAL'}
              </h3>
              <p style={{ color: '#94a3b8', margin: '0 0 24px 0' }}>Both weapons revealed!</p>

              <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '32px', marginBottom: '32px' }}>
                <div>
                  <span style={{ fontSize: '48px' }}>
                    {room.player1.revealedChoice === 'ROCK' ? '🪨' : room.player1.revealedChoice === 'PAPER' ? '📄' : '✂️'}
                  </span>
                  <p style={{ fontWeight: 700, margin: '8px 0 0 0' }}>{room.player1.username}</p>
                </div>
                <span style={{ fontSize: '20px', fontWeight: 900, color: '#f59e0b' }}>VS</span>
                <div>
                  <span style={{ fontSize: '48px' }}>
                    {room.player2?.revealedChoice === 'ROCK' ? '🪨' : room.player2?.revealedChoice === 'PAPER' ? '📄' : '✂️'}
                  </span>
                  <p style={{ fontWeight: 700, margin: '8px 0 0 0' }}>{room.player2?.username}</p>
                </div>
              </div>
            </div>
          ) : (
            <div>
              <h3 style={{ fontSize: '18px', fontWeight: 700, margin: '0 0 16px 0', textAlign: 'center' }}>
                {currentPlayer?.hasSubmitted ? 'Move Locked! Waiting for opponent...' : 'Select Your Weapon'}
              </h3>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px', marginBottom: '20px' }}>
                {(['ROCK', 'PAPER', 'SCISSORS'] as GameChoice[]).map((choice) => (
                  <button
                    key={choice}
                    disabled={currentPlayer?.hasSubmitted}
                    onClick={() => setSelectedChoice(choice)}
                    style={{
                      padding: '24px 12px',
                      background: selectedChoice === choice ? '#1e2942' : '#0c101c',
                      border: `2px solid ${selectedChoice === choice ? '#6366f1' : '#1e2843'}`,
                      borderRadius: '16px',
                      cursor: currentPlayer?.hasSubmitted ? 'not-allowed' : 'pointer',
                      textAlign: 'center'
                    }}
                  >
                    <span style={{ fontSize: '36px', display: 'block', marginBottom: '8px' }}>
                      {choice === 'ROCK' ? '🪨' : choice === 'PAPER' ? '📄' : '✂️'}
                    </span>
                    <strong style={{ color: '#f8fafc', fontSize: '13px' }}>{choice}</strong>
                  </button>
                ))}
              </div>

              <button
                disabled={!selectedChoice || currentPlayer?.hasSubmitted}
                onClick={handleSubmitChoice}
                style={{
                  width: '100%',
                  padding: '14px',
                  background: currentPlayer?.hasSubmitted ? '#1e2843' : '#6366f1',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '14px',
                  fontWeight: 700,
                  cursor: (!selectedChoice || currentPlayer?.hasSubmitted) ? 'not-allowed' : 'pointer'
                }}
              >
                {currentPlayer?.hasSubmitted ? 'Move Locked (Hidden)' : `Lock in ${selectedChoice || 'Move'}`}
              </button>
            </div>
          )}
        </div>
      )}

      {/* Floating Chat Bubble & Popup Window */}
      <FloatingChat
        messages={activeMessages}
        currentUserId={playerId}
        onSendMessage={handleSendChat}
        title={room ? `Room ${room.roomId} Chat` : 'Arena Live Chat'}
        isOnline={isConnected}
        unreadCount={unreadCount}
        onClearUnread={() => setUnreadCount(0)}
      />

      {/* Rules Modal */}

      {showRules && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50 }}>
          <div style={{ background: '#131b2e', border: '1px solid #2e3d66', padding: '24px', borderRadius: '20px', maxWidth: '420px', width: '90%' }}>
            <h3 style={{ margin: '0 0 12px 0' }}>Arena Combat Rules</h3>
            <ul style={{ lineHeight: 1.8, fontSize: '14px', color: '#94a3b8' }}>
              <li>🪨 <strong>Rock</strong> beats ✂️ Scissors</li>
              <li>✂️ <strong>Scissors</strong> beats 📄 Paper</li>
              <li>📄 <strong>Paper</strong> beats 🪨 Rock</li>
              <li>Matching weapons result in a <strong>Draw</strong></li>
              <li>Selections remain <strong>hidden</strong> until both submit</li>
            </ul>
            <button
              onClick={() => setShowRules(false)}
              style={{ width: '100%', padding: '10px', background: '#6366f1', color: '#fff', border: 'none', borderRadius: '10px', fontWeight: 700, cursor: 'pointer' }}
            >
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
