# Tsukihime Android 0.1.3 (development)
🌙 Tsukihime — Android Port

Bringing the world of Tsukihime to Android.

An independent Android ARM64 port of "Knife" (https://github.com/AuroraWright/knife), a Rust reimplementation of the game engine for Tsukihime -A piece of blue glass moon-.

This project aims to bring the engine to Android devices through a native application, without relying on traditional console emulation. It adapts the original engine for Android while preserving the game's presentation and experience as faithfully as possible.

---

✦ Project Highlights

- Native Android ARM64 — Runs as an Android application rather than through a conventional emulator.
- Rust-powered engine — Built upon the original Knife engine.
- Touchscreen Controls — Customizable on-screen controls designed for mobile gameplay.
- Backlog Navigation — Dedicated scrolling controls for navigating dialogue history.
- Asset Selection — Android-friendly interface for accessing your game assets.
- Save Management — Save backup and restoration support.
- Control Customization — Adjust button visibility, positioning, size, and opacity.

📱 Compatibility

Requirement| Details
Platform| Android
Architecture| ARM64-v8a
Engine| Knife (Rust)
Distribution| GitHub Releases

📦 Download

Get the latest APK from the "GitHub Releases page" (https://github.com/Iruma-chi/knife-android-/releases).

---

📖 Installation Guide

Requirements

Before getting started, make sure you have:

- A legally obtained and dumped copy of the Nintendo Switch version of Tsukihime -A piece of blue glass moon-.
- A PC with a Nintendo Switch emulator capable of extracting game files through its ROMFS feature.
- The required Nintendo system font:
  "nintendo_udsg-r_std_003.bfttf"
- An Android device compatible with ARM64-v8a.

Step 1 — Extract the game assets

1. On your PC, open your Nintendo Switch emulator.

2. Locate the ROMFS extraction feature for your legally obtained game dump.

3. Use the ROMFS feature to extract the game's files.
   
   You do not need to launch or play the game in the emulator. It is only being used as an extraction tool.

4. Once extraction is complete, rename the extracted ROMFS folder to:
   
   "assets"

Step 2 — Prepare the assets folder

1. Open the newly renamed "assets" folder.
2. Locate the "stream" folder inside it.
3. Cut the entire "stream" folder and move it outside the "assets" folder temporarily.

Your folder structure should now look like this:

Tsukihime/
├── assets/
│   └── (all extracted game files except stream)
└── stream/
    └── (game stream files)

Step 3 — Compress the stream folder

1. Compress the extracted "stream" folder into a ZIP or RAR archive.
2. Keep the compressed archive outside the "assets" folder.

Why compress it?

The "stream" folder contains a large number of files. Transferring every individual file from your PC to Android can take a considerable amount of time.

Transferring one compressed archive is generally much faster and more convenient. This method can make the transfer process significantly quicker.

Step 4 — Transfer everything to your Android device

Copy the following items from your PC to any convenient folder on your Android device:

- The prepared "assets" folder.
- The compressed "stream" archive (ZIP or RAR).
- The Nintendo system font file ("nintendo_udsg-r_std_003.bfttf").

You can use USB transfer or any other convenient file-transfer method.

Step 5 — Restore the stream folder

1. On your Android device, extract the compressed "stream" archive.
2. Move the resulting "stream" folder into the "assets" folder.

Important: Make sure the final location is "assets/stream/", not "assets/stream/stream/".

Step 6 — Add the Nintendo system font

Copy the following font file directly into your "assets" folder:

nintendo_udsg-r_std_003.bfttf

Do not place it inside the "stream" folder.

Step 7 — Final folder structure

Before launching the application, make sure your assets directory resembles this:

assets/
├── stream/
│   └── (game stream files)
├── nintendo_udsg-r_std_003.bfttf
└── (other extracted game files)

Step 8 — Launch and play!

1. Install and open the Tsukihime Android port APK.
2. When prompted, select the "assets" directory you prepared.
3. Allow Android to grant the required folder access.
4. Once the assets have been recognized, you're ready to play!

Enjoy Tsukihime on Android. 🌙

---

⚙️ Project Status

This project is under active development. Although the Android port is functional, certain features, compatibility details, and optimizations may still evolve.

Bug reports, testing feedback, and constructive contributions are welcome.

🙏 Credits & Acknowledgements

- "AuroraWright" (https://github.com/AuroraWright) — Original Knife engine and foundational work.
- Type-Moon — Creators of Tsukihime -A piece of blue glass moon-.
- 

🤖 Development Disclosure

This project was developed using my own coding skills, technical decisions, experimentation, and implementation work, with AI tools assisting throughout the development process.

AI assistance was used for coding support, debugging, troubleshooting, research, and refinement. The integration, direction, decisions, and testing were carried out as part of my own development work.

---

An independent community project. Not affiliated with or endorsed by Type-Moon.
