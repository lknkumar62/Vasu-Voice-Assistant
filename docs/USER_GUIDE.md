# VASU User Guide

VASU is a voice-first assistant for Android. Aap type karke ya bolke
commands de sakte ho — calls, messages, reminders, device control aur
bahut kuch. Ye guide install se lekar daily use tak sab cover karta hai.

## 1. Install and First Launch

- APK install karo aur app kholo. Home screen par VASU orb dikhega.
- Status pill batata hai VASU kya kar raha hai: Ready, Listening,
  Thinking, Speaking, Awake.
- Pehle Settings me Gemini API key add karo (Settings > AI Engine),
  warna VASU offline mode me limited rahega.
- Phir Permissions Center kholo (Home > Permissions) aur neeche
  Section 3 ke permissions grant karo.

## 2. Home Screen — 10 Actions

Bottom bar me 5 tabs hain: Home, Chat, Tools, Settings, Voice.
Home ke ACTIONS list me ye 10 shortcuts hain:

- **Chat** — VASU se text me baat karo.
- **Voice** — spoken commands ke liye Voice Mode kholo.
- **Guardian** — voice unlock / speaker verification setup.
- **Permissions** — access control, kaunsa permission granted hai.
- **Auto** — macros aur automation routines.
- **Memory** — VASU ko cheezein yaad rakhne ko bolo.
- **Tools** — available actions ki list.
- **Missions** — goals aur routines.
- **Privacy** — data aur consent settings.
- **Settings** — VASU configure karo.

## 3. Permissions — Kya Grant Karein, Kya Milega

Permissions Center me har item par tap karke system settings se
allow karo. Sabse zaroori:

- **Microphone Access** — voice commands, wake word, Guardian
  enrollment. Iske bina bolke use nahi hoga.
- **Display Over Other Apps** — "Hello Vasu" bolne par assistant
  popup dikhane ke liye.
- **Screen Control (Accessibility)** — app automation, screen reading,
  typing aur clicking ke liye.
- **Notification Access** — incoming messages padhne aur un par action
  lene ke liye.
- **Post Notifications** — background service status aur alerts ke liye.

Optional, kaam ke hisaab se:

- **Make Phone Calls** — numbers dial karne ke liye.
- **Read Contacts** — bole gaye naam ko contact se match karne ke liye.
- **Send SMS** — voice se text message bhejne ke liye.
- **Camera and Flashlight** — photo, QR scan, torch control ke liye.
- **Precise GPS Location** — nearby places, weather, navigation ke liye.

## 4. "Hello Vasu" Wake Word

- Home par **Wake Word** card par tap karke ya Settings > Security and
  Automation me **"Hello Vasu"** toggle on karo.
- Jab status **Listening** ho, bas bolo: "Hello Vasu", phir apna command.
- Agar status **Unavailable** dikhe to wake-word model is build me nahi
  hai — tab Voice tab ka button use karo.
- Dhyaan rakho: enrollment ya voice session ke time wake word pause
  rehta hai, aur khatam hone par wapas on ho jata hai.

## 5. Chat vs Voice

- **Chat tab** — type karke bhejo. Lambi queries, copy-paste, ya shaant
  jagah ke liye best. Replies padh bhi sakte ho, sun bhi sakte ho.
- **Voice tab (Voice Mode)** — bolke baat karo, hands-free use ke liye.
  Bolte time VASU **Speaking** status dikhata hai; beech me rokne ke
  liye **Stop** button dabao.
- Dono me VASU ka jawab same engine se aata hai — bas input/output ka
  style alag hai.

## 6. Guardian Enrollment (Voice Unlock)

Guardian risky actions se pehle speaker verify karta hai.
Setup ke steps:

1. Home > **Guardian** kholo.
2. **Start enrollment** dabao. Mic permission maangega — allow karo.
3. **3 short samples** record karo: mic button dabao, bolo (kam se kam
   1 second, zyada se zyada 5 second), phir half-second pause deke
   button se stop karo. 3 dots bhare = 3 samples done.
4. Naam likho aur **role** chuno, phir **Save voice**.
5. Ab **Voice Guardian** toggle on karo. Bina enrolled voice ke ye
   toggle on nahi hoga.
6. Aur voices add karni ho to **Enroll another voice** dabao. Role
   baad me change ya voice delete bhi kar sakte ho.

Tip: shaant room me enroll karo, phone se normal doori rakho.

## 7. Restart Ke Baad Kya Hota Hai

- Enrolled voices aur Guardian toggle **encrypted save** rehte hain —
  restart se delete nahi hote.
- Lekin **active speaker reset** ho jata hai: Guardian **unverified**
  dikhayega jab tak tum dobara verify nahi karte.
- Phone restart par VASU service standby mode me wapas aati hai;
  "Hello Vasu" toggle on tha to listening dobara shuru ho jati hai.
- Risky tool use karne par VASU tumhari voice verify karne ko bolega —
  Guardian me enrolled voice se verify karo.

## 8. Settings — Kaam Ke Toggles

- **AI Engine > Enable Gemini + API Key** — cloud reasoning on/off.
- **Model Fallback** — primary model fail ho to alternate use kare.
- **Voice and Sound** — language, speech rate, pitch, volume, voice
  choice, **Test Voice** button.
- **Auto-Allow Actions** — on hai to actions bina confirmation chalenge;
  off hai to pehle puchega.
- **Voice Guard** — risky tools ke liye speaker verification zaroori.
- **"Hello Vasu"** — background listening on/off, status ke saath.
- **System Access** — Screen Control, Notification Access, App
  Permissions ke shortcuts.

## 9. Troubleshooting

- **Mic busy** ("Mic is busy with another voice session") — koi voice
  session chal rahi hai; use khatam karke retry karo.
- **Permission denied (enrollment)** — mic permission allow karo, phir
  Start enrollment dobara dabao.
- **Sample too short** — kam se kam 1 second bolo har sample me.
- **Mic was lost** — enrollment cancel karke fresh start karo.
- **Guardian risky tool rok raha hai** — pehle voice enroll karo
  (Section 6), Guardian toggle on karo, aur verify karo. Agar
  confirmation chahiye nahi to Settings me Auto-Allow dekho.
- **Wake word kaam nahi kar raha** — mic permission, Wake Word toggle,
  aur status check karo (Unavailable = model is build me nahi).
- **Kuch samajh na aaye** — Settings me status rows (Wake Word state,
  errors) dekho; wahan reason likha hota hai.

## 10. Privacy Notes

- Voiceprints **tumhare phone par encrypted** save hote hain (Android
  Keystore based) — server par upload nahi hote.
- Passwords aur OTP codes memory me **store nahi** hote; OTP Protection
  (Privacy screen) on rakhne par OTP codes chhup kar dikhte hain.
- Ek hidden **error log** (`vasu_errors.log`, app-private) diagnostics
  ke liye rakha jata hai — isme passwords, OTP values ya tokens kabhi
  likhe nahi jate, aur iski koi UI screen nahi hai.
- Permissions sirf utne do jitne chahiye; Permissions Center me kabhi
  bhi wapas revoke kar sakte ho.
