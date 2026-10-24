import React, { useState, useEffect, useRef } from 'react';
import {
  MessageSquare,
  X,
  Minus,
  Send,
  Sparkles,
  Circle,
  Bot
} from 'lucide-react';
import { ChatMessage } from './types.ts';

interface FloatingChatProps {
  messages: ChatMessage[];
  currentUserId: string;
  onSendMessage: (text: string) => Promise<void> | void;
  title?: string;
  isOnline?: boolean;
  unreadCount?: number;
  onClearUnread?: () => void;
}

export function FloatingChat({
  messages,
  currentUserId,
  onSendMessage,
  title = 'Arena Live Chat',
  isOnline = true,
  unreadCount = 0,
  onClearUnread
}: FloatingChatProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Auto-scroll to latest message when open
  useEffect(() => {
    if (isOpen && !isMinimized) {
      chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen, isMinimized, isTyping]);

  // Focus input when opened
  useEffect(() => {
    if (isOpen && !isMinimized) {
      setTimeout(() => inputRef.current?.focus(), 150);
      onClearUnread?.();
    }
  }, [isOpen, isMinimized]);

  const handleToggleOpen = () => {
    if (isOpen) {
      setIsOpen(false);
      setIsMinimized(false);
    } else {
      setIsOpen(true);
      setIsMinimized(false);
      onClearUnread?.();
    }
  };

  const handleClose = (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsOpen(false);
    setIsMinimized(false);
  };

  const handleMinimize = (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsMinimized((prev) => !prev);
  };

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = input.trim();
    if (!trimmed) return;

    setInput('');
    try {
      await onSendMessage(trimmed);
    } catch {
      // Handled in parent
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const formatTimestamp = (ts: number): string => {
    try {
      const d = new Date(ts);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return '';
    }
  };

  return (
    <div style={{ position: 'fixed', bottom: '24px', right: '24px', zIndex: 9999 }}>
      {/* Popup Chat Window */}
      {isOpen && (
        <div
          role="dialog"
          aria-label={title}
          style={{
            position: 'absolute',
            bottom: '72px',
            right: '0',
            width: '370px',
            maxWidth: 'calc(100vw - 32px)',
            height: isMinimized ? '58px' : '520px',
            maxHeight: 'calc(100vh - 120px)',
            background: '#131b2e',
            border: '1px solid #2e3d66',
            borderRadius: '20px',
            boxShadow: '0 20px 45px rgba(0, 0, 0, 0.7), 0 0 15px rgba(99, 102, 241, 0.25)',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            transition: 'height 0.25s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.2s ease, transform 0.2s ease',
            transformOrigin: 'bottom right',
            animation: 'chatPopupEnter 0.25s cubic-bezier(0.16, 1, 0.3, 1)'
          }}
        >
          {/* Header */}
          <div
            style={{
              padding: '12px 16px',
              background: '#1e2843',
              borderBottom: isMinimized ? 'none' : '1px solid #2e3d66',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: isMinimized ? 'pointer' : 'default',
              userSelect: 'none'
            }}
            onClick={() => isMinimized && setIsMinimized(false)}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div
                style={{
                  width: '34px',
                  height: '34px',
                  borderRadius: '10px',
                  background: 'linear-gradient(135deg, #6366f1, #06b6d4)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 0 10px rgba(6, 182, 212, 0.4)'
                }}
              >
                <MessageSquare size={18} color="#ffffff" />
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: '14px', fontWeight: 700, color: '#f8fafc' }}>
                  {title}
                </h3>
                <div style={{ display: 'flex', alignItems: 'center', gap: '5px', marginTop: '1px' }}>
                  <span
                    style={{
                      width: '7px',
                      height: '7px',
                      borderRadius: '50%',
                      background: isOnline ? '#10b981' : '#f43f5e',
                      boxShadow: isOnline ? '0 0 6px #10b981' : 'none'
                    }}
                  />
                  <span style={{ fontSize: '11px', color: isOnline ? '#10b981' : '#94a3b8' }}>
                    {isOnline ? 'Online • Real-Time' : 'Offline'}
                  </span>
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <button
                type="button"
                onClick={handleMinimize}
                aria-label={isMinimized ? 'Expand Chat' : 'Minimize Chat'}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: '#94a3b8',
                  padding: '6px',
                  borderRadius: '8px',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <Minus size={16} />
              </button>
              <button
                type="button"
                onClick={handleClose}
                aria-label="Close Chat"
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: '#94a3b8',
                  padding: '6px',
                  borderRadius: '8px',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <X size={16} />
              </button>
            </div>
          </div>

          {!isMinimized && (
            <>
              {/* Message List */}
              <div
                style={{
                  flex: 1,
                  overflowY: 'auto',
                  padding: '14px',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '10px',
                  background: '#0c101c'
                }}
              >
                {/* Welcome Message Banner */}
                <div
                  style={{
                    background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.15), rgba(6, 182, 212, 0.1))',
                    border: '1px solid rgba(99, 102, 241, 0.25)',
                    borderRadius: '12px',
                    padding: '10px 12px',
                    fontSize: '12px',
                    color: '#cbd5e1',
                    lineHeight: '1.4',
                    textAlign: 'center'
                  }}
                >
                  <p style={{ margin: '0 0 2px 0', fontWeight: 600, color: '#06b6d4' }}>
                    ⚡ Welcome to RPS Arena Chat!
                  </p>
                  <p style={{ margin: 0, fontSize: '11px', color: '#94a3b8' }}>
                    Chat live with your opponent or coordinate game rounds in real-time.
                  </p>
                </div>

                {messages.length === 0 && (
                  <div
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flex: 1,
                      color: '#64748b',
                      fontSize: '12px',
                      padding: '24px 0'
                    }}
                  >
                    <MessageSquare size={32} color="#334155" style={{ marginBottom: '8px' }} />
                    <p style={{ margin: 0 }}>No messages yet.</p>
                    <p style={{ margin: '4px 0 0 0', fontSize: '11px' }}>Say hello to start the battle talk!</p>
                  </div>
                )}

                {messages.map((msg) => {
                  if (msg.isSystem) {
                    return (
                      <div
                        key={msg.id}
                        style={{
                          textAlign: 'center',
                          fontSize: '11px',
                          color: '#94a3b8',
                          background: '#1e2843',
                          border: '1px solid #334155',
                          padding: '4px 10px',
                          borderRadius: '12px',
                          alignSelf: 'center',
                          maxWidth: '90%'
                        }}
                      >
                        {msg.text}
                      </div>
                    );
                  }

                  const isMe = msg.senderId === currentUserId;

                  return (
                    <div
                      key={msg.id}
                      style={{
                        alignSelf: isMe ? 'flex-end' : 'flex-start',
                        maxWidth: '82%',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: isMe ? 'flex-end' : 'flex-start'
                      }}
                    >
                      {!isMe && (
                        <span
                          style={{
                            fontSize: '10px',
                            fontWeight: 700,
                            color: '#06b6d4',
                            marginBottom: '2px',
                            paddingLeft: '4px'
                          }}
                        >
                          {msg.senderName}
                        </span>
                      )}

                      <div
                        style={{
                          background: isMe
                            ? 'linear-gradient(135deg, #6366f1, #4f46e5)'
                            : '#1e2843',
                          color: '#ffffff',
                          padding: '9px 13px',
                          borderRadius: isMe ? '14px 14px 2px 14px' : '14px 14px 14px 2px',
                          fontSize: '13px',
                          lineHeight: 1.4,
                          wordBreak: 'break-word',
                          boxShadow: isMe ? '0 2px 8px rgba(99, 102, 241, 0.3)' : 'none',
                          border: isMe ? 'none' : '1px solid #334155'
                        }}
                      >
                        {msg.text}
                      </div>

                      <span
                        style={{
                          fontSize: '9px',
                          color: '#64748b',
                          marginTop: '2px',
                          padding: '0 4px'
                        }}
                      >
                        {formatTimestamp(msg.timestamp)}
                      </span>
                    </div>
                  );
                })}

                {isTyping && (
                  <div
                    style={{
                      alignSelf: 'flex-start',
                      background: '#1e2843',
                      border: '1px solid #334155',
                      padding: '8px 12px',
                      borderRadius: '14px',
                      display: 'flex',
                      gap: '4px',
                      alignItems: 'center'
                    }}
                  >
                    <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#06b6d4', animation: 'bounce 1s infinite 0ms' }} />
                    <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#06b6d4', animation: 'bounce 1s infinite 200ms' }} />
                    <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#06b6d4', animation: 'bounce 1s infinite 400ms' }} />
                  </div>
                )}

                <div ref={chatEndRef} />
              </div>

              {/* Input Area */}
              <form
                onSubmit={handleSend}
                style={{
                  padding: '10px 14px',
                  background: '#131b2e',
                  borderTop: '1px solid #2e3d66',
                  display: 'flex',
                  gap: '8px',
                  alignItems: 'center'
                }}
              >
                <input
                  ref={inputRef}
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value.slice(0, 500))}
                  onKeyDown={handleKeyDown}
                  placeholder="Type your message..."
                  maxLength={500}
                  aria-label="Chat input message"
                  style={{
                    flex: 1,
                    padding: '10px 14px',
                    background: '#1e2843',
                    border: '1px solid #334155',
                    borderRadius: '12px',
                    color: '#f8fafc',
                    fontSize: '13px',
                    outline: 'none'
                  }}
                />
                <button
                  type="submit"
                  disabled={!input.trim()}
                  aria-label="Send message"
                  style={{
                    width: '38px',
                    height: '38px',
                    borderRadius: '12px',
                    background: input.trim() ? '#6366f1' : '#1e2843',
                    color: input.trim() ? '#ffffff' : '#64748b',
                    border: 'none',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: input.trim() ? 'pointer' : 'not-allowed',
                    transition: 'all 0.15s ease',
                    boxShadow: input.trim() ? '0 0 12px rgba(99, 102, 241, 0.5)' : 'none'
                  }}
                >
                  <Send size={16} />
                </button>
              </form>
            </>
          )}
        </div>
      )}

      {/* Floating Chat Bubble Button */}
      <button
        type="button"
        onClick={handleToggleOpen}
        aria-label={isOpen ? 'Close Live Chat' : 'Open Live Chat'}
        aria-expanded={isOpen}
        style={{
          width: '56px',
          height: '56px',
          borderRadius: '50%',
          background: 'linear-gradient(135deg, #6366f1 0%, #4338ca 100%)',
          border: '2px solid rgba(255, 255, 255, 0.2)',
          boxShadow: '0 10px 25px -5px rgba(99, 102, 241, 0.5), 0 8px 10px -6px rgba(99, 102, 241, 0.4)',
          color: '#ffffff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          position: 'relative',
          transition: 'transform 0.2s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.2s ease',
          outline: 'none'
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.transform = 'scale(1.08)';
          e.currentTarget.style.boxShadow = '0 12px 30px rgba(99, 102, 241, 0.65)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.transform = 'scale(1)';
          e.currentTarget.style.boxShadow = '0 10px 25px -5px rgba(99, 102, 241, 0.5), 0 8px 10px -6px rgba(99, 102, 241, 0.4)';
        }}
      >
        {isOpen ? <X size={24} /> : <MessageSquare size={24} />}

        {/* Small Notification Badge */}
        {!isOpen && unreadCount > 0 && (
          <span
            style={{
              position: 'absolute',
              top: '-4px',
              right: '-4px',
              minWidth: '20px',
              height: '20px',
              padding: '0 5px',
              borderRadius: '10px',
              background: '#f43f5e',
              border: '2px solid #090d16',
              color: '#ffffff',
              fontSize: '11px',
              fontWeight: 800,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 8px rgba(244, 63, 94, 0.8)',
              animation: 'pulseBadge 1.5s infinite'
            }}
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>
    </div>
  );
}
