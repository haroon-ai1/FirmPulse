package io.github.haroonjadoon.firmscope

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.util.concurrent.Executors
import java.util.concurrent.Future

class WatchJob : JobService() {
    private val worker=Executors.newSingleThreadExecutor()
    private var task: Future<*>?=null
    override fun onStartJob(params: JobParameters): Boolean {
        val store=FirmwareStore(this)
        if(!store.prefs.getBoolean("alerts",false)) return false
        if(store.prefs.getBoolean("wifiOnly",true)) {
            val network=getSystemService(ConnectivityManager::class.java)
            if(network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)!=true) return false
        }
        task=worker.submit {
            try {
                val deadline=System.currentTimeMillis()+360000
                for(key in store.watched().sorted()) {
                    if(Thread.currentThread().isInterrupted || System.currentTimeMillis()>deadline) break
                    val snapshot=store.fetch(store.device(key))
                    if(Thread.currentThread().isInterrupted) break
                    val changed=store.remember(snapshot)
                    if(changed) notifyChange(key)
                }
            } finally { if(!Thread.currentThread().isInterrupted) jobFinished(params,false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(true);return true }
    override fun onDestroy() { task?.cancel(true);worker.shutdownNow();super.onDestroy() }
    private fun notifyChange(key: String) {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("firmware_changes","Firmware changes",NotificationManager.IMPORTANCE_DEFAULT))
        val open=Intent(this,MainActivity::class.java).putExtra("device",key).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending=PendingIntent.getActivity(this,key.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(key.hashCode(),Notification.Builder(this,"firmware_changes")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Samsung's firmware list changed")
            .setContentText(key.replace(":"," / ")+" · Open FirmPulse for details")
            .setContentIntent(pending).setAutoCancel(true).build())
    }
    companion object {
        fun schedule(context: Context): Boolean {
            val prefs=FirmwareStore(context).prefs
            val jobs=context.getSystemService(JobScheduler::class.java)
            if(!prefs.getBoolean("alerts",false)) { jobs.cancel(4103);return true }
            val hours=prefs.getInt("interval",6).coerceIn(1,24)
            val spec=JobInfo.Builder(4103,ComponentName(context,WatchJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setPeriodic(hours*3600000L).build()
            return jobs.schedule(spec)==JobScheduler.RESULT_SUCCESS
        }
    }
}
