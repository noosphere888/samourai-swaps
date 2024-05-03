cd xmr-btc-swap
cargo build --release
mkdir ..\app\src\main\resources\binaries
copy target\release\asb.exe ..\app\src\main\resources\binaries\asb.exe
copy target\release\price_ticker.exe ..\app\src\main\resources\binaries\price_ticker.exe
cd ..
cargo build --release
copy target\release\atomicswap.dll app\src\main\resources\atomicswap.dll