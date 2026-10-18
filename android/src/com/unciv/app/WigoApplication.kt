package com.unciv.app

import android.app.Application
import com.unciv.logic.WigoOnlineSafety

class WigoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WigoOnlineSafety.permission = { WigoParentActivity.isOnlineAllowed() }
        WigoOnlineSafety.requestParent = {
            android.os.Handler(mainLooper).post {
                startActivity(android.content.Intent(this, WigoParentActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        WigoParentActivity.setLockListener { WigoOnlineSafety.lockConnections() }
    }
}
