package com.lume.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * O Android trata KEYCODE_POWER antes de qualquer app, então o botão power do controle foi remapeado
 * para F11 (/data/system/devices/keylayout, ver scripts/power-key.sh). Este serviço de acessibilidade
 * existe só para capturar esse F11 em qualquer tela e abrir o menu de desligar.
 * Quem o liga é o próprio Lume, ao abrir a tela inicial (HomeActivity.enableKeyService).
 */
class KeyService : AccessibilityService() {
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_F11) return false
        if (e.action == KeyEvent.ACTION_UP) {
            startActivity(Intent(this, PowerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return true
    }
}
