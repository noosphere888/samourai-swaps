package swap.helper;

import org.json.JSONObject;
import swap.lib.AppSwap;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

public class HelperRawJsonDb {
    private static HelperRawJsonDb instance = null;
    public static HelperRawJsonDb getInstance() {
        if(instance == null) {
            instance = new HelperRawJsonDb();
        }

        return instance;
    }

    private HashMap<String, JSONObject> activeDbs = new HashMap<>();

    public JSONObject getDb(String name) {
        JSONObject db = activeDbs.get(name);
        File dbFile = new File(AppSwap.getSwapRootDir(), name + ".json");
        if(db == null && dbFile.exists()) {
            try {
                String jsonString = new String(Files.readAllBytes(Paths.get(dbFile.getAbsolutePath())));
                JSONObject dbObject = new JSONObject(jsonString);
                activeDbs.put(name, dbObject);
                return dbObject;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if(db == null) {
            JSONObject dbObject = new JSONObject();
            activeDbs.put(name, dbObject);
            return dbObject;
        }

        return db;
    }

    public void updateDb(String name, JSONObject dbObject) {
        activeDbs.put(name, dbObject);
        File dbFile = new File(AppSwap.getSwapRootDir(), name + ".json");
        try {
            Writer output = new BufferedWriter(new FileWriter(dbFile));
            output.write(dbObject.toString());
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
