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

// GeneralsX @feature Android cheat menu: JNI entry points for CheatMenu.java.
// The switches themselves, and every rule about where they may apply, live in
// Common/GXCheats.cpp; this file only carries the Java calls across.

#if defined(__ANDROID__)

#include "Common/GXCheats.h"

#include <jni.h>

namespace
{
	// Keep in step with CheatMenu.java.
	enum SetId { SET_MAX_MONEY = 0, SET_FREEZE_MONEY = 1, SET_FAST_BUILD = 2, SET_ENERGY_FULL = 3, SET_ENERGY_BONUS = 4 };
	enum QueryId { QUERY_AVAILABLE = 0, QUERY_FLAGS = 1, QUERY_ENERGY_BONUS = 2 };
}

extern "C" JNIEXPORT void JNICALL
Java_com_generalsx_zerohour_GeneralsZHActivity_nativeCheatSet(JNIEnv*, jclass, jint id, jint value)
{
	switch (id)
	{
		case SET_MAX_MONEY:    GXCheats::setMaxMoney(value != 0); break;
		case SET_FREEZE_MONEY: GXCheats::setFreezeMoney(value != 0); break;
		case SET_FAST_BUILD:   GXCheats::setFastBuild(value != 0); break;
		case SET_ENERGY_FULL:  GXCheats::setEnergyFull(value != 0); break;
		case SET_ENERGY_BONUS: GXCheats::setEnergyBonus(value); break;
		default: break;
	}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_generalsx_zerohour_GeneralsZHActivity_nativeCheatQuery(JNIEnv*, jclass, jint id)
{
	switch (id)
	{
		case QUERY_AVAILABLE:    return GXCheats::isAvailable();
		case QUERY_FLAGS:        return GXCheats::getFlags();
		case QUERY_ENERGY_BONUS: return GXCheats::getEnergyBonus();
		default: return 0;
	}
}

#endif // __ANDROID__
