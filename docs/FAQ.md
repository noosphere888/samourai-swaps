# Samourai Swaps FAQs

Learn more about building and running the program [here](../README.md).

Learn more about using the GUI [here](BUY_XMR.md).

Learn more about selling XMR by running the Automated Swap Backend [here](SELL_XMR.md).

Learn more about configuring the settings [here](SETTINGS.md).

Learn more about swaps architecture [here](SWAPS.md).

## What BTC and XMR wallet do I need to use?
The Samourai Wallet mobile application is required to initialize the Samourai Swaps desktop application (which is possible in both Mainnet and Testnet mode). It is up to you which XMR wallet you use. ([Mysu](http://rk63tc3isr7so7ubl6q7kdxzzws7a7t6s467lbtw2ru3cwy6zu6w4jad.onion/) is recommended).

## Can I test with Testnet/Stagenet?
Yes. When initializing, pair with a Testnet Samourai Wallet pairing payload and it will configure the app to Testnet/Stagenet. You can even use your ASB to receive the stagenet XMR if you don't want to create a stagenet XMR wallet. ANON/NERO, Feather Wallet, & Monerujo are options to use stagenet as well.

## What is the fee to swap?
Fees are established by the free market, and those providing XMR liquidity by running an Automated Swap Backend (ASB) are free to set their own percentage fee over or under the XMR spot price (Feather Wallet's price websocket). This is set on the [Settings](SETTINGS.md) page.

## How do I buy XMR?
Read [here](BUY_XMR.md).

## How do I sell XMR?
You must run the Automated Swap Backend. Read [here](SELL_XMR.md).

# Why is the "Buy Monero" button disabled and not letting me click it?
You have to wait for Whirlpool to be initialized. 

## What is ASB?
Automated Swap Backend. It is how you become a market maker / liquidity provider in order to sell XMR.

## How does the ASB Min & Max work?
Min = Minimum quantity for a trade with your ASB. If your ASB's XMR balance's equivalent BTC value is below this amount, you will stop appearing in Rendezvous lists until you replenish your XMR wallet. The Min currently must be over 0.0005 BTC.

Max = Maximum quantity for a trade with your ASB. If your ASB's XMR balance's equivalent BTC value is below this amount, then the max amount shown in the Rendezvous lists will be that lower value.

Example: If my max BTC limit is 1 BTC in my [Settings](SETTINGS.md) tab, but I only have ~132 XMR in my ASB wallet, then the "Max BTC" in the seller's list will be ~0.5 BTC (at current spot price).

## How do I know if my ASB is showing up on the Sellers list?
This circular icon will appear in the top navigation bar.<br>
![](./files/images/asb_public.png)

## How are ASBs found?
Each client connects to Rendezvous servers (5 by default). These servers provide a list of ASBs which are online and ready to start a swap. Additional Rendezvous servers can be added on the [Settings](SETTINGS.md) page.

If you and someone else wants to trade but don't have a Rendezvous server, they can send you their ASB libp2p address (like the ones you see in the sellers list now) directly via XMPP, SimpleX, Soroban, or something similar, and you can enter it in the seller text field in the Swaps program, and start a trade like normal.


## Can I trade with ASBs which are not using the Samourai Swaps application?
Yes, there are other ASBs you may carry out a swap with who are running on the same COMIT protocol found by the Rendezvous servers.

## What could cause a trade from being interrupted?
This would most likely be caused by a drop in your internet, Tor, or XMR node connection, or if the ASB stops listening during a swap or has disconnected.

## What happens if a swap gets interrupted?
Every swap is based around the "BTC lock transaction". Once this transaction is broadcasted then confirmed on-chain, the swap between Bob (swapping BTC for XMR) and Alice (swapping XMR for BTC) has 72 blocks* to complete with a successful outcome for both parties.

If the swap is not fully completed within this 72 block period*, Bob and/or Alice will automatically broadcast the "BTC cancel transaction".

As soon as the "BTC cancel transaction" is broadcasted a new timer is started. This timer ends when the "BTC cancel transaction" has 72 on-chain confirmations**. During this period if Bob is online they will automatically broadcast the "BTC refund transaction" as soon as possible. This will:
- Refund Bob the bitcoin to their "Swaps Refund BTC account".
- Refund Alice their XMR (if the "XMR lock transaction" was broadcasted during the swap).

If 72 blocks** pass since the "BTC cancel transaction" confirmed on-chain and Bob has not broadcasted the "BTC refund transaction", Alice will broadcast the "BTC punish transaction". This will:
- Redeem to Alice the locked BTC (from Bob).
- Burn forever the XMR locked during the swap (if the "XMR lock transaction" was broadcasted by Alice).

The app handles most of this automatically and is why you want to keep the GUI running until a swap completes.

Each transaction is explained more in depth [here](BUY_XMR.md).

&ast; Testnet: 12 confirmations.<br>
** Testnet: 6 confirmations.

## What is the flow of transactions?
**Successful Path:** <br>
Transaction to SWAPS_DEPOSIT (P2WPKH address) -> LOCK transaction (P2WSH) -> REDEEM transaction to SWAPS_ASB (P2WPKH) -> Bob redeems XMR

**Unsuccessful, refund path:** <br>
SWAPS_DEPOSIT -> LOCK -> (... 72 confirmations later ...) -> CANCEL transaction (to another P2WSH UTXO) -> REFUND transaction (to SWAPS_REFUNDS, P2WPKH) -> Alice refunds her XMR

**Unsuccessful, punish path:** <br>
SWAPS_DEPOSIT -> LOCK -> (... 72 confirmations ...) -> CANCEL -> (... 72 confirmations ...) -> PUNISH transaction (to SWAPS_ASB, P2WPKH) -> XMR remains locked currently

## Does this run over Tor?
Yes. The app defaults to running over Tor.

## What is the source of the BTC?
Unknown. Individual users decide what BTC they wish to use for buying XMR. As an ASB, it is recommended to mix your received BTC in Whirlpool. Auto-Tx0 can be enabled on the [Settings](SETTINGS.md) page.

## How is the XMR wallet generated?
Using [monero-wallet-rpc](https://www.getmonero.org/resources/developer-guides/wallet-rpc.html). It is a good idea to backup your .keys files. Navigate to the [Settings](SETTINGS.md) page and click "Open Data Folder". They will be located in`/samourai-swaps/monero/monero-data/MAINNET/`.

## How can I remove the pairing and enter a different pairing payload?
At the moment there's no UI functionality for that.
If you wish to completely reset the app, you can back up the following folder then delete it. It is recommended to back it up in case you have any incomplete swaps you might want to return to soon, as swaps cannot simply be restored via recovery phrase/pairing payload.

The folder can be accessed directly from the button "Open Data Folder" on the [Settings](SETTINGS.md#open-data-folder) page.

The folder:<br>
**Linux:** `~/.local/share/samourai-swaps`<br>
**Windows:** `%APPDATA%/samourai-swaps`<br>
**macOS:** `/Library/Application Support/samourai-swaps`

## What can be self-hosted?
Everything can be hosted yourself, except for the Feather price websocket currently (onion address). Eventually, writing our own price websocket so that people can self-host if they wanted is a goal

The Dojo, Electrum, and Monero nodes of course can be self-hosted, but the Rendezvous server can also be hosted yourself for others to use to fetch sellers from (if ASBs register to it), but it is not necessary for a swap to function. It does not act as coordinator for the swap.

## What Java version do I need?
Java 17-21.

## What does "CBOR Error" mean when starting a swap?
Please read the Known Issues/Bugs section in the [README](../README.md).

## How do I troubleshoot "Error initializing Monero wallet. Is it possible an extra monero-wallet-rpc or asb process is running?" message?
Try a different XMR Node. Try restarting. If still having troubles, try the command `ps aux | grep monero-wallet-rpc` to see if any other processes are running, and kill any extras.