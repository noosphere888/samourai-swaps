package swap.lib;

import com.samourai.wallet.api.pairing.PairingNetwork;
import com.samourai.wallet.crypto.DecryptionException;
import javafx.application.Platform;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.json.JSONObject;
import swap.bitcoin.DeterministicKeyChainAtomicSwaps;
import swap.client.ClientSwap;
import swap.gui.GUISwap;
import swap.gui.controller.MainController;
import swap.gui.controller.pages.HistoryController;
import swap.helper.*;
import swap.listener.StartupListener;
import swap.model.*;
import swap.model.request.GetHistoryRequest;
import swap.model.request.ListSellersRequest;
import swap.model.request.ResumeRequest;
import swap.model.request.SwapRequest;
import swap.model.response.GetHistoryResponse;
import swap.model.response.ListSellersResponse;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class AppSwap implements App {
    private ConcurrentHashMap<String, ClientSwap> swapClients = new ConcurrentHashMap<>();

    private final List<Multiaddr> rendezvousPeers = Arrays.asList(
            new Multiaddr(Multiaddr.Protocol.DNS4, "discover.unstoppableswap.net", Multiaddr.Protocol.TCP, 8888, Multiaddr.Protocol.P2P, "12D3KooWA6cnqJpVnreBVnoro8midDL9Lpzmg8oJPoAGi7YYaamE"),
            new Multiaddr(Multiaddr.Protocol.DNS4, "eratosthen.es", Multiaddr.Protocol.TCP, 7798, Multiaddr.Protocol.P2P, "12D3KooWAh7EXXa2ZyegzLGdjvj1W4G3EXrTGrf6trraoT1MEobs"),
            new Multiaddr(Multiaddr.Protocol.IP4, "162.19.3.15", Multiaddr.Protocol.TCP, 8888, Multiaddr.Protocol.P2P, "12D3KooWDgGZKJHbHfaUFMoQHj4zEA8xcXW7tdw7rtQDMU7zoXVE")
    );

    private final List<Multiaddr> rendezvousPeersWithTor = Arrays.asList(
            new Multiaddr(Multiaddr.Protocol.DNS4, "discover.unstoppableswap.net", Multiaddr.Protocol.TCP, 8888, Multiaddr.Protocol.P2P, "12D3KooWA6cnqJpVnreBVnoro8midDL9Lpzmg8oJPoAGi7YYaamE"),
            new Multiaddr(Multiaddr.Protocol.DNS4, "eratosthen.es", Multiaddr.Protocol.TCP, 7798, Multiaddr.Protocol.P2P, "12D3KooWAh7EXXa2ZyegzLGdjvj1W4G3EXrTGrf6trraoT1MEobs"),
            new Multiaddr(Multiaddr.Protocol.IP4, "162.19.3.15", Multiaddr.Protocol.TCP, 8888, Multiaddr.Protocol.P2P, "12D3KooWDgGZKJHbHfaUFMoQHj4zEA8xcXW7tdw7rtQDMU7zoXVE"),
            new Multiaddr(Multiaddr.Protocol.ONION3, "spqfqxirmlrhq7gbiwn4jn35c77gu2kof26i6psoc6bbyduol3zty6qd.onion", Multiaddr.Protocol.TCP, 9841, Multiaddr.Protocol.P2P, "12D3KooWM9ipr33nEtxyCBF7fdbHsMrRzHaSf1bEVYzV8XSBSMet"),
            new Multiaddr(Multiaddr.Protocol.ONION3, "yn5wziq6coomwvk2regzvwmjc2hvbw3z2w35bdfersr3kdqat6jwh6ad.onion", Multiaddr.Protocol.TCP, 8888, Multiaddr.Protocol.P2P, "12D3KooWDgGZKJHbHfaUFMoQHj4zEA8xcXW7tdw7rtQDMU7zoXVE")
    );
    public static int UNINITIALIZED_TOR_PORT = -1;
    private static String swapRootDir = null;

    /*** ELECTRUM SERVERS ***/
    private final List<ElectrumServer> mainnetElectrumServers = Arrays.asList(
            new ElectrumServer("kittyserver.ddnsfree", "ssl://kittyserver.ddnsfree.com:50002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.MAINNET),
            new ElectrumServer("electrum.emzy.de", "ssl://electrum.emzy.de:50002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.MAINNET),
            new ElectrumServer("electrum.blockstream.info", "ssl://blockstream.info:50002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.MAINNET),
            new ElectrumServer("electrum.bitaroo.net", "ssl://electrum.bitaroo.net:50002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.MAINNET),
            new ElectrumServer("wallet.blither.io", "ssl://wallet.blither.io:50002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.MAINNET)
    );

    private final List<ElectrumServer> mainnetElectrumServersWithTor = Arrays.asList(
            new ElectrumServer("kittyserver.ddnsfree", "ssl://kittyserver.ddnsfree.com:50002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.MAINNET),
            new ElectrumServer("electrum.emzy.de", "ssl://electrum.emzy.de:50002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.MAINNET),
            new ElectrumServer("electrum.blockstream.info", "ssl://blockstream.info:50002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.MAINNET),
            new ElectrumServer("electrum.bitaroo.net", "ssl://electrum.bitaroo.net:50002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.MAINNET),
            new ElectrumServer("wallet.blither.io", "ssl://wallet.blither.io:50002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.MAINNET),
            new ElectrumServer("kittyserver.onion", "tcp://kittycp2gatrqhlwpmbczk5rblw62enrpo2rzwtkfrrr27hq435d4vid.onion:50001", ElectrumServer.Type.ONION, ElectrumServer.Network.MAINNET),
            new ElectrumServer(null, "tcp://22mgr2fndslabzvx4sj7ialugn2jv3cfqjb3dnj67a6vnrkp7g4l37ad.onion:50001", ElectrumServer.Type.ONION, ElectrumServer.Network.MAINNET),
            new ElectrumServer(null, "tcp://egyh5mutxwcvwhlvjubf6wytwoq5xxvfb2522ocx77puc6ihmffrh6id.onion:50001", ElectrumServer.Type.ONION, ElectrumServer.Network.MAINNET),
            new ElectrumServer(null, "tcp://explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion:110", ElectrumServer.Type.ONION, ElectrumServer.Network.MAINNET)
    );

    private final List<ElectrumServer> testnetElectrumServers = Arrays.asList(
            new ElectrumServer("electrum.blockstream.info", "ssl://electrum.blockstream.info:60002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.TESTNET),
            new ElectrumServer("blockstream.info", "ssl://blockstream.info:993", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.TESTNET),
            new ElectrumServer("testnet.qtornado.com", "ssl://testnet.qtornado.com:51002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.TESTNET),
            new ElectrumServer("blackie.c3-soft.com", "ssl://blackie.c3-soft.com:57006", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.TESTNET)
    );
    private final List<ElectrumServer> testnetElectrumServersWithTor = Arrays.asList(
            new ElectrumServer("electrum.blockstream.info", "ssl://electrum.blockstream.info:60002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.TESTNET),
            new ElectrumServer("blockstream.info", "ssl://blockstream.info:993", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.TESTNET),
            new ElectrumServer("testnet.qtornado.com", "ssl://testnet.qtornado.com:51002", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.TESTNET),
            new ElectrumServer("blackie.c3-soft.com", "ssl://blackie.c3-soft.com:57006", ElectrumServer.Type.WEB_CORS, ElectrumServer.Network.TESTNET),
            new ElectrumServer(null, "tcp://explorerzydxu5ecjrkwceayqybizmpjjznk5izmitf2modhcusuqlid.onion:143", ElectrumServer.Type.ONION, ElectrumServer.Network.TESTNET),
            new ElectrumServer(null, "tcp://3tc6nefii2fwoc66dqvrwcyj64dd3r35ihgxvp4u37itsopns5fjtead.onion:50001", ElectrumServer.Type.ONION, ElectrumServer.Network.TESTNET),
            new ElectrumServer(null, "tcp://gsw6sn27quwf6u3swgra6o7lrp5qau6kt3ymuyoxgkth6wntzm2bjwyd.onion:51001", ElectrumServer.Type.ONION, ElectrumServer.Network.TESTNET)
    );

    /*** MONERO DAEMONS ***/
    private final List<XmrNode> mainnetMoneroDaemons = Arrays.asList(
            new XmrNode("SamouraiWallet", "http://163.172.56.213:18089", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET), // samourai
            new XmrNode("monerujo", "https://nodex.monerujo.io:18081", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET),
            new XmrNode("SupportXMR", "http://node.supportxmr.ir:18081", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET),
            new XmrNode("Hashvault", "https://nodes.hashvault.pro:18081", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET),
            new XmrNode("MoneroWorld", "https://node.moneroworld.com:18089", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET),
            new XmrNode("XMRTW", "https://opennode.xmr-tw.org:18089", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET)
    );
    private final List<XmrNode> mainnetMoneroDaemonsWithTor = Arrays.asList(
            new XmrNode("SamouraiWallet", "http://163.172.56.213:18089", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET), // samourai
            new XmrNode("monerujo", "https://nodex.monerujo.io:18081", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET),
            new XmrNode("SupportXMR", "http://node.supportxmr.ir:18081", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET),
            new XmrNode("Hashvault", "https://nodes.hashvault.pro:18081", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET),
            new XmrNode("MoneroWorld", "https://node.moneroworld.com:18089", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET),
            new XmrNode("XMRTW", "https://opennode.xmr-tw.org:18089", XmrNode.Type.WEB_CORS, XmrNode.Network.MAINNET),
            new XmrNode("SamouraiWalletOnion", "http://446unwib5vc7pfbzflosy6m6vtyuhddnalr3hutyavwe4esfuu5g6ryd.onion:18089", XmrNode.Type.ONION, XmrNode.Network.MAINNET), // samourai
            new XmrNode("node.mysu.onion", "http://tiopyrxseconw73thwlv2pf5hebfcqxj5zdolym7z6pbq6gl4z7xz4ad.onion:18081", XmrNode.Type.ONION, XmrNode.Network.MAINNET),
            new XmrNode("monerujo.onion", "http://monerujods7mbghwe6cobdr6ujih6c22zu5rl7zshmizz2udf7v7fsad.onion:18081", XmrNode.Type.ONION, XmrNode.Network.MAINNET),
            new XmrNode("boldsuck.onion", "http://6dsdenp6vjkvqzy4wzsnzn6wixkdzihx3khiumyzieauxuxslmcaeiad.onion:18081", XmrNode.Type.ONION, XmrNode.Network.MAINNET),
            new XmrNode("plowsof.onion", "http://plowsofe6cleftfmk2raiw5h2x66atrik3nja4bfd3zrfa2hdlgworad.onion:18089", XmrNode.Type.ONION, XmrNode.Network.MAINNET),
            new XmrNode("hashvault.onion", "http://hashvaultsvg2rinvxz7kos77hdfm6zrd5yco3tx2yh2linsmusfwyad.onion:18081", XmrNode.Type.ONION, XmrNode.Network.MAINNET)
    );
    private final List<XmrNode> stagenetMoneroDaemons = Arrays.asList(
            new XmrNode(null, "https://stagenet.xmr.ditatompel.com", XmrNode.Type.WEB_CORS, XmrNode.Network.STAGENET),
            new XmrNode(null, "http://stagenet.community.rino.io:38081", XmrNode.Type.WEB_CORS, XmrNode.Network.STAGENET),
            new XmrNode(null, "http://node2.monerodevs.org:38089", XmrNode.Type.WEB_CORS, XmrNode.Network.STAGENET),
            new XmrNode(null, "http://stagenet.xmr-tw.org:38081", XmrNode.Type.WEB_CORS, XmrNode.Network.STAGENET)
    );
    private final List<XmrNode> stagenetMoneroDaemonsWithTor = Arrays.asList(
            new XmrNode(null, "https://stagenet.xmr.ditatompel.com", XmrNode.Type.WEB_CORS, XmrNode.Network.STAGENET),
            new XmrNode(null, "http://stagenet.community.rino.io:38081", XmrNode.Type.WEB_CORS, XmrNode.Network.STAGENET),
            new XmrNode(null, "http://node2.monerodevs.org:38089", XmrNode.Type.WEB_CORS, XmrNode.Network.STAGENET),
            new XmrNode(null, "http://stagenet.xmr-tw.org:38081", XmrNode.Type.WEB_CORS, XmrNode.Network.STAGENET),
            new XmrNode(null, "http://ykqlrp7lumcik3ubzz3nfsahkbplfgqshavmgbxb4fauexqzat6idjad.onion:38081", XmrNode.Type.ONION, XmrNode.Network.STAGENET),
            new XmrNode(null, "http://plowsoffjexmxalw73tkjmf422gq6575fc7vicuu4javzn2ynnte6tyd.onion:38089", XmrNode.Type.ONION, XmrNode.Network.STAGENET),
            new XmrNode(null, "http://plowsof3t5hogddwabaeiyrno25efmzfxyro2vligremt7sxpsclfaid.onion:38089", XmrNode.Type.ONION, XmrNode.Network.STAGENET)
    );

    public File rpcRootDir = null;
    private DeterministicKeyChain keyChain = null;
    private String electrumServer = null;
    private String moneroDaemon = null;
    @Nullable
    private String passphrase = null;
    private StartupListener startupListener = null;

    public AppSwap(@org.jetbrains.annotations.Nullable String passphrase, StartupListener startupListener) {
        this.passphrase = passphrase;
        this.startupListener = startupListener;
        setupConfig();
    }

    public static File getSwapRootDir() {
        if (swapRootDir == null) {
            swapRootDir = getDataDir();
        }
        File dir = new File(swapRootDir);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static native String getDataDir();

    private void setupConfig() {
        if (HelperProperties.moneroDaemon == null || HelperProperties.moneroDaemon.isEmpty()) {
            // initial default
            List<XmrNode> nodes = getMoneroDaemons();
            int randomNode = new Random().nextInt(nodes.size());
            XmrNode node = nodes.get(randomNode);
            moneroDaemon = node.url();

            // save to properties file
            try {
                HelperProperties.setProperty(HelperProperties.KEY_MONERO_DAEMON, moneroDaemon);
            } catch (IOException e) {
                System.out.println("Failed update atomic-swaps.properties file.");
                throw new RuntimeException(e);
            }
        } else {
            // get saved config
            moneroDaemon = HelperProperties.moneroDaemon;
        }

        if (HelperProperties.electrumServer == null || HelperProperties.electrumServer.isEmpty()) {
            List<ElectrumServer> nodes = getElectrumServers();
            int randomNode = new Random().nextInt(nodes.size());
            ElectrumServer node = nodes.get(randomNode);
            // initial default
            electrumServer = node.url();

            // save to properties
            try {
                HelperProperties.setProperty(HelperProperties.KEY_ELECTRUM_SERVER, electrumServer);
            } catch (IOException e) {
                System.out.println("Failed update atomic-swaps.properties file.");
                throw new RuntimeException(e);
            }
        } else {
            // get saved config
            electrumServer = HelperProperties.electrumServer;
        }
    }

    public ListSellersResponse listSellers(ListSellersRequest listSellersRequest) {
        return ListSellersResponse.fromJson(listSellers(listSellersRequest.toJson().toString()));
    }

    public GetHistoryResponse getHistory(GetHistoryRequest getHistoryRequest) {
        return GetHistoryResponse.fromJson(getHistory(getHistoryRequest.toJson().toString()));
    }

    public File getRpcRootDir() {
        return rpcRootDir;
    }

    public Optional<String> getProxy() {
        if (HelperProperties.isUseTor() && getProxyPort() != UNINITIALIZED_TOR_PORT && !getProxyAddress().isEmpty()) {
            return Optional.of(getProxyAddress() + ":" + getProxyPort());
        } else {
            return Optional.empty();
        }
    }

    public String getProxyAddress() {
        return HelperProperties.isUseTor() ? "127.0.0.1" : "";
    }

    public String getElectrumServer() {
        return electrumServer;
    }

    public void setElectrumServer(String url) {
        electrumServer = url;

        // save config to properties file
        try {
            HelperProperties.setProperty(HelperProperties.KEY_ELECTRUM_SERVER, electrumServer);
            System.out.println("Updated atomic-swaps.properties file - electrumServer:" + electrumServer);
        } catch (IOException e) {
            System.out.println("Failed update atomic-swaps.properties file and save Electrum Server config.");
            throw new RuntimeException(e);
        }
    }

    public List<ElectrumServer> getElectrumServers() {
        boolean mainnet = getParams() == MainNetParams.get();
        List<ElectrumServer> defaults = HelperProperties.isUseTor() ? (mainnet ? mainnetElectrumServersWithTor : testnetElectrumServersWithTor) : (mainnet ? mainnetElectrumServers : testnetElectrumServers);
        HashMap<String, ElectrumServer> nodes = new HashMap<>();

        defaults.forEach(electrumServer -> nodes.put(electrumServer.url(), electrumServer));

        HelperBtcNodesProperties.getInstance().getCustomNodes().forEach((customNode, blank) -> {
            String id = Sha256Hash.of(customNode.getBytes()).toString().substring(0, 8);
            ElectrumServer.Type type = ElectrumServer.Type.WEB_CORS;
            ElectrumServer.Network network = mainnet ? ElectrumServer.Network.MAINNET : ElectrumServer.Network.TESTNET;
            if (customNode.contains(".onion"))
                type = ElectrumServer.Type.ONION;
            ElectrumServer electrNode = new ElectrumServer(id, customNode, type, network);
            nodes.put(customNode, electrNode);
        });

        return nodes.values().stream().toList();
    }

    public List<String> getElectrumServerUrls() {
        return getElectrumServers().stream().map(ElectrumServer::url).collect(Collectors.toList());
    }

    public String getMoneroDaemon() {
        return moneroDaemon;
    }

    public void setMoneroDaemon(String url) {
        moneroDaemon = url;
        // save config to properties file
        try {
            HelperProperties.setProperty(HelperProperties.KEY_MONERO_DAEMON, moneroDaemon);
            System.out.println("Updated atomic-swaps.properties file - moneroDaemon:" + moneroDaemon);
        } catch (IOException e) {
            System.out.println("Failed update atomic-swaps.properties file and save Monero Daemon config.");
            throw new RuntimeException(e);
        }
    }

    public List<XmrNode> getMoneroDaemons() {
        boolean mainnet = getParams() == MainNetParams.get();
        List<XmrNode> defaults = HelperProperties.isUseTor() ? (mainnet ? mainnetMoneroDaemonsWithTor : stagenetMoneroDaemonsWithTor) : (mainnet ? mainnetMoneroDaemons : stagenetMoneroDaemons);
        HashMap<String, XmrNode> nodes = new HashMap<>();

        defaults.forEach(xmrNode -> nodes.put(xmrNode.url(), xmrNode));

        HelperXmrNodesProperties.getInstance().getCustomNodes().forEach((customNode, blank) -> {
            String id = Sha256Hash.of(customNode.getBytes()).toString().substring(0, 8);
            XmrNode.Type type = XmrNode.Type.WEB_CORS;
            XmrNode.Network network = mainnet ? XmrNode.Network.MAINNET : XmrNode.Network.STAGENET;
            if (customNode.contains(".onion"))
                type = XmrNode.Type.ONION;
            else if (customNode.contains(".i2p"))
                type = XmrNode.Type.I2P;
            XmrNode xmrNode = new XmrNode(id, customNode, type, network);
            nodes.put(customNode, xmrNode);
        });

        return nodes.values().stream().toList();
    }

    public List<String> getMoneroDaemonUrls() {
        return getMoneroDaemons().stream().map(XmrNode::url).collect(Collectors.toList());
    }

    public List<Multiaddr> getRendezvousPeers() {
        HashMap<String, Multiaddr> peers = new HashMap<>();
        List<Multiaddr> defaults = HelperProperties.isUseTor() ? rendezvousPeersWithTor : rendezvousPeers;
        defaults.forEach(defaultMultiaddr -> peers.put(defaultMultiaddr.toString(), defaultMultiaddr));
        HelperRendezvousPeersProperties.getInstance().getCustomRendezvousPeers().forEach((s, customMultiaddr) -> peers.put(customMultiaddr.toString(), customMultiaddr));
        return peers.values().stream().toList();
    }

    public int getProxyPort() {
        Tor tor = Tor.getDefault();
        if (tor != null) {
            try {
                return tor.getProxy("127.0.0.1").getPort();
            } catch (TorCtlException e) {
                return UNINITIALIZED_TOR_PORT;
            }
        } else {
            return UNINITIALIZED_TOR_PORT;
        }
    }

    public DeterministicKeyChain getKeyChain() {
        return keyChain;
    }

    public String getSeedAsBase64() {
        DeterministicKeyChainAtomicSwaps keyChain = (DeterministicKeyChainAtomicSwaps) getKeyChain();
        if (keyChain == null || keyChain.getSeed() == null) return "";
        return Base64.getEncoder().encodeToString(keyChain.getSeed().getSeedBytes());
    }

    public NetworkParameters getParams() {
        boolean testnet = HelperProperties.network.equals(PairingNetwork.TESTNET.name());
        return testnet ? TestNet3Params.get() : MainNetParams.get();
    }

    public boolean getRunningSwap() {
        AtomicBoolean anyRunning = new AtomicBoolean(false);
        swapClients.forEach((s, clientSwap) -> {
            if(clientSwap.running.get()) {
                anyRunning.set(true);
            }
        });
        return anyRunning.get();
    }

    public void setRunningSwap(boolean status) {
        swapClients.forEach((s, clientSwap) -> {
            if(!status)
                clientSwap.stop();
        });
    }

    private void handleMonitoringWalletFilesDeletion() {
        String network = getParams() == MainNetParams.get() ? "mainnet" : "testnet";
        File[] files = new File(getRpcRootDir(), "/monero-data/" + network).listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && file.getName().contains("-monitoring-wallet")) {
                boolean deleted = file.delete();
            }
        }
    }

    @Override
    public void start() {
        if (startupListener == null) throw new RuntimeException("startupListener is null.");
        try {
            File moneroWalletsDir = new File(getRpcRootDir(), "monero-data/" + HelperProperties.network.toLowerCase());
            if (!moneroWalletsDir.exists()) moneroWalletsDir.mkdirs();
            this.keyChain = HelperWallet.createKeyChain(HelperProperties.mnemonicEncrypted, passphrase, HelperProperties.hasPassphrase);
        } catch (UnreadableWalletException | InvalidCipherTextException | DecryptionException | IOException e) {
            throw new RuntimeException(e);
        }

        Platform.runLater(startupListener::onClientStarted);
    }

    public void maybeAutoResume(HistoryController historyController) {
        GUISwap.executorService.submit(() -> {
            while (!historyController.initialized) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            Optional<SwapData> firstIncompleteSwap = historyController.historyObservableList.stream().filter(swapData -> {
                String status = swapData.status();
                return !status.equals("XMR_REDEEMED") && !status.equals("BTC_REDEEMED") && !status.equals("REFUNDED") && !status.equals("PUNISHED") && !status.equals("SAFELY_ABORTED");
            }).findAny();

            MainController.getInstance().updateGui(() -> {
                firstIncompleteSwap.ifPresent(swapData -> {
                    MainController.getInstance().historyPage();
                    historyController.resumeSwap(swapData.swapId());
                });
            });
        });
    }

    @Override
    public void stop() {
        MainController mainController = MainController.getInstance();
        if (mainController == null) return;
        mainController.printSwapLogLn(LogType.HIGHLIGHT, "[SWAP_CLIENT] Running shutdown process...", false);
        for (ClientSwap swapClient : swapClients.values()) {
            swapClient.stop();
        }

        handleMonitoringWalletFilesDeletion();
    }

    @Override
    public void restart() {
        // stub
    }

    public void restartXmrRpcProcesses() {
        for (ClientSwap clientSwap : getSwapClients().values()) {
            clientSwap.restartXmrRpcProcess();
        }
    }

    public ConcurrentHashMap<String, ClientSwap> getSwapClients() {
        return swapClients;
    }

    public void buyXmr(SwapRequest swapRequest) {
        ClientSwap swapClient = new ClientSwap(this, swapRequest);
        swapClients.put(swapRequest.uuid(), swapClient);
        swapClient.start();

        HelperSwapsDb.getInstance().setSwapsAccount(swapRequest.uuid(), swapRequest.swapsAccount());
    }

    private native String listSellers(String json);

    public void resume(ResumeRequest resumeRequest) {
        ClientSwap swapClient = new ClientSwap(this, resumeRequest);
        swapClients.put(resumeRequest.swapId(), swapClient);
        swapClient.start();
    }

    private native String getHistory(String json);
}