package com.noxcrew.interfaces.utilities

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit

/**
 * Scheduler abstraction that transparently handles both Paper/Spigot (single main thread)
 * and Folia (region-based threading).
 */
internal object FoliaScheduler {

    /** Whether the server we're running on is Folia. */
    @JvmStatic
    val isFolia: Boolean = runCatching {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        true
    }.getOrDefault(false)

    /**
     * Runs [function] on the main/global-region thread *synchronously* if we're already
     * on an appropriate thread; otherwise schedules it on the player's entity scheduler
     * (Folia) or the global scheduler (Paper/Spigot).
     *
     * When [entity] is non-null and we're on Folia, the task is scheduled on that entity's
     * scheduler so it follows the entity across regions. Used by operations that act on a
     * specific player (e.g. closing their inventory).
     */
    @JvmStatic
    fun runSync(plugin: Plugin, entity: Entity?, function: Runnable) {
        if (!isFolia) {
            if (Bukkit.isPrimaryThread()) {
                function.run()
            } else {
                Bukkit.getScheduler().callSyncMethod(plugin) { function.run() }
            }
            return
        }

        if (entity != null) {
            entity.scheduler.execute(plugin, function, null, 0L)
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin, function)
        }
    }

    /**
     * Schedules [function] to run after [delayTicks] on the main/global thread.
     * Routes to `GlobalRegionScheduler.runDelayed` on Folia.
     */
    @JvmStatic
    fun runTaskLater(plugin: Plugin, delayTicks: Long, function: Runnable) {
        val safeDelay = if (delayTicks <= 0L) 1L else delayTicks
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { function.run() }, safeDelay)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, function, safeDelay)
        }
    }

    /**
     * Schedules [function] to run asynchronously after [delayTicks].
     * Routes to `AsyncScheduler.runDelayed` (in milliseconds) on Folia.
     */
    @JvmStatic
    fun runTaskLaterAsynchronously(plugin: Plugin, delayTicks: Long, function: Runnable) {
        if (isFolia) {
            val delayMs = delayTicks.coerceAtLeast(0L) * 50L
            if (delayMs <= 0L) {
                Bukkit.getAsyncScheduler().runNow(plugin) { function.run() }
            } else {
                Bukkit.getAsyncScheduler().runDelayed(plugin, { function.run() }, delayMs, TimeUnit.MILLISECONDS)
            }
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, function, delayTicks.coerceAtLeast(0L))
        }
    }
}
