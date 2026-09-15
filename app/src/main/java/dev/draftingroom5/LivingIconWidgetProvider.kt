package dev.draftingroom5

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

/**
 * The system owns the hourly (inexact) update schedule while at least one tile is installed.
 * Manifest callbacks run in a private process so a redraw does not initialize the main app's
 * WorkManager/Startup providers. This provider keeps no process-local rotation or widget state.
 */
class LivingIconWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, manager, appWidgetIds, System.currentTimeMillis())
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        update(context, manager, intArrayOf(appWidgetId), System.currentTimeMillis())
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        refreshIfPresent(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) refreshIfPresent(context)
    }

    companion object {
        internal val images = intArrayOf(
            R.drawable.widget_01_dumbbell,
            R.drawable.widget_02_flex,
            R.drawable.widget_03_fire,
            R.drawable.widget_04_melt,
            R.drawable.widget_05_orbit,
            R.drawable.widget_06_jump_rope,
            R.drawable.widget_07_wave,
            R.drawable.widget_08_skateboard,
            R.drawable.widget_09_spotlight,
            R.drawable.widget_10_paper_plane,
            R.drawable.widget_11_kettlebell,
            R.drawable.widget_12_chalk_clap,
            R.drawable.widget_13_lightning,
            R.drawable.widget_14_frost,
            R.drawable.widget_15_alien_abduction,
            R.drawable.widget_16_lying_down,
            R.drawable.widget_17_pole_vault,
            R.drawable.widget_18_slingshot,
            R.drawable.widget_19_umbrella,
            R.drawable.widget_20_butterfly,
            R.drawable.widget_21_climbing,
            R.drawable.widget_22_balance_beam,
            R.drawable.widget_23_tornado,
            R.drawable.widget_24_geode,
            R.drawable.widget_25_portal,
            R.drawable.widget_26_shadow_puppet,
            R.drawable.widget_27_pendulum,
            R.drawable.widget_28_pinwheel,
            R.drawable.widget_29_balloon,
            R.drawable.widget_30_windup_toy,
            R.drawable.widget_31_sprint_start,
            R.drawable.widget_32_rowing,
            R.drawable.widget_33_rain_cloud,
            R.drawable.widget_34_coral,
            R.drawable.widget_35_impossible_stair,
            R.drawable.widget_36_origami,
            R.drawable.widget_37_spring,
            R.drawable.widget_38_domino,
            R.drawable.widget_39_juggling,
            R.drawable.widget_40_treasure,
            R.drawable.widget_41_punching_bag,
            R.drawable.widget_42_yoga,
            R.drawable.widget_43_prism,
            R.drawable.widget_44_sandstorm,
            R.drawable.widget_45_moonwalk,
            R.drawable.widget_46_genie_lamp,
            R.drawable.widget_47_roller_coaster,
            R.drawable.widget_48_compass,
            R.drawable.widget_49_kite,
            R.drawable.widget_50_curtain_call,
        )
        private const val HOUR_MILLIS = 60L * 60L * 1000L

        internal fun imageIndexAt(nowMillis: Long): Int =
            Math.floorMod(Math.floorDiv(nowMillis, HOUR_MILLIS), images.size.toLong()).toInt()

        /** App entry is an opportunistic refresh; with no installed tile it schedules no work. */
        fun refreshIfPresent(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LivingIconWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids, System.currentTimeMillis())
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray, nowMillis: Long) {
            if (ids.isEmpty()) return
            val openApp = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launch = PendingIntent.getActivity(
                context, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val image = images[imageIndexAt(nowMillis)]
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.living_icon_widget).apply {
                    // Render each WebP's actual alpha silhouette without a circular mask.
                    setImageViewResource(R.id.living_icon_image, image)
                    setOnClickPendingIntent(R.id.living_icon_tile, launch)
                }
                manager.updateAppWidget(id, views)
            }
        }

    }
}
