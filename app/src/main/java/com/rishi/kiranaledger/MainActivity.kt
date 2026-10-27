package com.rishi.kiranaledger

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rishi.kiranaledger.data.LedgerDatabase
import com.rishi.kiranaledger.ui.KiranaApp
import com.rishi.kiranaledger.ui.KiranaTheme
import com.rishi.kiranaledger.ui.LockScreen

// AppCompatActivity (not ComponentActivity) so the per-app language works on Android 8–12 too,
// and because the fingerprint prompt needs a FragmentActivity.
class MainActivity : AppCompatActivity() {

    private var locked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 always draws apps edge to edge; doing it on every version (and padding for the
        // status bar, navigation bar and keyboard below) makes the screens look and behave the same everywhere.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Must run after super.onCreate: before that AppCompat has no active delegate and ignores the call.
        AppLanguage.applyDefaultIfUnset()
        locked = AppLock.needsUnlock(this, alreadyUnlocked = false)
        val db = LedgerDatabase.get(this)
        setContent {
            KiranaTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    KiranaApp(db.ledgerDao(), db.billDao(), db.expenseDao(), db.stockDao(), db.reportDao())
                    if (locked) LockScreen(onUnlock = ::unlock)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (AppLock.needsUnlock(this, alreadyUnlocked = !locked)) locked = true
    }

    override fun onStop() {
        super.onStop()
        AppLock.onBackground()
    }

    private fun unlock() {
        AppLock.prompt(this, getString(R.string.locked)) { locked = false }
    }
}
