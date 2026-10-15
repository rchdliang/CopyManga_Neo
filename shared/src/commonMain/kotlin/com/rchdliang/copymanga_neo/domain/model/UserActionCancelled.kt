package com.rchdliang.copymanga_neo.domain.model

/** A dismissed native action, not cancellation of the coroutine that owns UI state. */
class UserActionCancelled : Exception("User dismissed action")

/** Nullable native completion payloads distinguish dismissal from an acknowledged action. */
fun Result<String?>.asUserActionResult(): Result<Unit> = mapCatching { value ->
    if (value == null) throw UserActionCancelled()
    Unit
}

/** Native share completion may include an error even when a completion flag is set. */
fun nativeShareResult(completed: Boolean, error: String?): Result<Unit> = when {
    error != null -> Result.failure(IllegalStateException(error))
    completed -> Result.success(Unit)
    else -> Result.failure(UserActionCancelled())
}
