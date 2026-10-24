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

#include "PreRTS.h"	// This must go first in EVERY cpp file in the GameEngine
#include "Common/GXCheats.h"

#include "Common/Energy.h"
#include "Common/Money.h"
#include "Common/Player.h"
#include "Common/PlayerList.h"
#include "Common/KindOf.h"
#include "GameLogic/ExperienceTracker.h"
#include "GameLogic/GameLogic.h"
#include "GameLogic/Locomotor.h"
#include "GameLogic/Module/AIUpdate.h"
#include "GameLogic/Object.h"
#include "GameLogic/PartitionManager.h"
#include "GameLogic/RankInfo.h"
#include "GameLogic/Weapon.h"

#include <atomic>

namespace
{
	std::atomic<bool> s_maxMoney( false );
	std::atomic<bool> s_freezeMoney( false );
	std::atomic<bool> s_fastBuild( false );
	std::atomic<bool> s_energyFull( false );
	std::atomic<int>  s_energyBonus( 0 );
	std::atomic<bool> s_noCooldown( false );
	std::atomic<bool> s_revealMap( false );
	std::atomic<bool> s_healthBars( false );
	std::atomic<bool> s_economyInfo( false );
	std::atomic<bool> s_damage( false );
	std::atomic<bool> s_fireRate( false );
	std::atomic<bool> s_tough( false );
	std::atomic<bool> s_speed( false );
	std::atomic<bool> s_elite( false );
	std::atomic<bool> s_airAmmo( false );
	std::atomic<bool> s_income( false );

	// One-shot requests from the UI thread. They are carried out in update(), on the game thread,
	// because changing a Player from the Android UI thread would race with the game logic.
	std::atomic<int>  s_pendingGeneralPoints( 0 );
	std::atomic<bool> s_pendingMaxRank( false );

	// State that only the game thread touches.
	int          s_lastEnergyKey = 0;
	bool         s_revealApplied = false;
	UnsignedInt  s_lastFrame = 0;
}

