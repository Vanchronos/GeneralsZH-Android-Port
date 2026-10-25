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
#include "Common/ActionManager.h"
#include "Common/Money.h"
#include "Common/Player.h"
#include "Common/PlayerList.h"
#include "Common/SpecialPowerType.h"
#include "Common/KindOf.h"
#include "GameLogic/ExperienceTracker.h"
#include "GameLogic/GameLogic.h"
#include "GameLogic/Locomotor.h"
#include "GameLogic/Module/AIUpdate.h"
#include "GameLogic/Object.h"
#include "GameLogic/Module/BodyModule.h"
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
	std::atomic<bool> s_damage( false );
	std::atomic<bool> s_fireRate( false );
	std::atomic<bool> s_tough( false );
	std::atomic<bool> s_speed( false );
	std::atomic<bool> s_elite( false );
	std::atomic<bool> s_airAmmo( false );
	std::atomic<bool> s_npcStop( false );
	std::atomic<bool> s_autoHealUnits( false );
	std::atomic<bool> s_autoRepairAll( false );
	std::atomic<bool> s_turretFireSpeed( false );
	std::atomic<bool> s_turretRange( false );
	std::atomic<bool> s_turretDetection( false );

	// One-shot requests from the UI thread. They are carried out in update(), on the game thread,
	// because changing a Player from the Android UI thread would race with the game logic.
	std::atomic<int>  s_pendingGeneralPoints( 0 );
	std::atomic<bool> s_pendingMaxRank( false );
	std::atomic<bool> s_pendingRepairAllBuildings( false ); // gx_repair_all_buildings_patch
	std::atomic<bool> s_pendingInstantCapture( false ); // gx_cheat141_all_v1
	std::atomic<bool> s_pendingHealRepairAll( false ); // heal all local units and structures
	std::atomic<bool> s_buildAnywhere( false );

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
		| (s_damage ? FLAG_DAMAGE : 0)
		| (s_fireRate ? FLAG_FIRE_RATE : 0)
		| (s_tough ? FLAG_TOUGH : 0)
		| (s_speed ? FLAG_SPEED : 0)
		| (s_elite ? FLAG_ELITE : 0)
		| (s_airAmmo ? FLAG_AIR_AMMO : 0)
		| (s_buildAnywhere ? FLAG_BUILD_ANYWHERE : 0)
		| (s_npcStop ? FLAG_NPC_STOP : 0)
		| (s_autoHealUnits ? FLAG_AUTO_HEAL_UNITS : 0)
		| (s_autoRepairAll ? FLAG_AUTO_REPAIR_ALL : 0)
		| (s_turretFireSpeed ? FLAG_TURRET_FIRE_SPEED : 0)
		| (s_turretRange ? FLAG_TURRET_RANGE : 0)
		| (s_turretDetection ? FLAG_TURRET_DETECTION : 0);
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
		case SET_DAMAGE:       s_damage = value != 0; break;
		case SET_FIRE_RATE:    s_fireRate = value != 0; break;
		case SET_TOUGH:        s_tough = value != 0; break;
		case SET_SPEED:        s_speed = value != 0; break;
		case SET_ELITE:        s_elite = value != 0; break;
		case SET_AIR_AMMO:     s_airAmmo = value != 0; break;
		case SET_BUILD_ANYWHERE: s_buildAnywhere = value != 0; break;
		case SET_NPC_STOP: s_npcStop = value != 0; break;
		case SET_AUTO_HEAL_UNITS: s_autoHealUnits = value != 0; break;
		case SET_AUTO_REPAIR_ALL: s_autoRepairAll = value != 0; break;
		case SET_TURRET_FIRE_SPEED: s_turretFireSpeed = value != 0; break;
		case SET_TURRET_RANGE: s_turretRange = value != 0; break;
		case SET_TURRET_DETECTION: s_turretDetection = value != 0; break;
		case ACT_HEAL_REPAIR_ALL:
			if( gameAllowsCheats() )
				s_pendingHealRepairAll = true;
			break;
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
		case ACT_REPAIR_ALL_BUILDINGS:
			if( gameAllowsCheats() )
				s_pendingRepairAllBuildings = true;
			break;
		case ACT_INSTANT_CAPTURE:
			if( gameAllowsCheats() )
				s_pendingInstantCapture = true;
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
Bool buildAnywhere( const Player *player ) { return s_buildAnywhere && appliesTo( player ); }
Bool noCooldown( const Player *player )  { return s_noCooldown && appliesTo( player ); }

