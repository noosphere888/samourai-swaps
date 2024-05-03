package swap.listener;

// Called by the native library
public interface RpcDownloadListener {
    default void onXmrRpcDownloadProgress(long percent) {
        this.onRpcDownloadProgress(percent);
    }

    void onRpcDownloaded();

    void onRpcDownloadError(String error);

    void onRpcDownloadProgress(long pct);
}
