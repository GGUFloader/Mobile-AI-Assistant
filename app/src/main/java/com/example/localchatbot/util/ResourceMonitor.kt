package com.example.localchatbot.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.RandomAccessFile

data class LiveResourceStats(
    val cpuUsage: Int = 0,
    val memoryUsageMb: Int = 0,
    val availableMemoryMb: Int = 0,
    val nativeHeapMb: Int = 0
)

data class ResourceMetrics(
    val cpuUsage: Float,
    val memoryUsageMb: Long,
    val availableMemoryMb: Long,
    val nativeHeapMb: Long
)

class ResourceMonitor(private val context: Context) {
    
    private val _liveStats = MutableStateFlow(LiveResourceStats())
    val liveStats: StateFlow<LiveResourceStats> = _liveStats.asStateFlow()
    
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // For CPU calculation using process time
    private var lastCpuTime: Long = 0
    private var lastUpdateTime: Long = 0

    fun startLiveMonitoring(intervalMs: Long = 1000) {
        monitoringJob?.cancel()
        // Initialize CPU tracking
        lastCpuTime = Process.getElapsedCpuTime()
        lastUpdateTime = System.currentTimeMillis()
        
        monitoringJob = scope.launch {
            while (isActive) {
                val metrics = getResourceMetrics()
                _liveStats.value = LiveResourceStats(
                    cpuUsage = metrics.cpuUsage.toInt(),
                    memoryUsageMb = metrics.memoryUsageMb.toInt(),
                    availableMemoryMb = metrics.availableMemoryMb.toInt(),
                    nativeHeapMb = metrics.nativeHeapMb.toInt()
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
        // Use simple Runtime-based memory tracking to avoid memtrack errors
        val runtime = Runtime.getRuntime()
        val appMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        
        // Native heap allocated by the app (useful for ML models)
        val nativeHeapMb = try {
            Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        } catch (e: Exception) {
            0L
        }
        
        // Available system memory
        val availableMemory = try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.availMem / (1024 * 1024)
        } catch (e: Exception) {
            0L
        }
        
        // Calculate CPU usage for this process
        val cpuUsage = getProcessCpuUsage()
        
        return ResourceMetrics(
            cpuUsage = cpuUsage,
            memoryUsageMb = appMemoryMb,
            availableMemoryMb = availableMemory,
            nativeHeapMb = nativeHeapMb
        )
    }

    /**
     * Calculate CPU usage for this process based on elapsed CPU time.
     * This measures how much CPU time our app has used relative to wall clock time.
     */
    private fun getProcessCpuUsage(): Float {
        return try {
            val currentCpuTime = Process.getElapsedCpuTime() // in milliseconds
            val currentTime = System.currentTimeMillis()
            
            val cpuTimeDelta = currentCpuTime - lastCpuTime
            val timeDelta = currentTime - lastUpdateTime
            
            // Update for next calculation
            lastCpuTime = currentCpuTime
            lastUpdateTime = currentTime
            
            if (timeDelta > 0) {
                // CPU usage as percentage of wall clock time
                // Multiply by number of cores to get relative usage
                val numCores = Runtime.getRuntime().availableProcessors()
                val usage = (cpuTimeDelta.toFloat() / timeDelta.toFloat()) * 100f
                // Cap at 100% per core, show total across cores
                minOf(usage, 100f * numCores)
            } else {
                0f
            }
        } catch (e: Exception) {
            0f
        }
    }
}
