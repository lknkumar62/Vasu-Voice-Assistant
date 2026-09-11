package com.vasu.assistant.core.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

class TermuxResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val execId = intent.getIntExtra(EXTRA_EXEC_ID, -1)
        if (execId < 0) {
            Log.w(TAG, "Invalid execution ID")
            return
        }

        val resultBundle = intent.getBundleExtra(EXTRA_RESULT)
        val output = resultBundle?.getString(RESULT_OUTPUT) ?: ""
        val error = resultBundle?.getString(RESULT_ERROR) ?: ""
        val exitCode = resultBundle?.getInt(RESULT_EXIT_CODE, -1) ?: -1

        Log.d(TAG, "Received result for exec #$execId: exit=$exitCode")

        TermuxManager.onCommandResult(execId, output, error, exitCode)
    }

    companion object {
        private const val TAG = "TermuxResultReceiver"
        const val EXTRA_EXEC_ID = "vasu_exec_id"
        const val EXTRA_RESULT = "result"
        const val RESULT_OUTPUT = "output"
        const val RESULT_ERROR = "error"
        const val RESULT_EXIT_CODE = "exit_code"
    }
}
