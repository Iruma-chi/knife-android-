🌙 TsukiPort

Tsukihime, now on Android.

A native Android ARM64 port of Tsukihime, powered by the Rust-based Knife engine.

TsukiPort brings Tsukihime -A piece of blue glass moon- to Android devices without requiring a PC or an external Nintendo Switch emulator for game-data preparation.

Select your own NSP or XCI game dump, let TsukiPort handle the decryption and ROMFS extraction directly on your device, and get your game ready to play.

---

✨ What's New — Direct Game Preparation

No PC. No external emulator. No manual extraction.

TsukiPort now includes an integrated game preparation system.

You no longer need to extract ROMFS on a computer, transfer thousands of files to your phone, or configure an external emulator just to prepare your game data.

Everything happens directly on your Android device.

How it works:

1. Open TsukiPort.
2. Select your legally obtained Nintendo Switch game dump (".NSP" or ".XCI").
3. Provide the required decryption keys when prompted.
4. Optionally select the matching game update, if supported and available.
5. Start Prepare Game Data.
6. TsukiPort handles decryption and ROMFS extraction on-device.
7. Once preparation finishes, your game data is ready. Launch Tsukihime and play!

💾 Important: Storage Requirements

Game preparation requires substantial temporary storage.

Requirement| Details
Recommended free space| At least 45–50 GB
Game dump| Your own NSP or XCI
Preparation| On-device decryption and ROMFS extraction
After preparation| Original game dump can be deleted
Potential storage recovered| Approximately 23 GB, depending on the dump

Please read before starting:

- Ensure that your device has at least 45–50 GB of free storage before beginning.
- Do not interrupt the preparation process.
- Keep the original game dump until preparation completes successfully.
- Once the game data has been prepared and verified, you can delete the original NSP/XCI file to recover storage space.

The extracted game data remains available for gameplay, so you don't need to retain the original dump just to launch the game.

---

🎮 Features

- Native Android ARM64 — Designed to run directly on Android.
- Integrated NSP/XCI Importer — Select your own game dump from within the application.
- On-device Decryption — Prepare encrypted game data without an external emulator.
- Direct ROMFS Extraction — Extract the required game files on your phone.
- Optional Game Updates — Support for selecting a matching game update where available.
- Custom Touch Controls — Mobile-friendly controls with customization options.
- Backlog Navigation — Dedicated scrolling controls for dialogue history.
- Save Management — Backup and restoration functionality.
- Android File Access — Select and manage game files through Android's file picker.

---

📱 Requirements

- An Android device with ARM64-v8a support.

- A legally obtained Nintendo Switch copy of Tsukihime -A piece of blue glass moon-.

- The required Nintendo decryption keys ("prod.keys" and "title.keys", as applicable to your dump and preparation flow).

- At least 45–50 GB of available storage for initial game preparation.

- The required Nintendo system font:
  
  "nintendo_udsg-r_std_003.bfttf"

Important: TsukiPort does not include or distribute the original game, copyrighted game assets, or Nintendo decryption keys. You must supply your own legally obtained files.

---

📥 Installation

1. Visit the "GitHub Releases page" (https://github.com/Iruma-chi/knife-android-/releases).
2. Download the latest TsukiPort APK.
3. Install it on your Android device.
4. Open TsukiPort and follow the in-app game preparation instructions.
5. Select your NSP/XCI file and provide the required keys and font.
6. Start the preparation process and wait for it to complete.
7. Launch the game!

You no longer need to prepare an "assets" folder manually on your PC.

---

⚙️ Technical Information

Component| Details
Application| TsukiPort
Package| Android
Architecture| ARM64-v8a
Engine| Knife
Engine language| Rust
Game input| NSP / XCI
Game preparation| On-device
Extraction| ROMFS

---

🚧 Development Status

TsukiPort has reached a stage where the game is playable , although I haven't reached end.
The upcoming updates may happen when i have some free time.

The integrated game preparation workflow and Android port are evolving. Some features may still be experimental, and compatibility may vary depending on the game dump, update version, device hardware, and available storage.

Bug reports, testing feedback, and constructive contributions are welcome.

If you encounter an issue, please include:

- The stage at which the problem occurred.
- Relevant error messages or logs.



---

🙏 Credits & Acknowledgements

- "AuroraWright" (https://github.com/AuroraWright) — Original Knife engine and foundational work.
- "Type-Moon" (https://typemoon.com/) — Creators of Tsukihime -A piece of blue glass moon-.
- Contributors and developers whose work made this project possible.

🤖 Development Disclosure

TsukiPort was developed through my own coding skills, technical decisions, experimentation, implementation, and testing, with AI tools assisting throughout the development process.

AI assistance was used for debugging, troubleshooting, and refinement. The project's direction, integration, implementation decisions, and testing were carried out as part of my development work.

---

TsukiPort is an independent community project. It is not affiliated with, endorsed by, or sponsored by Type-Moon 
