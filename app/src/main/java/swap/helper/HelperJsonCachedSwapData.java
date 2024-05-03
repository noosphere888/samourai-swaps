package swap.helper;

import org.json.JSONArray;
import org.json.JSONObject;
import swap.model.CachedSwapData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;

public class HelperJsonCachedSwapData {
    private final File saveFile;
    private final HashMap<String, CachedSwapData> cachedSwapDatas = new HashMap<>();

    public HelperJsonCachedSwapData(File saveFile) {
        this.saveFile = saveFile;
        readJson();
    }

    private void readJson() {
        try {
            byte[] jsonBytes = Files.readAllBytes(this.saveFile.toPath());
            String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(jsonString);
        } catch (IOException e) {
            saveJson();
        }
    }

    public void saveJson() {
        FileWriter file = null;
        try {
            file = new FileWriter(this.saveFile);
            // no saveFile, create new swap data json
            JSONObject jsonObject = new JSONObject();
            JSONArray dataArray = new JSONArray();
            /*for(CachedSwapData cachedSwapData : cachedSwapDatas.values()) {
                JSONObject cachedSwapDataJson = new JSONObject();
                cachedSwapDataJson.put("swapId", cachedSwapData.swap)
                cachedSwapDataJson.put("btcLockTxid", cachedSwapData.btcLockTxid());
                cachedSwapDataJson.put("xmrLockTxid", cachedSwapData.xmrLockTxid());
                cachedSwapDataJson.put("btcCancelTxid", cachedSwapData.btcCancelTxid());
                cachedSwapDataJson.put("btcRefundTxid", cachedSwapData.btcRefundTxid());
                cachedSwapDataJson.put("btcPunishTxid", cachedSwapData.btcPunishTxid());
                cachedSwapDataJson.put("xmrRedeemTxid", cachedSwapData.xmrRedeemTxid());
                cachedSwapDataJson.put("btcRedeemTxid", cachedSwapData.btcRedeemTxid());
                dataArray.put(cachedSwapDataJson);
            }*/
            jsonObject.put("data", dataArray);
            file.write(jsonObject.toString());
            file.flush();
            file.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
