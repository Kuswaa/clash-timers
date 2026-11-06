{
  description = "Optional Linux shell for running the Android SDK emulator";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/1d060086d60cb76a40cf37109f979d88a5bd6396";

  outputs = { nixpkgs, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
    in {
      devShells = nixpkgs.lib.genAttrs systems (system:
        let
          pkgs = import nixpkgs { inherit system; };
          emulatorLibraries = with pkgs; [
            libx11 libxcb libxext libxi libxrender libxtst
            libxcursor libxrandr libxfixes libxkbcommon
            alsa-lib libpulseaudio nss nspr libglvnd
            zlib expat dbus libbsd libpng libdrm libxkbfile libuuid
          ];
        in {
          default = pkgs.mkShell {
            packages = [ pkgs.android-tools ];
            # Use an existing SDK; this shell neither downloads an SDK nor changes global tools.
            shellHook = ''
              export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath emulatorLibraries}''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
            '';
          };
        });
    };
}
