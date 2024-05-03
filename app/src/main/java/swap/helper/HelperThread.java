package swap.helper;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;

public class HelperThread implements ThreadFactory {
    @Override
    public Thread newThread(@NotNull Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }
}
