export type GameChoice = 'ROCK' | 'PAPER' | 'SCISSORS';

export type Winner = 'PLAYER_ONE' | 'PLAYER_TWO' | 'DRAW' | 'UNDECIDED';

export type RoomStatus =
  | 'WAITING_FOR_OPPONENT'
  | 'IN_PROGRESS'
  | 'ROUND_REVEAL'
  | 'OPPONENT_DISCONNECTED'
  | 'ABANDONED';

export interface Player {
  id: string;
  username: string;
  score: number;
  hasSubmitted: boolean;
  revealedChoice: GameChoice | null;
  isConnected: boolean;
}

export interface ChatMessage {
  id: string;
  senderId: string;
  senderName: string;
  text: string;
  timestamp: number;
  isSystem: boolean;
}

export interface Room {
  roomId: string;
  player1: Player;
  player2: Player | null;
  roundNumber: number;
  draws: number;
  status: RoomStatus;
  lastWinner: Winner;
  chatMessages: ChatMessage[];
}
