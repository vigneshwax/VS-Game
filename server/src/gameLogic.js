/**
 * Server-authoritative Rock Paper Scissors rules engine.
 */

export const CHOICES = {
  ROCK: 'ROCK',
  PAPER: 'PAPER',
  SCISSORS: 'SCISSORS'
};

export const WINNERS = {
  PLAYER_ONE: 'PLAYER_ONE',
  PLAYER_TWO: 'PLAYER_TWO',
  DRAW: 'DRAW',
  UNDECIDED: 'UNDECIDED'
};

/**
 * Validates whether a choice is a valid weapon.
 * @param {string} choice 
 * @returns {boolean}
 */
export function isValidChoice(choice) {
  return choice === CHOICES.ROCK || choice === CHOICES.PAPER || choice === CHOICES.SCISSORS;
}

/**
 * Authoritative winner determination:
 * - Rock beats Scissors
 * - Scissors beats Paper
 * - Paper beats Rock
 * - Identical choices result in a Draw
 * @param {string} p1Choice 
 * @param {string} p2Choice 
 * @returns {string} WINNERS enum
 */
export function evaluateWinner(p1Choice, p2Choice) {
  if (!isValidChoice(p1Choice) || !isValidChoice(p2Choice)) {
    return WINNERS.UNDECIDED;
  }

  if (p1Choice === p2Choice) {
    return WINNERS.DRAW;
  }

  if (
    (p1Choice === CHOICES.ROCK && p2Choice === CHOICES.SCISSORS) ||
    (p1Choice === CHOICES.SCISSORS && p2Choice === CHOICES.PAPER) ||
    (p1Choice === CHOICES.PAPER && p2Choice === CHOICES.ROCK)
  ) {
    return WINNERS.PLAYER_ONE;
  }

  return WINNERS.PLAYER_TWO;
}
