package com.vasu.assistant;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.plugin.texttospeech.TextToSpeech;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.bridge.registerPlugin(TextToSpeech.class);
  }
}
