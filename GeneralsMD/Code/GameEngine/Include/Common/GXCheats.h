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
class WeaponBonus;

namespace GXCheats
{
	// Maximum money the "Max money" switch tops the player up to.
	const UnsignedInt kMaxMoney = 99999999;

	// Strength of the extra cheats. They only ever apply to the local human player.
	const Real kDamageScale = 3.0f;        // damage dealt by the player's weapons
	const Real kFireRateScale = 3.0f;      // fire rate of the player's weapons
	const Real kDamageTakenScale = 0.1f;   // "tough units": the player's units take 10% of the damage
	const Real kSpeedScale = 1.5f;         // movement speed of the player's units

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
		ACT_MAX_RANK = 9,            // one-shot
		// Id 10 and 17 are intentionally unused; keep the other cheat IDs stable.
		ACT_REPAIR_ALL_BUILDINGS = 18, // one-shot: heal local player's structures
		ACT_INSTANT_CAPTURE = 19,      // one-shot: capture eligible nearby structures
		SET_DAMAGE = 11,             // weapons hit harder
		SET_FIRE_RATE = 12,          // weapons fire faster
		SET_TOUGH = 13,              // units take much less damage
		SET_SPEED = 14,              // units move faster
		SET_ELITE = 15,              // units are always at the top veterancy level
		SET_AIR_AMMO = 16,           // aircraft never need to reload
		SET_BUILD_ANYWHERE = 20       // relax local-player placement restrictions
	};
	enum QueryId { QUERY_AVAILABLE = 0, QUERY_FLAGS = 1, QUERY_ENERGY_BONUS = 2 };

	// Flag bits returned by query(QUERY_FLAGS).
	enum FlagBit
	{
		FLAG_MAX_MONEY = 1, FLAG_FREEZE_MONEY = 2, FLAG_FAST_BUILD = 4, FLAG_ENERGY_FULL = 8,
		FLAG_NO_COOLDOWN = 16, FLAG_REVEAL_MAP = 32, FLAG_HEALTH_BARS = 64,
		FLAG_DAMAGE = 256, FLAG_FIRE_RATE = 512, FLAG_TOUGH = 1024, FLAG_SPEED = 2048,
		FLAG_ELITE = 4096, FLAG_AIR_AMMO = 8192, FLAG_BUILD_ANYWHERE = 16384
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

	// Weapon::computeBonus(): scale damage / fire rate if the weapon belongs to the local human player.
	void applyWeaponBonus( const Player *owner, WeaponBonus &bonus );

	// ActiveBody::attemptDamage(): 1 normally, kDamageTakenScale for the local human player's objects.
	Real damageTakenScale( const Player *owner );

	// Display only: true when health bars should be drawn on every unit.
	Bool healthBarsAlways();
	Bool buildAnywhere( const Player *player );

	// Once per logic frame for each player, from Player::update().
	void update( Player *player );
}
