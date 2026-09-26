package app.termaff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.termaff.ssh.Sessions
import app.termaff.ui.ConnectScreen
import app.termaff.ui.TerminalScreen
import app.termaff.ui.TermaffTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TermaffTheme {
                val session = Sessions.current
                if (session == null) {
                    ConnectScreen(onConnect = { Sessions.open(it) })
                } else {
                    TerminalScreen(
                        session = session,
                        onBack = Sessions::closeCurrent,
                        onReconnect = { Sessions.open(session.target) },
                    )
                }
            }
        }
    }
}
