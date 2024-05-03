package swap.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class HelperLibraryNative {
    private static boolean argExists(String[] args, String desiredArg) {
        for (String arg : args) {
            if (arg.equals(desiredArg))
                return true;
        }

        return false;
    }

    public static void loadLibrary() {
        try {
            load("libatomicswap");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void load(String name) throws Exception {
        try {
            File so = getResourceAsFile("/" + name + ".so");
            System.load(so.getAbsolutePath());
        } catch (Exception linxException) {
            try {
                File dylib = getResourceAsFile("/" + name + ".dylib");
                System.load(dylib.getAbsolutePath());
            } catch (Exception macOsException) {
                try {
                    File dll = getResourceAsFile("/" + name.replace("lib", "") + ".dll");
                    System.load(dll.getAbsolutePath());
                } catch (Exception windowsException) {
                    throw new Exception("Could not load library for Linux, macOS, or Windows!");
                }
            }
        }
    }

    private static File getResourceAsFile(String resourcePath) {
        try {
            InputStream in = HelperLibraryNative.class.getResourceAsStream(resourcePath);
            if (in == null) {
                return null;
            }

            File tempFile = File.createTempFile(String.valueOf(in.hashCode()), ".tmp");
            tempFile.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                //copy stream
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
