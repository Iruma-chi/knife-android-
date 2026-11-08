fn main() {
    if let Err(error) = main::run_desktop() {
        eprintln!("{error:#}");
        std::process::exit(1);
    }
}
