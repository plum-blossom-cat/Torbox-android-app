package io.github.plumblossomcat.torbox.data

import io.github.plumblossomcat.torbox.core.api.UserDto
import io.github.plumblossomcat.torbox.core.util.Timestamps
import java.time.Instant

/** TorBox plan ids as documented: 0 Free, 1 Essential, 2 Pro, 3 Standard. */
fun planName(plan: Long): String = when (plan) {
    0L -> "Free"
    1L -> "Essential"
    2L -> "Pro"
    3L -> "Standard"
    else -> "Plan $plan"
}

val UserDto.planLabel: String get() = planName(plan)
val UserDto.expiresAt: Instant? get() = Timestamps.parse(premiumExpiresAt)
val UserDto.memberSince: Instant? get() = Timestamps.parse(createdAt)
val UserDto.isPro: Boolean get() = plan == 2L
