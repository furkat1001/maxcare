package uz.maxcare.backend

import uz.maxcare.backend.devices.DeviceService
import uz.maxcare.backend.events.EventService
import uz.maxcare.backend.filterwords.FilterWordsService
import uz.maxcare.backend.location.LocationService
import uz.maxcare.backend.parents.ParentService
import uz.maxcare.backend.ratelimit.RateLimiter

/** Все зависимости сервера: в тестах собираются с тестовыми базой, часами и параметрами Argon2. */
class Services(
    val parents: ParentService,
    val devices: DeviceService,
    val events: EventService,
    val locations: LocationService,
    val filterWords: FilterWordsService,
    val rateLimiter: RateLimiter
)
