A GUI for COMIT XMR-BTC atomic swaps with modifications to further enhance anonymity, with the Automated Swap Backend (ASB) built-in, as well as Samourai Wallet Whirlpool for automatic mixing of redeemed BTC.

Learn more about using the GUI [here](./docs/BUY_XMR.md).

Learn more about selling XMR by running the Automated Swap Backend [here](./docs/SELL_XMR.md).

Learn more about configuring the settings [here](./docs/SETTINGS.md).

Learn more about swaps architecture [here](./docs/SWAPS.md).

[FAQ](./docs/FAQ.md)

[Releases](https://code.samourai.io/wallet/comit-swaps-java/-/releases)

# Known Issues/Bugs
- "Error deserializing message using CBOR" when attempting to start a swap: This might be an issue with the Bitcoin Dev Kit library, or some other dependency when attempting to communicate with the peer.
You don't need to panic. The Bitcoin you deposited is still in the "Swaps main account" (listed further down in this file), and the program will attempt to retry. Right now there is no limit to the amount of retries.
*Just leave the program running.*

# Build & Run

### Requirements
- Java 17-21 (If you wish to compile on Apple Silicon, make sure to install an aarch64 version of JDK. Java 22 is not yet supported by Gradle: https://docs.gradle.org/current/userguide/compatibility.html)
- Rust (easily install via https://rustup.rs)

### Preparation

Run
```bash
git submodule update --init
```
and then run the following script in the `scripts/prepare` folder from the root of the project.

Linux:
```bash
./scripts/prepare/prepare-linux.sh
```
macOS:
```bash
./scripts/prepare/prepare-macos.sh
```
Windows:
```bash
./scripts/prepare/prepare-windows.bat
```

### Building/Running

#### IntelliJ with Rust plugin:
Import `comit-swaps-java` into IntelliJ as a Cargo project.
You will then be prompted within IntelliJ to also open the Gradle project. Do that then run:
```bash
cargo build
```
This should generate a `libatomicswap.so`/`libatomicswap.dylib`/`atomicswap.dll` file (depending on your operating system) in `target/debug`

Set up a Run configuration in IntelliJ to run `swap.gui.Main`.

#### IntelliJ without Rust plugin (behind Ultimate Edition paywall):
You can simply install Rust via rustup.rs as noted above, and run `cargo build` within the root folder of this project while in IntelliJ, or install the new RustRover IDE from Jet Brains website, then follow the instructions below:

Import `comit-swaps-java` into RustRover as a Cargo project, then run:
```bash
cargo build
```
This should generate a `libatomicswap.so`/`libatomicswap.dylib`/`atomicswap.dll` file (depending on your operating system) in `target/debug`

Import `comit-swaps-java` into IntelliJ as a Gradle project.

Set up a Run configuration in IntelliJ to run `swap.gui.Main`.

#### Notes
There are also scripts to package the resulting native library (`libatomicswap.so` or similarly named) and jar file into an executable for that specific operating system using JDK's `jpackage` program, located in the
`scripts/package` folder of this project:
```
package-linux.sh
package-windows.bat
```
Currently, jpackage does not seem to work for macOS, so the `package-macos.sh` script creates a script to launch the program.

It is recommended to run these scripts from the root of the project, like so:
```bash
./scripts/package/package-linux.sh
```

This program has been tested on:
- Linux (Arch, Debian, Pop!_OS, Ubuntu)
- macOS (Intel, Apple Silicon)
- Windows 10

### Pairing to Samourai Wallet

The GUI must be paired to your Samourai Wallet. The procedure is the same as for pairing the Whirlpool GUI/CLI.

The Swaps GUI will point to the same bitcoin network as the payload exported from your wallet: mainnet or testnet. A GUI paired to a Samourai mainnet Wallet mainnet will swap with Monero mainnet. A GUI paired to a Samourai testnet Wallet mainnet will swap with Monero stagenet.

1. Go to Settings -> Transactions -> Pair to Swaps GUI in Samourai Wallet.
2. The pairing payload will be displayed. It contains pairing information which is encrypted using your Samourai Wallet passphrase.
3. After first launch of the Samourai Swaps GUI paste the pairing payload, type your wallet passphrase in the entry field below and confirm.
4. Wait until the pairing process is finished. It will take a few minutes.

### Samourai Wallet bitcoin swaps accounts

Mainnet:

* Swaps main account: m/84'/0'/2147483643'
* Swaps refund account: m/84'/0'/2147483642'
* ASB main account: m/84'/0'/2147483641'

Testnet:

* Swaps main account: m/84'/1'/2147483643'
* Swaps refund account: m/84'/1'/2147483642'
* ASB main account: m/84'/1'/2147483641'

# Contributing

We're always looking to polish and make improvements. Help us make Swaps better!

1. Fork it (`git clone https://code.samourai.io/wallet/comit-swaps-java.git`)
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create new Merge Request

[How to Fork an Open Source Project](https://bitcoiner.guide/fork/)

# License

[GNU General Public License 3](https://code.samourai.io/wallet/comit-swaps-java/-/blob/master/LICENSE)


