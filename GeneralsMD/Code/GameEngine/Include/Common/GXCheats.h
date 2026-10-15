/*
**	Command & Conquer Generals Zero Hour(tm)
**	Copyright 2025 Electronic Arts Inc.
**
**	This program is free software: you can redistribute it and/or modify
**	it under the terms of the GNU General Public License as published by
**	the Free Software Foundation, either version 3 of the License, or
**	(at your option) any later version.
**
**	This program is distributed in the hope that it will be useful,
**	but WITHOUT ANY WARRANTY; without even the implied warranty of
**	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**	GNU General Public License for more details.
**
**	You should have received a copy of the GNU General Public License
**	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

// GeneralsX @feature Android port Single-player cheat menu (engine side).
//
// The switches live here; the Android overlay (CheatMenu.java) flips them over JNI.
// A cheat only ever applies to ONE player: the human at this device (the local player).
// AI players - enemy, ally or neutral - are never touched. And nothing applies at all in a
// LAN/online match or a replay: those run in lockstep, and a one-sided change would desync
// the match (and be unfair to the other humans). Skirmish and campaign only.
#pragma once

#include "Lib/BaseType.h"

class Player;

namespace GXCheats
{
	// Maximum money the "Max money" switch tops the player up to.
	const UnsignedInt kMaxMoney = 99999999;

	// Ids shared with CheatMenu.java (through the generic set()/query() JNI pair).
	enum SetId
	{
		SET_MAX_MONEY = 0,
		SET_FREEZE_MONEY = 1,
		SET_FAST_BUILD = 2,
		SET_ENERGY_FULL = 3,
		SET_ENERGY_BONUS = 4,        // value = extra kW
		SET_NO_COOLDOWN = 5,         // special powers and Generals powers
		SET_REVEAL_MAP = 6,
		SET_HEALTH_BARS = 7,         // health bar on every unit
		ACT_ADD_GENERAL_POINTS = 8,  // one-shot, value = how many
		ACT_MAX_RANK = 9             // one-shot
	};
	enum QueryId { QUERY_AVAILABLE = 0, QUERY_FLAGS = 1, QUERY_ENERGY_BONUS = 2 };

	// Flag bits returned by query(QUERY_FLAGS).
	enum FlagBit
	{
		FLAG_MAX_MONEY = 1, FLAG_FREEZE_MONEY = 2, FLAG_FAST_BUILD = 4, FLAG_ENERGY_FULL = 8,
		FLAG_NO_COOLDOWN = 16, FLAG_REVEAL_MAP = 32, FLAG_HEALTH_BARS = 64
	};

	// ---- from the Android UI thread (thread-safe; the engine applies them on its own thread) ----
	void set( Int id, Int value );
	Int  query( Int id );

	void setMaxMoney( Bool on );
	void setFreezeMoney( Bool on );
	void setFastBuild( Bool on );
	void setEnergyFull( Bool on );        // never "low power", ignores sabotage
	void setEnergyBonus( Int kilowatts ); // extra kW of production, 0 = none

	Int  getFlags();
	Int  getEnergyBonus();

	// 1 when a cheat can apply right now (a skirmish/campaign game is running), else 0.
	Int  isAvailable();

	// ---- engine hooks ----
	// True only for the human local player of a single-player game.
	Bool appliesTo( const Player *player );
	Bool appliesToIndex( Int playerIndex );

	Bool freezeMoney( const Player *player );
	Bool fastBuild( const Player *player );
	Bool energyFull( const Player *player );
	Int  energyBonus( const Player *player );
	Bool noCooldown( const Player *player );

	// Display only: true when health bars should be drawn on every unit.
	Bool healthBarsAlways();

	// Once per logic frame for each player, from Player::update().
	void update( Player *player );
}