Bool healthBarsAlways()
{
	return s_healthBars && gameAllowsCheats();
}

// Called for every weapon of every player; only the local human's weapons are ever changed.
void applyWeaponBonus( const Object *source, const Player *owner, WeaponBonus &bonus )
{
	const bool vehicleOrStructure = source != nullptr
		&& (source->isKindOf( KINDOF_VEHICLE ) || source->isKindOf( KINDOF_STRUCTURE ));
	const bool turretFire = vehicleOrStructure && s_turretFireSpeed;
	const bool turretRange = vehicleOrStructure && s_turretRange;
	if( !(s_damage || s_fireRate || turretFire || turretRange) || !appliesTo( owner ) )
		return;
	if( s_damage )
		bonus.setField( WeaponBonus::DAMAGE, bonus.getField( WeaponBonus::DAMAGE ) * kDamageScale );
	if( s_fireRate )
		bonus.setField( WeaponBonus::RATE_OF_FIRE, bonus.getField( WeaponBonus::RATE_OF_FIRE ) * kFireRateScale );
	// Avoid stacking two separate x3 fire-rate switches to x9. The existing general Fire Rate
	// cheat already supplies x3 to this weapon; otherwise this turret-specific toggle supplies x3.
	if( turretFire && !s_fireRate )
		bonus.setField( WeaponBonus::RATE_OF_FIRE, bonus.getField( WeaponBonus::RATE_OF_FIRE ) * 3.0f );
	if( turretRange )
		bonus.setField( WeaponBonus::RANGE, bonus.getField( WeaponBonus::RANGE ) * 2.0f );
}

