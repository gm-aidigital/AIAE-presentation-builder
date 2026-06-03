{ pkgs }: {
  deps = [
    pkgs.jdk21
    pkgs.maven
    pkgs.nodejs_22
    pkgs.postgresql_16
    pkgs.curl
    pkgs.git
    pkgs.bash
    pkgs.python3
  ];
}
