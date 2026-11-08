package com.interviewcopilot.app.llm

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import java.io.File

/** Reads RAM, storage, CPU/GPU/NPU hints and the OS version from the phone. */
object DeviceProbe {
    fun read(ctx: Context): DeviceSpecs {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val stat = StatFs(ctx.filesDir.absolutePath)
        val cpu = readCpuInfo()
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != "unknown" }.joinToString(" ")
        } else {
            Build.HARDWARE
        }
        val vulkan = vulkanLevel(ctx.packageManager)
        return DeviceSpecs(
            totalRamMb = mi.totalMem / (1024 * 1024),
            availRamMb = mi.availMem / (1024 * 1024),
            freeStorageMb = stat.availableBytes / (1024 * 1024),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            socName = soc.ifBlank { Build.MODEL },
            osName = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            sdkInt = Build.VERSION.SDK_INT,
            hasDotProd = cpu.contains("asimddp"),
            hasI8mm = cpu.contains("i8mm"),
            vulkanLevel = vulkan,
            hasNpuHint = looksLikeNpuSoc(soc),
        )
    }

    private fun readCpuInfo(): String = try {
        File("/proc/cpuinfo").readLines().filter { it.startsWith("Features") }.joinToString(" ")
    } catch (e: Exception) {
        ""
    }

    private fun vulkanLevel(pm: PackageManager): Int =
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) 1 else 0

    private fun looksLikeNpuSoc(soc: String): Boolean {
        val s = soc.lowercase()
        return listOf("tensor", "snapdragon", "sm8", "qualcomm", "dimensity", "mt69", "exynos", "kirin").any { s.contains(it) }
    }

    fun describeCpu(d: DeviceSpecs): String {
        val isa = when {
            d.hasI8mm -> "NEON + dot-product + i8mm"
            d.hasDotProd -> "NEON + dot-product"
            else -> "NEON"
        }
        return "${d.cpuCores} cores, ${d.abi}, $isa"
    }

    fun describeGpu(d: DeviceSpecs): String = if (d.vulkanLevel > 0) "Vulkan capable" else "No Vulkan hardware"

    fun describeNpu(d: DeviceSpecs): String = if (d.hasNpuHint) "Likely present (${d.socName})" else "Not detected"
}
