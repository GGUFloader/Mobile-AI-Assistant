package com.example.localchatbot.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.RandomAccessFile

data class LiveResourceStats(
    val cpuUsage: Int = 0,
    val memoryUsageMb: Int = 0,
    val availableMemoryMb: Int = 0
)

data class ResourceMetrics(
    val cpuUsage: Float,
    val memoryUsageMb: Long,
    val availableMemoryMb: Long
)

class ResourceMonitor(private val context: Context) {
    
    private val _liveStats = MutableStateFlow(LiveResourceStats())
    val liveStats: StateFlow<LiveResourceStats> = _liveStats.asStateFlow()
    
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startLiveMonitoring(intervalMs: Long = 1000) {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            while (isActive) {
                val metrics = getResourceMetrics()
                _liveStats.value = LiveResourceStats(
                    cpuUsage = metrics.cpuUsage.toInt(),
                    memoryUsageMb = metrics.memoryUsageMb.toInt(),
                    availableMemoryMb = metrics.availableMemoryMb.toInt()
                )
                delay(intervalMs)
            }
        }
    }

    fun stopLiveMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    fun getResourceMetrics(): ResourceMetrics {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val availableMemory = memoryInfo.availMem / (1024 * 1024)
        
        val cpuUsage = getCpuUsage()
        
        return ResourceMetrics(
            cpuUsage = cpuUsage,
            memoryUsageMb = usedMemory,
            availableMemoryMb = availableMemory
        )
    }

    private fun getCpuUsage(): Float {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()
            
            val toks = load.split(" +".toRegex())
            val idle1 = toks[4].toLong()
            val cpu1 = toks[1].toLong() + toks[2].toLong() + toks[3].toLong() + toks[5].toLong() +
                    toks[6].toLong() + toks[7].toLong()
            
            Thread.sleep(100)
            
            val reader2 = RandomAccessFile("/proc/stat", "r")
            val load2 = reader2.readLine()
            reader2.close()
            
            val toks2 = load2.split(" +".toRegex())
            val idle2 = toks2[4].toLong()
            val cpu2 = toks2[1].toLong() + toks2[2].toLong() + toks2[3].toLong() + toks2[5].toLong() +
                    toks2[6].toLong() + toks2[7].toLong()
            
            ((cpu2 - cpu1).toFloat() / ((cpu2 + idle2) - (cpu1 + idle1))) * 100f
        } catch (e: Exception) {
            0f
        }
    }
}
