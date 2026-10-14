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
#include "GameLogic/GameLogic.h"

#include <atomic>

namespace
{
	std::atomic<bool> s_maxMoney( false );
	std::atomic<bool> s_freezeMoney( false );
	std::atomic<bool> s_fastBuild( false );
	std::atomic<bool> s_energyFull( false );
	std::atomic<int>  s_energyBonus( 0 );

	// Last energy setting pushed into the player's brown-out state (see update()).
	int s_lastEnergyKey = 0;
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
	return (s_maxMoney ? 1 : 0) | (s_freezeMoney ? 2 : 0) | (s_fastBuild ? 4 : 0) | (s_energyFull ? 8 : 0);
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

// ------------------------------------------------------------------------------------------------
void update( Player *player )
{
	if( !appliesTo( player ) )
		return;

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
}

} // namespace GXCheats
