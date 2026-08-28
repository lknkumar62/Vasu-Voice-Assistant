package com.vasu.assistant.ui.privacy

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PrivacyViewModel @Inject constructor() : ViewModel() {
    // Privacy state managed directly in PrivacyScreen via Compose remember
    // This ViewModel can be extended for data deletion, permission request flows, etc.
}
