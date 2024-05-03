package swap.helper;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import swap.lib.AppSwap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Properties;

public class HelperXmrNodesProperties {
    private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static HelperXmrNodesProperties instance = null;

    public static HelperXmrNodesProperties getInstance() {
        if (instance == null)
            instance = new HelperXmrNodesProperties();
        return instance;
    }

    private static final String KEY_CUSTOM_NODES = "nodes";
    private HashMap<String, String> customNodes = new HashMap<>();

    public HelperXmrNodesProperties() {
        Properties appProperties = new Properties();
        try {
            if (hasPropertiesFile()) {
                appProperties.load(new FileInputStream(getPropertiesFile()));
                String nodesArrayString = appProperties.getProperty(KEY_CUSTOM_NODES, "[]");
                JSONArray nodesJsonArray = new JSONArray(nodesArrayString);
                for (int i = 0; i < nodesJsonArray.length(); i++) {
                    String node = nodesJsonArray.getString(i);
                    customNodes.put(node, "");
                }
            } else {
                if (!AppSwap.getSwapRootDir().exists()) AppSwap.getSwapRootDir().mkdirs();
            }

            appProperties.store(new FileWriter(getPropertiesFile()), "store custom-xmr-nodes.properties");
        } catch (IOException e) {
            log.error("Failed to load props", e);
        }
    }

    public File getPropertiesFile() {
        return new File(AppSwap.getSwapRootDir(), "custom-xmr-nodes.properties");
    }

    public boolean hasPropertiesFile() {
        return getPropertiesFile().exists();
    }

    public void setProperty(String key, String value) throws IOException {
        Properties props = new Properties();
        props.load(new FileInputStream(getPropertiesFile()));
        props.setProperty(key, value);
        props.store(new FileWriter(getPropertiesFile()), "store custom-xmr-nodes.properties");
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

    public HashMap<String, String> getCustomNodes() {
        return customNodes;
    }

    public void addCustomNode(String xmrNodeAddress) {
        customNodes.put(xmrNodeAddress, "");
        save();
    }

    public void removeCustomNode(String xmrNodeAddress) {
        customNodes.remove(xmrNodeAddress);
        save();
    }

    private void save() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (String nodeAddress : customNodes.keySet()) {
                jsonArray.put(nodeAddress);
            }
            setProperty(KEY_CUSTOM_NODES, jsonArray.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
