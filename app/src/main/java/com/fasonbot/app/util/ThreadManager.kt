package com.fasonbot.app.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object ThreadManager {

    private const val TAG = "ThreadManager"
    private const val MAX_POOL_SIZE = 10

    private val threadCounter = AtomicInteger(0)
    private val isShutdown = AtomicBoolean(false)

    private val executor = Executors.newFixedThreadPool(MAX_POOL_SIZE, object : ThreadFactory {
        override fun newThread(r: Runnable): Thread {
            return Thread {
                try {
                    r.run()
                } catch (e: Exception) {
                    Log.e(TAG, "Uncaught exception: ${e.message}", e)
                }
            }.apply {
                name = "FasonBot-Worker-${threadCounter.incrementAndGet()}"
                isDaemon = true
                setUncaughtExceptionHandler { thread, throwable ->
                    Log.e(TAG, "Thread ${thread.name} crashed: ${throwable.message}", throwable)
                }
            }
        }
    }) as ThreadPoolExecutor

    private val mainHandler = Handler(Looper.getMainLooper())

    fun runInBackground(runnable: Runnable) {
        if (isShutdown.get()) {
            Log.w(TAG, "Executor is shut down, ignoring task")
            return
        }

        try {
            executor.execute {
                try {
                    runnable.run()
                } catch (e: Exception) {
                    Log.e(TAG, "Background task error: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Submit task error: ${e.message}", e)
        }
    }

    fun runOnMainThread(runnable: Runnable) {
        try {
            mainHandler.post {
                try {
                    runnable.run()
                } catch (e: Exception) {
                    Log.e(TAG, "Main thread task error: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Post to main thread error: ${e.message}", e)
        }
    }

    fun runWithDelay(delayMs: Long, runnable: Runnable) {
        try {
            mainHandler.postDelayed({
                try {
                    runnable.run()
                } catch (e: Exception) {
                    Log.e(TAG, "Delayed task error: ${e.message}", e)
                }
            }, delayMs)
        } catch (e: Exception) {
            Log.e(TAG, "Schedule delayed task error: ${e.message}", e)
        }
    }

    fun shutdown() {
        if (isShutdown.getAndSet(true)) return

        try {
            executor.shutdown()
            Log.d(TAG, "Thread manager shut down")
        } catch (e: Exception) {
            Log.e(TAG, "Shutdown error: ${e.message}", e)
        }
    }

    fun getActiveThreadCount(): Int = executor.activeCount

    fun getQueueSize(): Int = executor.queue.size
}
