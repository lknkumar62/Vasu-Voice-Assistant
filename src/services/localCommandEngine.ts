export interface LocalCommandMatch {
  matched: boolean;
  toolId?: string;
  args?: Record<string, any>;
  spokenResponse?: string;
  isMemoryCommand?: boolean;
  memoryFact?: string;
}

export class LocalCommandEngine {
  public static parse(input: string, options?: { allowConversationalMatches?: boolean }): LocalCommandMatch {
    let text = input.trim().toLowerCase();

    // Strip leading wake phrases so commands like "hello vasu torch on karo" match cleanly
    text = text
      .replace(/^(?:hello|hey|hi|namaste|ok|okay)\s+vasu\s*[,.]*\s*/i, '')
      .replace(/^vasu\s*[,.]*\s*/i, '')
      .trim();

    // 0. Greetings & Pure Wake acknowledgements — only when no Gemini key (allowConversationalMatches)
    if (options?.allowConversationalMatches !== false) {
      if (
        !text ||
        text === 'hello' ||
        text === 'hi' ||
        text === 'hey' ||
        text === 'namaste' ||
        text === 'pranam' ||
        text === 'kaho' ||
        text === 'sun rahi ho' ||
        text === 'are you listening' ||
        text === 'sunoo' ||
        text === 'sun' ||
        text === 'bolo' ||
        text === 'haan' ||
        text === 'ji' ||
        text === 'yes' ||
        text === 'ok' ||
        text === 'okay'
      ) {
        return {
          matched: true,
          spokenResponse: 'Namaste! Main aapki Vasu hoon. Bataiye, aaj aapka din kaisa hai? Main aapki kya madad kar sakti hoon?',
        };
      }
    }

    // Conversational patterns — only when no Gemini key (allowConversationalMatches)
    // When Gemini key is configured, these should go to Gemini for context-aware responses
    if (options?.allowConversationalMatches !== false) {
      if (
        text.includes('kaise ho') ||
        text.includes('kaisi ho') ||
        text.includes('how are you') ||
        text.includes('kya haal')
      ) {
        return {
          matched: true,
          spokenResponse: 'I am doing great and feeling wonderful! Talking to you always makes my day brighter. Thank you for asking!',
        };
      }

      if (
        text.includes('kaun ho') ||
        text.includes('who are you') ||
        text.includes('aapka naam') ||
        text.includes('tumhara naam')
      ) {
        return {
          matched: true,
          spokenResponse: 'I am Vasu — your affectionate and smart AI companion! Tell me, how may I assist you today?',
        };
      }

      if (
        text.includes('pyar') ||
        text.includes('love') ||
        text.includes('pari') ||
        text.includes('dost') ||
        text.includes('friend')
      ) {
        return {
          matched: true,
          spokenResponse: 'Oh, thank you so much! You are my most special friend, and I am always here for you, no matter what.',
        };
      }

      if (
        text.includes('kya kar sakti ho') ||
        text.includes('what can you do') ||
        text.includes('features kya') ||
        text.includes('madad karo') ||
        text === 'help'
      ) {
        return {
          matched: true,
          spokenResponse: 'I can help you with your phone torch, camera, volume control, setting alarms, having conversations, and saving your important memories. Just ask me anything!',
        };
      }

      if (
        text.includes('voice nahi') ||
        text.includes('aawaz nahi') ||
        text.includes('sound check') ||
        text.includes('aawaz check') ||
        text.includes('bol nahi rahi')
      ) {
        return {
          matched: true,
          spokenResponse: 'My voice is working perfectly fine! I can hear you and speak to you very clearly. Everything is in order!',
        };
      }

      if (
        text.includes('dhanyawad') ||
        text.includes('shukriya') ||
        text.includes('thank you') ||
        text.includes('thanks')
      ) {
        return {
          matched: true,
          spokenResponse: 'You are very welcome! It was my pleasure to help you. I am always here whenever you need me.',
        };
      }

      if (
        text === 'bye' ||
        text === 'alvida' ||
        text.includes('good bye') ||
        text.includes('goodbye')
      ) {
        return {
          matched: true,
          spokenResponse: 'Goodbye! Take good care of yourself. Whenever you need me, just call and I will be right here for you.',
        };
      }
    }

    // 1. Memory commands ("Yaad rakhna...", "Remember that...")
    if (
      text.startsWith('yaad rakhna') ||
      text.startsWith('yaad rakho') ||
      text.startsWith('remember that') ||
      text.startsWith('remember ')
    ) {
      const fact = text
        .replace(/^yaad rakhna ki |^yaad rakhna |^yaad rakho ki |^yaad rakho |^remember that |^remember /i, '')
        .trim();
      return {
        matched: true,
        isMemoryCommand: true,
        memoryFact: fact,
        spokenResponse: `Of course! I have carefully remembered this for you: "${fact}"`,
      };
    }

    // 2. Torch / Flashlight
    if (
      text.includes('torch on') ||
      text.includes('torch jalao') ||
      text.includes('flashlight on') ||
      text.includes('torch chalu') ||
      text.includes('light on')
    ) {
      return {
        matched: true,
        toolId: 'turn_on_torch',
        spokenResponse: 'Yes, I have turned on the flashlight for you. It is all set!',
      };
    }

    if (
      text.includes('torch off') ||
      text.includes('torch band') ||
      text.includes('flashlight off') ||
      text.includes('torch bujhao') ||
      text.includes('light off')
    ) {
      return {
        matched: true,
        toolId: 'turn_off_torch',
        spokenResponse: 'The flashlight has been turned off. All done!',
      };
    }

    // 3. Volume controls
    const volumeMatch = text.match(/volume (\d+)\s*(?:percent|%|pe|par)?/);
    if (volumeMatch) {
      const level = parseInt(volumeMatch[1], 10);
      return {
        matched: true,
        toolId: 'set_volume',
        args: { level },
        spokenResponse: `Sure thing! The volume has been set to ${level} percent for you.`,
      };
    }

    if (
      text.includes('volume up') ||
      text.includes('volume badhao') ||
      text.includes('aawaz badhao') ||
      text.includes('sound up')
    ) {
      return {
        matched: true,
        toolId: 'volume_up',
        spokenResponse: 'Volume has been increased for you. Done!',
      };
    }

    if (
      text.includes('volume down') ||
      text.includes('volume kam karo') ||
      text.includes('aawaz kam karo') ||
      text.includes('sound down')
    ) {
      return {
        matched: true,
        toolId: 'volume_down',
        spokenResponse: 'Volume has been turned down. All set!',
      };
    }

    // 4. Battery info
    if (
      text.includes('battery') ||
      text.includes('charging') ||
      text.includes('battery kitni')
    ) {
      return {
        matched: true,
        toolId: 'battery_info',
        spokenResponse: 'Let me check the battery status for you right now.',
      };
    }

    // 5. WhatsApp
    if (text.includes('whatsapp')) {
      const recipientMatch = text.match(/(?:whatsapp kholo aur|whatsapp par|whatsapp pe)?\s*([a-zA-Z]+)\s*(?:ko message|ko msg)/i);
      const recipient = recipientMatch ? recipientMatch[1] : undefined;
      return {
        matched: true,
        toolId: 'open_whatsapp',
        args: { recipient, message: 'Namaste' },
        spokenResponse: recipient
          ? `Opening WhatsApp and drafting a message for ${recipient} right away.`
          : 'Opening WhatsApp for you. All done!',
      };
    }

    // 6. Camera / Photos
    if (
      text.includes('camera') ||
      text.includes('photo lo') ||
      text.includes('photo kheecho') ||
      text.includes('take photo')
    ) {
      return {
        matched: true,
        toolId: 'take_photo',
        spokenResponse: 'Camera has been opened and the photo has been captured successfully!',
      };
    }

    // 7. Screen Reader (Accessibility)
    if (
      text.includes('screen pe kya') ||
      text.includes('screen padho') ||
      text.includes('read screen') ||
      text.includes('display pe kya')
    ) {
      return {
        matched: true,
        toolId: 'read_screen',
        spokenResponse: 'The accessibility service is now inspecting the screen content for you.',
      };
    }

    // 8. Alarm
    if (text.includes('alarm') || text.includes('uthana')) {
      let time = '07:00 AM';
      const timeMatch = text.match(/(\d+)(?:\s*baje|\s*am|\s*pm)/);
      if (timeMatch) {
        time = `${timeMatch[1].padStart(2, '0')}:00 AM`;
      }
      return {
        matched: true,
        toolId: 'create_alarm',
        args: { time },
        spokenResponse: `Alarm has been set for ${time}. You will be reminded on time!`,
      };
    }

    // 9. Files / Storage
    if (text.includes('downloads') || text.includes('files') || text.includes('folder')) {
      return {
        matched: true,
        toolId: 'browse_files',
        args: { folder: 'Downloads' },
        spokenResponse: 'Opening the Downloads folder and checking the files for you.',
      };
    }

    if (text.includes('storage') || text.includes('memory kitni')) {
      return {
        matched: true,
        toolId: 'storage_info',
        spokenResponse: 'Checking your device storage status right now.',
      };
    }

    // 10. Notifications
    if (text.includes('notification') || text.includes('suchna')) {
      return {
        matched: true,
        toolId: 'read_notifications',
        spokenResponse: 'Reading your recent notifications now. Let me see what you have got.',
      };
    }

    // 11. Media
    if (text.includes('gaana') || text.includes('song') || text.includes('music') || text.includes('play')) {
      if (text.includes('next') || text.includes('agla')) {
        return {
          matched: true,
          toolId: 'media_next',
          spokenResponse: 'Playing the next song for you now!',
        };
      }
      return {
        matched: true,
        toolId: 'media_play_pause',
        spokenResponse: 'Media playback has been toggled. Enjoy your music!',
      };
    }

    // 12. Navigation
    if (text.includes('back jao') || text.includes('go back')) {
      return {
        matched: true,
        toolId: 'press_back',
        spokenResponse: 'Going back for you right now.',
      };
    }

    if (text.includes('home jao') || text.includes('go home')) {
      return {
        matched: true,
        toolId: 'press_home',
        spokenResponse: 'Taking you to the home screen now.',
      };
    }

    // 13. Calls
    if (text.includes('call') || text.includes('phone mila')) {
      const nameMatch = text.match(/([a-zA-Z]+)\s*ko call/i);
      const name = nameMatch ? nameMatch[1] : 'Rahul';
      return {
        matched: true,
        toolId: 'make_call',
        args: { phoneNumber: '+91 98765 43210' },
        spokenResponse: `Calling ${name} for you right now. Please hold on.`,
      };
    }

    return { matched: false };
  }
}
