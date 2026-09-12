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

    // 0. Greetings & Pure Wake acknowledgements (always active, even when AI key is configured)
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
      text === 'ready'
    ) {
      return {
        matched: true,
        spokenResponse: 'Namaste ji! Aapki pyaari Vasu haazir hai. Bataiye, aaj aapka din kaisa chal raha hai? Aur main aapki kya madad karun?',
      };
    }

    // Conversational patterns — always active (no API key dependency)
    if (
      text.includes('kaise ho') ||
      text.includes('kaisi ho') ||
      text.includes('how are you') ||
      text.includes('kya haal')
    ) {
      return {
        matched: true,
        spokenResponse: 'Main bahut khush aur bilkul theek hoon ji! Aapke saath baat karke mera din bahut achha ho jaata hai.',
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
        spokenResponse: 'Main Vasu hoon — aapki pyaari aur samajhdaar saathi! Bataiye main aapki kya seva karun?',
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
        spokenResponse: 'Are, bahut-bahut shukriya ji! Aap mere sabse khaas dost hain, main hamesha aapke saath hoon.',
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
        spokenResponse: 'Main aapke liye phone ki torch, camera, volume, alarm, baatein karna aur aapki yaadein sahejna sab jaanti hoon ji!',
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
        spokenResponse: 'Vasu ki aawaaz bilkul sakriya hai ji! Main aapko bahut achhi tarah sun aur bol sakti hoon.',
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
        spokenResponse: 'Aapka bahut-bahut shukriya ji, aapki madad karke mujhe bahut khushi hui.',
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
        spokenResponse: 'Alvida ji! Apna khyaal rakhiyega. Jab bhi zaroorat ho, bas mujhe pukariyega.',
      };
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
        spokenResponse: `Ji, maine yeh baat pyaar se yaad rakh li hai: "${fact}"`,
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
        spokenResponse: 'Haanji, maine torch chalu kar di hai.',
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
        spokenResponse: 'Torch band kar di gayi hai ji.',
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
        spokenResponse: `Bilkul, volume ${level}% par set kar diya hai.`,
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
        spokenResponse: 'Ji, volume badha diya gaya hai.',
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
        spokenResponse: 'Ji, volume kam kar diya gaya hai.',
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
        spokenResponse: 'Main battery status check kar rahi hoon.',
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
          ? `Bilkul, WhatsApp khol kar ${recipient} ko message draft kar rahi hoon.`
          : 'Bilkul, WhatsApp khol diya hai.',
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
        spokenResponse: 'Camera khol kar photo capture kar li gayi hai.',
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
        spokenResponse: 'Accessibility service screen inspect kar rahi hai.',
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
        spokenResponse: `Bilkul, kal subah ${time} ka alarm laga diya hai.`,
      };
    }

    // 9. Files / Storage
    if (text.includes('downloads') || text.includes('files') || text.includes('folder')) {
      return {
        matched: true,
        toolId: 'browse_files',
        args: { folder: 'Downloads' },
        spokenResponse: 'Downloads folder ki files check kar rahi hoon.',
      };
    }

    if (text.includes('storage') || text.includes('memory kitni')) {
      return {
        matched: true,
        toolId: 'storage_info',
        spokenResponse: 'Device storage status check kar rahi hoon.',
      };
    }

    // 10. Notifications
    if (text.includes('notification') || text.includes('suchna')) {
      return {
        matched: true,
        toolId: 'read_notifications',
        spokenResponse: 'Notification listener se recent messages read kar rahi hoon.',
      };
    }

    // 11. Media
    if (text.includes('gaana') || text.includes('song') || text.includes('music') || text.includes('play')) {
      if (text.includes('next') || text.includes('agla')) {
        return {
          matched: true,
          toolId: 'media_next',
          spokenResponse: 'Agla gaana play kiya ja raha hai.',
        };
      }
      return {
        matched: true,
        toolId: 'media_play_pause',
        spokenResponse: 'Media playback toggle kar diya hai.',
      };
    }

    // 12. Navigation
    if (text.includes('back jao') || text.includes('go back')) {
      return {
        matched: true,
        toolId: 'press_back',
        spokenResponse: 'Back navigation execute kiya hai.',
      };
    }

    if (text.includes('home jao') || text.includes('go home')) {
      return {
        matched: true,
        toolId: 'press_home',
        spokenResponse: 'Home screen par ja rahe hain.',
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
        spokenResponse: `Ji, ${name} ko call mila rahi hoon.`,
      };
    }

    return { matched: false };
  }
}