Real turretDetectionScale( const Object *object )
{
	if( !s_turretDetection || object == nullptr )
		return 1.0f;
	if( !(object->isKindOf( KINDOF_VEHICLE ) || object->isKindOf( KINDOF_STRUCTURE )) )
		return 1.0f;
	return appliesTo( object->getControllingPlayer() ) ? 2.0f : 1.0f;
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
	const bool autoHealUnits = s_autoHealUnits;
	const bool autoRepairAll = s_autoRepairAll;

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

		// Gradual healing is deliberately limited to living objects owned by the local player.
		BodyModuleInterface *body = obj->getBodyModule();
		if( body != nullptr && (autoRepairAll || (autoHealUnits && !obj->isKindOf( KINDOF_STRUCTURE ))) )
		{
			const Real maxHealth = body->getMaxHealth();
			const Real missingHealth = maxHealth - body->getHealth();
			Real healAmount = 0.0f;
			if( autoHealUnits && !obj->isKindOf( KINDOF_STRUCTURE ) )
				healAmount += maxHealth * 0.05f; // 10% max HP/second at the existing half-second upkeep
			if( autoRepairAll )
				healAmount += maxHealth * 0.10f; // includes units, aircraft and buildings
			if( healAmount > missingHealth ) healAmount = missingHealth;
			if( healAmount > 0.0f ) obj->attemptHealing( healAmount, nullptr );
		}
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

	// NPC Stop: temporarily disable all non-local player-owned objects (AI allies and enemies).
	// Timed disable expires automatically shortly after OFF, avoiding permanent state changes.
	if( s_npcStop && TheGameLogic != nullptr )
	{
		const UnsignedInt refreshFrames = (LOGICFRAMES_PER_SECOND / 4) > 0 ? (LOGICFRAMES_PER_SECOND / 4) : 1;
		if( now % refreshFrames == 0 )
		{
			for( Object *obj = TheGameLogic->getFirstObject(); obj != nullptr; obj = obj->getNextObject() )
			{
				Player *owner = obj->getControllingPlayer();
				if( owner == nullptr || owner == player || obj->isEffectivelyDead() ) continue;
				obj->setDisabledUntil( DISABLED_SCRIPT_DISABLED, now + refreshFrames * 2 );
			}
		}
	}

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

	// One-shot repair. Run on the game thread and touch only live structures
	// owned by the local human player; AI and enemy objects are never modified.
	// One-shot Instant Capture. The action only touches capturable structures near a local
	// human capture-capable unit; ActionManager remains authoritative for legal capture targets.
	if( s_pendingInstantCapture.exchange( false ) && TheGameLogic != nullptr && TheActionManager != nullptr )
	{
		const Real captureRadius = 96.0f;
		const Real captureRadiusSq = captureRadius * captureRadius;
		for( Object *target = TheGameLogic->getFirstObject(); target != nullptr; target = target->getNextObject() )
		{
			if( target->getControllingPlayer() == player || target->isEffectivelyDead() ||
				!target->isKindOf( KINDOF_STRUCTURE ) || target->isKindOf( KINDOF_IMMUNE_TO_CAPTURE ) ||
				target->testStatus( OBJECT_STATUS_UNDER_CONSTRUCTION ) || target->testStatus( OBJECT_STATUS_SOLD ) )
				continue;
			if( target->getShroudedStatus( player->getPlayerIndex() ) >= OBJECTSHROUD_FOGGED )
				continue;
			const Coord3D *targetPos = target->getPosition();
			if( targetPos == nullptr ) continue;
			for( Object *unit = TheGameLogic->getFirstObject(); unit != nullptr; unit = unit->getNextObject() )
			{
				if( unit->getControllingPlayer() != player || unit->isEffectivelyDead() ) continue;
				if( !unit->hasSpecialPower( SPECIAL_INFANTRY_CAPTURE_BUILDING ) &&
					!unit->hasSpecialPower( SPECIAL_BLACKLOTUS_CAPTURE_BUILDING ) ) continue;
				const Coord3D *unitPos = unit->getPosition();
				if( unitPos == nullptr ) continue;
				const Real dx = unitPos->x - targetPos->x;
				const Real dy = unitPos->y - targetPos->y;
				if( dx * dx + dy * dy > captureRadiusSq ) continue;
				if( TheActionManager->canCaptureBuilding( unit, target, CMD_FROM_SCRIPT ) )
				{
					// setTeam performs the engine's ownership/team transition and capture callbacks.
					target->setTeam( player->getDefaultTeam() );
					break;
				}
			}
		}
	}

	if( s_pendingRepairAllBuildings.exchange( false ) && TheGameLogic != nullptr )
	{
		for( Object *obj = TheGameLogic->getFirstObject(); obj != nullptr; obj = obj->getNextObject() )
		{
			if( obj->getControllingPlayer() != player || obj->isEffectivelyDead() || !obj->isKindOf( KINDOF_STRUCTURE ) )
				continue;
			BodyModuleInterface *body = obj->getBodyModule();
			if( body == nullptr )
				continue;
			const Real missingHealth = body->getMaxHealth() - body->getHealth();
			if( missingHealth > 0.0f )
				obj->attemptHealing( missingHealth, nullptr );
		}
	}

	// One-shot full heal for all live objects controlled by the local player.
	if( s_pendingHealRepairAll.exchange( false ) && TheGameLogic != nullptr )
	{
		for( Object *obj = TheGameLogic->getFirstObject(); obj != nullptr; obj = obj->getNextObject() )
		{
			if( obj->getControllingPlayer() != player || obj->isEffectivelyDead() || obj->getBodyModule() == nullptr )
				continue;
			obj->healCompletely();
		}
	}
}

} // namespace GXCheats

// ------------------------------------------------------------------------------------------------
// JNI entry points for CheatMenu.java (GeneralsZHActivity.nativeCheatSet / nativeCheatQuery).
// They live in THIS file on purpose: z_gameengine is a static library linked without --whole-archive,
// and this file is always pulled in because Money.cpp, Energy.cpp ... reference GXCheats, so these
// symbols reliably reach libmain.so. (A file holding nothing but JNI functions would be dropped.)
// The ids and their meaning live in Common/GXCheats.h, so a new cheat needs no change here.
#if defined(__ANDROID__)

#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_com_generalsx_zerohour_GeneralsZHActivity_nativeCheatSet(JNIEnv*, jclass, jint id, jint value)
{
	GXCheats::set(id, value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_generalsx_zerohour_GeneralsZHActivity_nativeCheatQuery(JNIEnv*, jclass, jint id)
{
	return GXCheats::query(id);
}

#endif // __ANDROID__
