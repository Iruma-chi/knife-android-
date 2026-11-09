fn main() {
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() != Ok("android") {
        return;
    }
    let root = std::path::PathBuf::from("../../app/src/main/cpp/nximport");
    for file in ["nximport.cpp", "nxcrypto.cpp", "nxgame_c.cpp", "nxgame.h", "nximport.h", "nxcrypto.h", "nxgame_c.h"] {
        println!("cargo:rerun-if-changed={}", root.join(file).display());
    }

    // Compile the package reader and its C ABI into libmain.so. The Android JNI shim
    // is built separately as libpackagejni.so and links one-way against libmain.so.
    cc::Build::new()
        .cpp(true)
        .std("c++20")
        .file(root.join("nximport.cpp"))
        .file(root.join("nxcrypto.cpp"))
        .file(root.join("nxgame_c.cpp"))
        .include(&root)
        .flag_if_supported("-fexceptions")
        .flag_if_supported("-frtti")
        .flag_if_supported("-Wall")
        .flag_if_supported("-Wextra")
        .compile("tsuki_nxgame");
}
