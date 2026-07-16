package com.tvworker.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.tvworker.BuildConfig
import com.tvworker.TvWorkerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AdbAutoApproveService : AccessibilityService() {

    companion object {
        private const val TAG = "AdbAutoApprove"

        // Only these packages can raise the "Allow USB debugging?" dialog.
        // Mirrored in accessibility_config.xml (android:packageNames) so the
        // framework never even delivers us events from other apps.
        private val TARGET_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.shell",
            "com.google.android.tvlauncher",
            "com.android.tv.settings"
        )

        // Title patterns — must be specific to the auth dialog question
        private val DIALOG_PATTERNS = listOf(
            "izinkan debugging usb?",
            "izinkan debug usb?",
            "allow usb debugging?",
            "allow wireless debugging?",
            "izinkan debugging usb",
            "izinkan debug usb",
            "allow usb debugging",
            "allow wireless debugging"
        )

        private val ALLOW_PATTERNS = listOf(
            "izinkan", "allow", "ok", "ya", "yes"
        )

        private val ALWAYS_ALLOW_PATTERNS = listOf(
            "selalu izinkan dari komputer ini",
            "selalu izinkan",
            "always allow from this computer",
            "always allow"
        )

        private const val DEBOUNCE_MS = 500L

        // The "Always allow" tick mutates the tree asynchronously; give the UI
        // a moment to settle before we re-read it and click "Allow".
        private const val CLICK_DELAY_MS = 150L

        // Button labels are short ("IZINKAN", "ALLOW"); anything longer is
        // almost certainly the dialog body, not a button.
        private const val MAX_BUTTON_TEXT_LEN = 20

        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastProcessedTime = 0L

    // Cached copy of the user's auto-approve preference so onAccessibilityEvent
    // (main thread) can read it synchronously without blocking on DataStore.
    @Volatile
    private var autoApproveEnabled = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        // Ensure the resilient foreground process is up whenever accessibility
        // binds — this also covers the post-reboot re-bind path.
        TvWorkerService.start(this)

        // Keep the cached flag in sync with the DataStore preference.
        serviceScope.launch {
            try {
                TvWorkerApp.instance.repository.autoApproveEnabled.collect { enabled ->
                    autoApproveEnabled = enabled
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to observe auto-approve preference", e)
            }
        }

        Log.i(TAG, "Service connected — accessibility service is active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Skip events from packages that never show the ADB auth dialog.
        // This avoids full recursive tree scans on unrelated UI churn.
        if (packageName !in TARGET_PACKAGES) return

        // Debounce — skip if we just processed an event
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < DEBOUNCE_MS) return

        val rootNode = rootInActiveWindow ?: return

        // Check for ADB dialog (title + "always allow" checkbox both present)
        if (!isAdbDialog(rootNode)) return

        lastProcessedTime = now
        Log.i(TAG, "ADB dialog detected from $packageName (event=$eventType)")

        // Log node tree only in debug builds — it dumps the full UI hierarchy.
        if (BuildConfig.DEBUG) dumpNodeTree(rootNode, 0)

        processDialog(packageName)
    }

    private fun processDialog(packageName: String) {
        // Respect the user's auto-approve toggle.
        if (!autoApproveEnabled) {
            Log.i(TAG, "Auto-approve disabled — leaving dialog untouched")
            logEvent(packageName, "ignored")
            return
        }

        val root = rootInActiveWindow ?: return
        tickAlwaysAllow(root)

        // Re-read the tree after the checkbox tick has had time to apply,
        // then click Allow and record the *actual* outcome.
        mainHandler.postDelayed({
            val freshRoot = rootInActiveWindow ?: return@postDelayed
            val clicked = clickAllow(freshRoot)
            logEvent(packageName, if (clicked) "approved" else "denied")
        }, CLICK_DELAY_MS)
    }

    private fun logEvent(packageName: String, action: String) {
        serviceScope.launch {
            try {
                TvWorkerApp.instance.repository.logApproval(packageName, action)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log '$action' event", e)
            }
        }
    }

    private fun dumpNodeTree(node: AccessibilityNodeInfo, depth: Int) {
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val cls = node.className?.toString() ?: ""
        val clickable = if (node.isClickable) " [clickable]" else ""
        val checkable = if (node.isCheckable) " [checkable]" else ""
        val checked = if (node.isChecked) " [checked]" else ""

        if (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable || node.isCheckable) {
            Log.d(TAG, "${indent}$cls text='$text' desc='$desc'$clickable$checkable$checked")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNodeTree(child, depth + 1)
        }
    }

    private fun isAdbDialog(root: AccessibilityNodeInfo): Boolean {
        // Must have BOTH the dialog title AND the "always allow" checkbox
        // This prevents false positives on Developer Options screen
        val hasTitle = findByText(root, DIALOG_PATTERNS) != null
        val hasCheckbox = findByText(root, ALWAYS_ALLOW_PATTERNS) != null
        return hasTitle && hasCheckbox
    }

    private fun tickAlwaysAllow(root: AccessibilityNodeInfo) {
        val node = findByText(root, ALWAYS_ALLOW_PATTERNS) ?: return
        val checkable = findCheckableParent(node) ?: node

        if (checkable.isCheckable && !checkable.isChecked) {
            checkable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "Checked 'Always allow'")
        } else if (!checkable.isCheckable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "Clicked 'Always allow' text")
        }
    }

    /**
     * Finds and clicks the "Allow" button.
     * @return true if a click action was successfully dispatched.
     */
    private fun clickAllow(root: AccessibilityNodeInfo): Boolean {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        findAllByText(root, ALLOW_PATTERNS, candidates)

        // Drop candidates that are actually the dialog title or the "always
        // allow" checkbox — the loose ALLOW_PATTERNS ("izinkan"/"allow") also
        // match those, and clicking them would be wrong.
        val filtered = candidates.filter { node ->
            val combined = buildString {
                append(node.text?.toString()?.lowercase() ?: "")
                append(' ')
                append(node.contentDescription?.toString()?.lowercase() ?: "")
            }
            DIALOG_PATTERNS.none { combined.contains(it) } &&
                ALWAYS_ALLOW_PATTERNS.none { combined.contains(it) }
        }
        val pool = filtered.ifEmpty { candidates }

        Log.d(TAG, "clickAllow: ${candidates.size} candidates, ${pool.size} after filtering")
        pool.forEachIndexed { i, node ->
            val cls = node.className?.toString() ?: ""
            Log.d(TAG, "  candidate[$i]: text='${node.text}' class=$cls clickable=${node.isClickable}")
        }

        // Strategy 1: a real Button widget
        var button = pool.firstOrNull { node ->
            node.className?.toString()?.lowercase()?.contains("button") == true
        }

        // Strategy 2: a clickable node with short (button-like) text
        if (button == null) {
            button = pool.firstOrNull { node ->
                val textLen = node.text?.length ?: 0
                val isClickableNode = node.isClickable || findClickableParent(node) != null
                isClickableNode && textLen < MAX_BUTTON_TEXT_LEN
            }
        }

        // Strategy 3: fall back to any remaining candidate
        if (button == null) {
            button = pool.firstOrNull()
        }

        if (button == null) {
            Log.w(TAG, "No 'Allow' button found at all")
            return false
        }

        val clickable = if (button.isClickable) button else (findClickableParent(button) ?: button)
        val result = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "Clicked 'Allow': text='${button.text}', class=${button.className}, result=$result")
        return result
    }

    private fun findAllByText(
        node: AccessibilityNodeInfo,
        patterns: List<String>,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (p in patterns) {
            if (text.contains(p) || desc.contains(p)) {
                results.add(node)
                break
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val before = results.size
            findAllByText(child, patterns, results)
            // Release subtrees that contributed no matches (no-op on API 33+).
            if (results.size == before) child.recycle()
        }
    }

    private fun findByText(
        node: AccessibilityNodeInfo,
        patterns: List<String>
    ): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (p in patterns) {
            if (text.contains(p) || desc.contains(p)) return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByText(child, patterns)
            if (found != null) return found
            // No match in this subtree — release it (no-op on API 33+).
            child.recycle()
        }
        return null
    }

    private fun findCheckableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node.parent
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isCheckable) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var current = node.parent
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        Log.i(TAG, "Service destroyed")
    }
}
