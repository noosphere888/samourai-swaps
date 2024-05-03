cd xmr-btc-swap
cargo build --release
mkdir ../app/src/main/resources/binaries
cp target/release/asb ../app/src/main/resources/binaries/asb
cp target/release/price_ticker ../app/src/main/resources/binaries/price_ticker
cd ..
cargo build --release
cp target/release/libatomicswap.so app/src/main/resources/libatomicswap.so