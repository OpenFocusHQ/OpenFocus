package org.openfocus.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun habitKind(v: HabitKind): String = v.name
    @TypeConverter fun habitKind(v: String): HabitKind = HabitKind.valueOf(v)
    @TypeConverter fun freq(v: HabitFrequency): String = v.name
    @TypeConverter fun freq(v: String): HabitFrequency = HabitFrequency.valueOf(v)
    @TypeConverter fun diff(v: Difficulty): String = v.name
    @TypeConverter fun diff(v: String): Difficulty = Difficulty.valueOf(v)
    @TypeConverter fun goalStatus(v: GoalStatus): String = v.name
    @TypeConverter fun goalStatus(v: String): GoalStatus = GoalStatus.valueOf(v)
    @TypeConverter fun outcome(v: FocusOutcome): String = v.name
    @TypeConverter fun outcome(v: String): FocusOutcome = FocusOutcome.valueOf(v)
    @TypeConverter fun blockMode(v: BlockMode): String = v.name
    @TypeConverter fun blockMode(v: String): BlockMode = BlockMode.valueOf(v)
    @TypeConverter fun blockGate(v: BlockGate): String = v.name
    @TypeConverter fun blockGate(v: String): BlockGate = BlockGate.valueOf(v)
}
