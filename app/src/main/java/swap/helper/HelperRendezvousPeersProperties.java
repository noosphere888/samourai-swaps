package swap.helper;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import swap.lib.AppSwap;
import swap.model.Multiaddr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Properties;

public class HelperRendezvousPeersProperties {
    private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static HelperRendezvousPeersProperties instance = null;

    public static HelperRendezvousPeersProperties getInstance() {
        if (instance == null)
            instance = new HelperRendezvousPeersProperties();
        return instance;
    }

    private static final String KEY_CUSTOM_NODES = "peers";
    private HashMap<String, Multiaddr> customRendezvousPeers = new HashMap<>();

    public HelperRendezvousPeersProperties() {
        Properties appProperties = new Properties();
        try {
            if (hasPropertiesFile()) {
                appProperties.load(new FileInputStream(getPropertiesFile()));
                String nodesArrayString = appProperties.getProperty(KEY_CUSTOM_NODES, "[]");
                JSONArray nodesJsonArray = new JSONArray(nodesArrayString);
                for (int i = 0; i < nodesJsonArray.length(); i++) {
                    String node = nodesJsonArray.getString(i);
                    Multiaddr multiaddr = HelperAddress.parseMultiaddr(node);
                    customRendezvousPeers.put(node, multiaddr);
                }
            } else {
                if (!AppSwap.getSwapRootDir().exists()) AppSwap.getSwapRootDir().mkdirs();
            }

            appProperties.store(new FileWriter(getPropertiesFile()), "store rendezvous-peers.properties");
        } catch (IOException e) {
            log.error("Failed to load props", e);
        }
    }

    public File getPropertiesFile() {
        return new File(AppSwap.getSwapRootDir(), "rendezvous-peers.properties");
    }

    public boolean hasPropertiesFile() {
        return getPropertiesFile().exists();
    }

    public void setProperty(String key, String value) throws IOException {
        Properties props = new Properties();
        props.load(new FileInputStream(getPropertiesFile()));
        props.setProperty(key, value);
        props.store(new FileWriter(getPropertiesFile()), "store rendezvous-peers.properties");
    }

    public String getProperty(String key) {
        Properties props = new Properties();
        try {
            props.load(new FileInputStream(getPropertiesFile()));
        } catch (IOException e) {
            return null;
        }
        return props.getProperty(key);
    }

    public HashMap<String, Multiaddr> getCustomRendezvousPeers() {
        return customRendezvousPeers;
    }

    public void addCustomPeer(String multiaddr) {
        customRendezvousPeers.put(multiaddr, HelperAddress.parseMultiaddr(multiaddr));
        save();
    }

    public void removeCustomPeer(String multiaddr) {
        customRendezvousPeers.remove(multiaddr);
        save();
    }

    private void save() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (String nodeAddress : customRendezvousPeers.keySet()) {
                jsonArray.put(nodeAddress);
            }
            setProperty(KEY_CUSTOM_NODES, jsonArray.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