namespace GXCheats
{

void setMaxMoney( Bool on )    { s_maxMoney = on != FALSE; }
void setFreezeMoney( Bool on ) { s_freezeMoney = on != FALSE; }
void setFastBuild( Bool on )   { s_fastBuild = on != FALSE; }
void setEnergyFull( Bool on )  { s_energyFull = on != FALSE; }
void setEnergyBonus( Int kilowatts )
{
	if( kilowatts < 0 )
		kilowatts = 0;
	if( kilowatts > 100000 )
		kilowatts = 100000;
	s_energyBonus = kilowatts;
}

Int getFlags()
{
	return (s_maxMoney ? FLAG_MAX_MONEY : 0)
		| (s_freezeMoney ? FLAG_FREEZE_MONEY : 0)
		| (s_fastBuild ? FLAG_FAST_BUILD : 0)
		| (s_energyFull ? FLAG_ENERGY_FULL : 0)
		| (s_noCooldown ? FLAG_NO_COOLDOWN : 0)
		| (s_revealMap ? FLAG_REVEAL_MAP : 0)
		| (s_healthBars ? FLAG_HEALTH_BARS : 0)
		| (s_economyInfo ? FLAG_ECONOMY_INFO : 0)
		| (s_damage ? FLAG_DAMAGE : 0)
		| (s_fireRate ? FLAG_FIRE_RATE : 0)
		| (s_tough ? FLAG_TOUGH : 0)
		| (s_speed ? FLAG_SPEED : 0)
		| (s_elite ? FLAG_ELITE : 0)
		| (s_airAmmo ? FLAG_AIR_AMMO : 0)
		| (s_income ? FLAG_INCOME : 0);
}

Int getEnergyBonus() { return s_energyBonus; }

// ------------------------------------------------------------------------------------------------
// Only a skirmish or campaign game, never a LAN/online match, replay or the shell map.
static Bool gameAllowsCheats()
{
	if( TheGameLogic == nullptr || ThePlayerList == nullptr )
		return FALSE;
	if( !TheGameLogic->isInGame() )
		return FALSE;
	if( TheGameLogic->isInShellGame() || TheGameLogic->isInReplayGame() || TheGameLogic->isInMultiplayerGame() )
		return FALSE;
	return TRUE;
}

Int isAvailable()
{
	return gameAllowsCheats() ? 1 : 0;
}

void set( Int id, Int value )
{
	switch( id )
	{
		case SET_MAX_MONEY:    setMaxMoney( value != 0 ); break;
		case SET_FREEZE_MONEY: setFreezeMoney( value != 0 ); break;
		case SET_FAST_BUILD:   setFastBuild( value != 0 ); break;
		case SET_ENERGY_FULL:  setEnergyFull( value != 0 ); break;
		case SET_ENERGY_BONUS: setEnergyBonus( value ); break;
		case SET_NO_COOLDOWN:  s_noCooldown = value != 0; break;
		case SET_REVEAL_MAP:   s_revealMap = value != 0; break;
		case SET_HEALTH_BARS:  s_healthBars = value != 0; break;
		case SET_ECONOMY_INFO: s_economyInfo = value != 0; break;
		case SET_DAMAGE:       s_damage = value != 0; break;
		case SET_FIRE_RATE:    s_fireRate = value != 0; break;
		case SET_TOUGH:        s_tough = value != 0; break;
		case SET_SPEED:        s_speed = value != 0; break;
		case SET_ELITE:        s_elite = value != 0; break;
		case SET_AIR_AMMO:     s_airAmmo = value != 0; break;
		case SET_INCOME:       s_income = value != 0; break;
		case ACT_ADD_GENERAL_POINTS:
			// Only while a game that allows cheats is running, so a tap in the menu cannot be
			// stored up and surprise-apply to a game started later.
			if( value > 0 && value <= 100 && gameAllowsCheats() )
				s_pendingGeneralPoints += value;
			break;
		case ACT_MAX_RANK:
			if( gameAllowsCheats() )
				s_pendingMaxRank = true;
			break;
		default: break;
	}
}

Int query( Int id )
{
	switch( id )
	{
		case QUERY_AVAILABLE:    return isAvailable();
		case QUERY_FLAGS:        return getFlags();
		case QUERY_ENERGY_BONUS: return getEnergyBonus();
		default: return 0;
	}
}

// ------------------------------------------------------------------------------------------------
Bool appliesTo( const Player *player )
{
	if( player == nullptr || !gameAllowsCheats() )
		return FALSE;
	// Never an AI player (enemy, ally or neutral); only the human at this device.
	if( player->getPlayerType() != PLAYER_HUMAN )
		return FALSE;
	return player->isLocalPlayer();
}

Bool appliesToIndex( Int playerIndex )
{
	if( ThePlayerList == nullptr || playerIndex < 0 || playerIndex >= ThePlayerList->getPlayerCount() )
		return FALSE;
	return appliesTo( ThePlayerList->getNthPlayer( playerIndex ) );
}

Bool freezeMoney( const Player *player ) { return s_freezeMoney && appliesTo( player ); }
Bool fastBuild( const Player *player )   { return s_fastBuild && appliesTo( player ); }
Bool energyFull( const Player *player )  { return s_energyFull && appliesTo( player ); }
Int  energyBonus( const Player *player ) { return appliesTo( player ) ? (Int)s_energyBonus : 0; }
Bool noCooldown( const Player *player )  { return s_noCooldown && appliesTo( player ); }

Bool healthBarsAlways()
{
	return s_healthBars && gameAllowsCheats();
}

Bool showEconomyInfo()
{
	return s_economyInfo && gameAllowsCheats();
}

// Called for every weapon of every player; only the local human's weapons are ever changed.
void applyWeaponBonus( const Player *owner, WeaponBonus &bonus )
{
	if( !(s_damage || s_fireRate) || !appliesTo( owner ) )
		return;
	if( s_damage )
		bonus.setField( WeaponBonus::DAMAGE, bonus.getField( WeaponBonus::DAMAGE ) * kDamageScale );
	if( s_fireRate )
		bonus.setField( WeaponBonus::RATE_OF_FIRE, bonus.getField( WeaponBonus::RATE_OF_FIRE ) * kFireRateScale );
}

Real damageTakenScale( const Player *owner )
{
	return ( s_tough && appliesTo( owner ) ) ? kDamageTakenScale : 1.0f;
}

// ------------------------------------------------------------------------------------------------
// Things that have to be done to the player's objects themselves, a couple of times a second.
static void upkeepObjects( Player *player )
{
	const Real speedScale = s_speed ? kSpeedScale : 1.0f; // written every time, so switching off restores 1.0
	const bool elite = s_elite;
	const bool airAmmo = s_airAmmo;

	for( Object *obj = TheGameLogic->getFirstObject(); obj != nullptr; obj = obj->getNextObject() )
	{
		if( obj->getControllingPlayer() != player || obj->isEffectivelyDead() )
			continue;

		AIUpdateInterface *ai = obj->getAI();
		if( ai != nullptr )
		{
			Locomotor *loco = ai->getCurLocomotor();
			if( loco != nullptr )
				loco->setGxSpeedScale( speedScale );
		}

		if( elite )
		{
			ExperienceTracker *xp = obj->getExperienceTracker();
			if( xp != nullptr && xp->isTrainable() )
				xp->setMinVeterancyLevel( LEVEL_HEROIC, FALSE );
		}

		if( airAmmo && obj->isKindOf( KINDOF_AIRCRAFT ) )
			obj->reloadAllAmmo( TRUE );
	}
}

// ------------------------------------------------------------------------------------------------
void update( Player *player )
{
	if( !appliesTo( player ) )
		return;

	// A new game starts counting frames from the start again: forget what we did to the last map.
	const UnsignedInt now = TheGameLogic->getFrame();
	if( now < s_lastFrame )
	{
		s_revealApplied = false;
		s_lastEnergyKey = 0;
	}
	s_lastFrame = now;

	// Max money: top up once the balance drops below 90% of the cap. deposit() with no sound and
	// no income tracking, so the income-per-minute statistic is left alone.
	if( s_maxMoney )
	{
		Money *money = player->getMoney();
		const UnsignedInt have = money->countMoney();
		if( have < kMaxMoney / 10 * 9 )
			money->deposit( kMaxMoney - have, FALSE, FALSE );
	}

	// The power state of every building is only recomputed when production or consumption
	// changes. A switch flipped in the menu changes neither, so push the new state through once.
	const int key = (s_energyFull ? 1 : 0) | ((int)s_energyBonus << 1);
	if( key != s_lastEnergyKey )
	{
		s_lastEnergyKey = key;
		player->onPowerBrownOutChange( !player->getEnergy()->hasSufficientPower() );
	}

	// Reveal map: the same calls the "reveal map permanently" script action uses, mid-game.
	// Waits until the map has been set up (frame > 1) and re-applies itself in a new game.
	if( ThePartitionManager != nullptr )
	{
		const bool wantReveal = s_revealMap;
		if( wantReveal && !s_revealApplied && now > 1 )
		{
			ThePartitionManager->revealMapForPlayerPermanently( player->getPlayerIndex() );
			s_revealApplied = true;
		}
		else if( !wantReveal && s_revealApplied )
		{
			// Only ever undoes a reveal that we did ourselves; undoing one that never happened
			// would shroud everything the player owns.
			ThePartitionManager->undoRevealMapForPlayerPermanently( player->getPlayerIndex() );
			s_revealApplied = false;
		}
	}

	// Extra income: a fixed amount every second, no sound and not counted as harvested income.
	if( s_income && now % LOGICFRAMES_PER_SECOND == 0 )
		player->getMoney()->deposit( kIncomePerSecond, FALSE, FALSE );

	// Speed, veterancy and aircraft ammo act on the objects: twice a second is plenty.
	// (Also runs when everything is off, to put the speed scale back to 1 after switching it off.)
	if( now % ( LOGICFRAMES_PER_SECOND / 2 ) == 0 )
		upkeepObjects( player );

	// One-shot General's-points requests, taken here on the game thread.
	const int points = s_pendingGeneralPoints.exchange( 0 );
	if( points > 0 )
		player->addSciencePurchasePoints( points );

	if( s_pendingMaxRank.exchange( false ) && TheRankInfoStore != nullptr )
		player->setRankLevel( TheRankInfoStore->getRankLevelCount() );
}

} // namespace GXCheats
